package com.rhys.chat

import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildRefreshAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_CHAT
import com.campaign.common.constants.RoutePath.PAGE_CHAT_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_CHAT_NOTIFICATIONS
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.example.network.AuthException
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.chat.data.ChatRepository
import com.rhys.chat.databinding.ChatFragmentChatBinding
import com.rhys.chat.databinding.ChatItemConversationBinding
import com.rhys.chat.domain.ChatConversation
import com.rhys.chat.ui.ChatUiSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 消息列表：好友会话 + 固定在列表上方的独立预订通知入口。
 *
 * 登录前不创建 [ChatRepository]，避免使用上一账号或 Mock 固化 owner 的数据源；
 * 账号变化时取消旧观察/刷新并重建 Repository。列表与通知由 Room 观察流驱动，
 * 首次登录和下拉时主动刷新；失败保留缓存并给出内联重试，无缓存时展示完整错误态。
 * 本期只做文字会话列表，不展示未读/已读或实时连接状态。
 */
@Route(PAGE_CHAT)
class ChatFragment : BaseBindFragment<ChatFragmentChatBinding>(ChatFragmentChatBinding::inflate) {

    private val conversations = mutableListOf<ChatConversation>()

    private var repository: ChatRepository? = null
    private var repositoryOwnerId = 0L
    private var observedRepository: ChatRepository? = null
    private var observeJob: Job? = null

    private var loggedIn = false
    /** 401 后保留“登录已失效”提示，直到重新登录成功。 */
    private var authExpired = false
    private var hasLoadedOnce = false
    private var initialLoading = false
    private var loading = false
    private var loadingMore = false
    private var nextCursor: String? = null
    private var lastError: String? = null
    /** 登录态/账号/页面重建时递增，丢弃旧请求的迟到结果。 */
    private var requestGeneration = 0

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: ChatFragmentChatBinding) {
        initConversationList()
    }

    override fun initData(binding: ChatFragmentChatBinding) {
    }

    override fun initListener(binding: ChatFragmentChatBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener { refreshFirstPage() }
        binding.tvInlineRetry.setOnClickListener { refreshFirstPage() }
        binding.llNotificationEntry.setOnClickListener { openNotifications() }
    }

    override fun initObserver(binding: ChatFragmentChatBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        // 页面重建后旧协程不得再触碰新 binding；递增代际使其结果全部失效。
        requestGeneration++
        observeJob?.cancel()
        observeJob = null
        observedRepository = null
        loading = false
        loadingMore = false
        initialLoading = false
    }

    private fun initConversationList() {
        binding.rv.addItemDecoration(
            RvDecoration.Builder().setVertical(2).build()
        )
        binding.rv.liner().buildRefreshAdapter<ChatConversation>(header = true) {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(ChatItemConversationBinding::class.java)
            bind<ChatItemConversationBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                val nicknameFallback = getString(R.string.chat_nickname_fallback)
                ChatUiSupport.bindAvatar(
                    imageView = itemBinding.ivAvatar,
                    fallbackView = itemBinding.tvAvatarFallback,
                    nickname = item.peerNickname,
                    avatarUrl = item.peerAvatar,
                    nicknameFallback = nicknameFallback
                )
                itemBinding.tvNickname.text =
                    ChatUiSupport.displayNickname(item.peerNickname, nicknameFallback)
                itemBinding.tvPreview.text = ChatUiSupport.displayPreview(
                    item.lastMessagePreview,
                    getString(R.string.chat_preview_empty)
                )
                val time = ChatUiSupport.formatListTime(
                    millis = item.updatedAt,
                    yesterdayLabel = getString(R.string.chat_time_yesterday)
                )
                if (time == null) {
                    itemBinding.tvTime.text = null
                    itemBinding.tvTime.gone()
                } else {
                    itemBinding.tvTime.text = time
                    itemBinding.tvTime.visible()
                }
            }
            setOnItemClickListener { _, _, position ->
                val item = conversations.getOrNull(position) ?: return@setOnItemClickListener
                openConversation(item)
            }
        }
        binding.rv.submitList(conversations.toList())
        binding.rv.setOnRefreshListener { refreshFirstPage() }
        binding.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore()
            }
        })
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> {
                loggedIn = false
                showLoadingPage()
            }

            AuthState.LoggedOut -> switchToLoggedOut(authExpired)

            is AuthState.LoggedIn -> {
                val ownerId = state.user.id
                loggedIn = true
                authExpired = false
                if (repository == null || repositoryOwnerId != ownerId) {
                    // Mock 数据源在创建时固化 owner，远程模式也要避免账号串号。
                    requestGeneration++
                    observeJob?.cancel()
                    observeJob = null
                    observedRepository = null
                    conversations.clear()
                    binding.rv.submitList(emptyList<ChatConversation>())
                    resetLoadState()
                    repository = ChatRepository()
                    repositoryOwnerId = ownerId
                    showLoadingPage()
                }
                repository?.let { startObserving(it) }
                if (!hasLoadedOnce && !loading) {
                    refreshFirstPage()
                } else {
                    renderPage()
                }
            }
        }
    }

    private fun startObserving(repository: ChatRepository) {
        if (view == null) return
        if (observeJob?.isActive == true && observedRepository === repository) return
        observeJob?.cancel()
        observedRepository = repository
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeConversations().collect { items ->
                    if (view == null || this@ChatFragment.repository !== repository) return@collect
                    conversations.clear()
                    conversations.addAll(items)
                    binding.rv.submitList(conversations.toList())
                    renderPage()
                    fillViewportIfNeeded()
                }
            }
        }
    }

    private fun refreshFirstPage() {
        if (view == null || !loggedIn) return
        if (loading) return
        val repository = repository ?: return
        // 新的首屏/下拉刷新会取代仍在途的加载更多，避免游标被旧结果覆盖。
        val generation = ++requestGeneration
        loading = true
        loadingMore = false
        val showInitialLoading = !hasLoadedOnce && conversations.isEmpty()
        if (showInitialLoading) {
            initialLoading = true
            showLoadingPage()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = repository.refreshConversations()
                if (!isCurrent(generation)) return@launch
                nextCursor = page.nextCursor
                hasLoadedOnce = true
                lastError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleRefreshError(e, generation)
            } finally {
                if (generation == requestGeneration) {
                    loading = false
                    initialLoading = false
                    if (view != null) {
                        binding.rv.refreshComplete()
                        renderPage()
                        fillViewportIfNeeded()
                    }
                }
            }
        }
    }

    private fun loadMore() {
        if (view == null || !loggedIn || loading || loadingMore) return
        if (!hasLoadedOnce) return
        val repository = repository ?: return
        val cursor = nextCursor ?: return
        loadingMore = true
        val generation = requestGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = repository.refreshConversations(cursor = cursor)
                if (!isCurrent(generation)) return@launch
                // 服务端游标未推进时停止继续加载，避免空页无限循环。
                nextCursor = page.nextCursor?.takeIf { it != cursor }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    onUnauthorized()
                } else {
                    toast(e.message ?: getString(R.string.chat_error_default))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                toast(R.string.chat_error_default)
            } finally {
                if (generation == requestGeneration) loadingMore = false
            }
        }
    }

    private fun handleRefreshError(error: Throwable, generation: Int) {
        if (!isCurrent(generation)) return
        if (error is AuthException && error.isUnauthorized) {
            onUnauthorized()
            return
        }
        lastError = (error as? AuthException)?.message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.chat_error_default)
    }

    private fun onUnauthorized() {
        authExpired = true
        SessionManager.logout()
        switchToLoggedOut(expired = true)
    }

    private fun switchToLoggedOut(expired: Boolean) {
        loggedIn = false
        authExpired = expired
        requestGeneration++
        observeJob?.cancel()
        observeJob = null
        observedRepository = null
        repository = null
        repositoryOwnerId = 0L
        conversations.clear()
        resetLoadState()
        if (view != null) {
            binding.rv.refreshComplete()
            binding.rv.submitList(emptyList<ChatConversation>())
            showLoginRequired(expired)
        }
    }

    private fun resetLoadState() {
        hasLoadedOnce = false
        initialLoading = false
        loading = false
        loadingMore = false
        nextCursor = null
        lastError = null
    }

    private fun renderPage() {
        if (view == null || !loggedIn) return
        updateDemoBanner()
        val error = lastError
        when {
            initialLoading && conversations.isEmpty() -> showLoadingPage()
            error != null && conversations.isEmpty() && !hasLoadedOnce -> showFullError(error)
            else -> {
                showContentPage()
                binding.llInlineError.visibility =
                    if (error != null) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvInlineError.text = error.orEmpty()
                binding.tvEmpty.visibility =
                    if (conversations.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun updateDemoBanner() {
        if (view == null) return
        binding.tvDemoBanner.visibility = if (loggedIn && repository?.isMock == true) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun showContentPage() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.llNotificationEntry.visible()
        binding.rv.visible()
    }

    private fun showLoadingPage() {
        if (view == null) return
        updateDemoBanner()
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.llNotificationEntry.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
    }

    private fun showLoginRequired(expired: Boolean) {
        if (view == null) return
        updateDemoBanner()
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.llNotificationEntry.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.tvLoginDesc.text = getString(
            if (expired) R.string.chat_login_expired_desc else R.string.chat_login_required_desc
        )
        binding.llLoginRequired.visible()
    }

    private fun showFullError(message: String) {
        if (view == null) return
        updateDemoBanner()
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llNotificationEntry.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.tvError.text = message
        binding.llError.visible()
    }

    /**
     * 首屏数据不足以滚动时主动补齐一页，避免大屏上只剩第一页而无法触发加载更多。
     * 仅在已有内容且存在下一页时执行，本身受 [loadingMore] 并发保护。
     */
    private fun fillViewportIfNeeded() {
        if (view == null || conversations.isEmpty() || nextCursor == null) return
        binding.rv.post {
            if (view == null) return@post
            val canScroll = binding.rv.canScrollVertically(1)
            if (!canScroll) loadMore()
        }
    }

    private fun isCurrent(generation: Int): Boolean =
        loggedIn && generation == requestGeneration && view != null

    private fun openConversation(conversation: ChatConversation) {
        if (conversation.id <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_CHAT_DETAIL)
                .args(ChatUiSupport.conversationArgs(conversation))
                .go()
        }.onFailure {
            toast(R.string.chat_open_chat_failed)
        }
    }

    private fun openNotifications() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_CHAT_NOTIFICATIONS)
                .go()
        }.onFailure {
            toast(R.string.chat_open_notifications_failed)
        }
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.chat_login_failed)
        }
    }

    private fun toast(messageRes: Int) {
        if (view == null) return
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        if (view == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
