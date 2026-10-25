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
import com.campaign.common.constants.RoutePath.PAGE_CHAT_NOTIFICATIONS
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.example.network.AuthException
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.chat.data.ChatRepository
import com.rhys.chat.databinding.ChatFragmentNotificationsBinding
import com.rhys.chat.databinding.ChatItemNotificationBinding
import com.rhys.chat.domain.ChatNotification
import com.rhys.chat.ui.ChatUiSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 独立的预订通知列表。
 *
 * 预订通知是消息列表外的独立项，不并入好友会话；本期无未读/已读、无分类过滤。
 * 登录、Repository 生命周期、Mock 标识和加载/空/失败/重试规则与消息列表一致。
 * 通知中的 orderId 只用于展示“关联订单”标签，不伪造订单详情跳转（P08 才接入）。
 */
@Route(PAGE_CHAT_NOTIFICATIONS)
class ChatNotificationsFragment :
    BaseBindFragment<ChatFragmentNotificationsBinding>(ChatFragmentNotificationsBinding::inflate) {

    private val notifications = mutableListOf<ChatNotification>()

    private var repository: ChatRepository? = null
    private var repositoryOwnerId = 0L
    private var observedRepository: ChatRepository? = null
    private var observeJob: Job? = null

    private var loggedIn = false
    private var authExpired = false
    private var hasLoadedOnce = false
    private var initialLoading = false
    private var loading = false
    private var loadingMore = false
    private var nextCursor: String? = null
    private var lastError: String? = null
    private var requestGeneration = 0

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: ChatFragmentNotificationsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        initNotificationList()
    }

    override fun initData(binding: ChatFragmentNotificationsBinding) {
    }

    override fun initListener(binding: ChatFragmentNotificationsBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener { refreshFirstPage() }
        binding.tvInlineRetry.setOnClickListener { refreshFirstPage() }
    }

    override fun initObserver(binding: ChatFragmentNotificationsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        requestGeneration++
        observeJob?.cancel()
        observeJob = null
        observedRepository = null
        loading = false
        loadingMore = false
        initialLoading = false
    }

    private fun initNotificationList() {
        binding.rv.addItemDecoration(
            RvDecoration.Builder().setVertical(2).build()
        )
        binding.rv.liner().buildRefreshAdapter<ChatNotification>(header = true) {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(ChatItemNotificationBinding::class.java)
            bind<ChatItemNotificationBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvTitle.text = ChatUiSupport.displayNotificationTitle(
                    item.title,
                    getString(R.string.chat_notification_title_fallback)
                )
                val body = ChatUiSupport.displayNotificationBody(item.body)
                itemBinding.tvBody.text = body
                if (body.isEmpty()) itemBinding.tvBody.gone() else itemBinding.tvBody.visible()

                val time = ChatUiSupport.formatListTime(
                    millis = item.createdAt,
                    yesterdayLabel = getString(R.string.chat_time_yesterday)
                )
                if (time == null) {
                    itemBinding.tvTime.text = null
                    itemBinding.tvTime.gone()
                } else {
                    itemBinding.tvTime.text = time
                    itemBinding.tvTime.visible()
                }
                itemBinding.tvOrderTag.visibility =
                    if (item.orderId != null) android.view.View.VISIBLE else android.view.View.GONE
            }
            setOnItemClickListener { _, _, position ->
                if (notifications.getOrNull(position) == null) return@setOnItemClickListener
                onNotificationClicked()
            }
        }
        binding.rv.submitList(notifications.toList())
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
                    requestGeneration++
                    observeJob?.cancel()
                    observeJob = null
                    observedRepository = null
                    notifications.clear()
                    binding.rv.submitList(emptyList<ChatNotification>())
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
                repository.observeNotifications().collect { items ->
                    if (view == null || this@ChatNotificationsFragment.repository !== repository) return@collect
                    notifications.clear()
                    notifications.addAll(items)
                    binding.rv.submitList(notifications.toList())
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
        val generation = ++requestGeneration
        loading = true
        loadingMore = false
        val showInitialLoading = !hasLoadedOnce && notifications.isEmpty()
        if (showInitialLoading) {
            initialLoading = true
            showLoadingPage()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = repository.refreshNotifications()
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
                val page = repository.refreshNotifications(cursor = cursor)
                if (!isCurrent(generation)) return@launch
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
        notifications.clear()
        resetLoadState()
        if (view != null) {
            binding.rv.refreshComplete()
            binding.rv.submitList(emptyList<ChatNotification>())
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
            initialLoading && notifications.isEmpty() -> showLoadingPage()
            error != null && notifications.isEmpty() && !hasLoadedOnce -> showFullError(error)
            else -> {
                showContentPage()
                binding.llInlineError.visibility =
                    if (error != null) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvInlineError.text = error.orEmpty()
                binding.tvEmpty.visibility =
                    if (notifications.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
        binding.rv.visible()
    }

    private fun showLoadingPage() {
        if (view == null) return
        updateDemoBanner()
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
    }

    private fun showLoginRequired(expired: Boolean) {
        if (view == null) return
        updateDemoBanner()
        binding.pbLoading.gone()
        binding.llError.gone()
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
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.tvError.text = message
        binding.llError.visible()
    }

    private fun fillViewportIfNeeded() {
        if (view == null || notifications.isEmpty() || nextCursor == null) return
        binding.rv.post {
            if (view == null) return@post
            if (!binding.rv.canScrollVertically(1)) loadMore()
        }
    }

    private fun isCurrent(generation: Int): Boolean =
        loggedIn && generation == requestGeneration && view != null

    /**
     * 订单事件属于 P08；这里只给诚实的后续开放提示，不构造无效的订单详情跳转。
     */
    private fun onNotificationClicked() {
        if (view == null) return
        toast(getString(R.string.chat_order_not_available))
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
