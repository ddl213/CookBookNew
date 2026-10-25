package com.rhys.chat

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.common.R as CommonR
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildRefreshAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_CHAT_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.example.network.AuthException
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.chat.data.ChatRealtimeState
import com.rhys.chat.data.ChatRepository
import com.rhys.chat.databinding.ChatFragmentChatDetailBinding
import com.rhys.chat.databinding.ChatItemMessageOwnBinding
import com.rhys.chat.databinding.ChatItemMessagePeerBinding
import com.rhys.chat.domain.ChatMessage
import com.rhys.chat.domain.ChatMessageStatus
import com.rhys.chat.ui.ChatUiSupport
import com.rhys.chat.ui.ConversationEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 好友一对一文字聊天详情。
 *
 * 只做文字消息：发送、历史分页、失败重试与权限反馈。未读/已读、送达状态、
 * 真实 WebSocket 均不在本期范围；实时能力通过 [ChatRepository.realtime] 预留，
 * 当前实现必须明确显示“实时消息暂不可用”，不能伪造已连接。
 *
 * 登录前不创建 Repository；账号变化时取消旧 Flow/任务并重建，避免 Mock owner
 * 或旧账号数据串号。好友关系、会话参与方与消息归属由服务端判定，客户端只做
 * 防御性校验与错误展示。
 */
@Route(PAGE_CHAT_DETAIL)
class ChatDetailFragment :
    BaseBindFragment<ChatFragmentChatDetailBinding>(ChatFragmentChatDetailBinding::inflate) {

    private val routeArgs by lazy(LazyThreadSafetyMode.NONE) {
        ChatUiSupport.readConversationArgs(arguments)
    }

    private var repository: ChatRepository? = null
    private var repositoryOwnerId = 0L
    private var currentUserId = 0L
    private var conversationId = 0L
    private var peerNickname: String? = null

    private var messageObserveJob: Job? = null
    private var realtimeObserveJob: Job? = null
    private var observedRepository: ChatRepository? = null
    private var observedConversationId = 0L
    private var observedRealtimeRepository: ChatRepository? = null

    private var loggedIn = false
    private var authExpired = false
    private var openingConversation = false
    private var initialLoading = false
    private var loading = false
    private var loadingOlder = false
    private var hasLoadedOnce = false
    private var nextCursor: String? = null

    /** 可重试的内联错误（首屏失败或有缓存时的刷新/分页失败）。 */
    private var lastError: String? = null
    private var retryLoadsOlder = false

    /** 权限/会话不存在等不可通过重试自动恢复的提示；置位后禁用输入。 */
    private var blockedMessage: String? = null

    /** 首屏/打开会话的致命错误；参数非法或权限拒绝时不可重试。 */
    private var fatalError: String? = null
    private var fatalRetryable = false

    private var realtimeState = ChatRealtimeState.UNAVAILABLE
    private var renderedMessages: List<ChatMessage> = emptyList()
    private var firstCommit = true
    private var pendingScrollToBottom = false
    private var pendingAnchor: MessageAnchor? = null

    private var sending = false
    private val retryingIds = mutableSetOf<String>()
    private var requestGeneration = 0

    /** 设置 IME Insets 监听器的根 View；用于 View 重建时安全移除监听。 */
    private var imeInsetsRoot: View? = null
    /** 上一次 IME 底部高度；只比较变化，避免重复应用同一 inset。 */
    private var lastImeBottom = 0

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: ChatFragmentChatDetailBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        peerNickname = routeArgs.peerNickname
        updateTitle()
        initMessageList()
        initImeInsets()
    }

    override fun initData(binding: ChatFragmentChatDetailBinding) {
        if (!routeArgs.isValid) {
            fatalError = getString(R.string.chat_detail_invalid_args)
            fatalRetryable = false
            showFullError(fatalError!!, retryable = false)
        }
    }

    override fun initListener(binding: ChatFragmentChatDetailBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener { retryPage() }
        binding.tvInlineRetry.setOnClickListener { retryInline() }
        binding.btnLoadEarlier.setOnClickListener { loadOlder() }
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.rv.setOnRefreshListener { onPullRefresh() }
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                updateInputState()
            }
        })
    }

    override fun initObserver(binding: ChatFragmentChatDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        // 页面重建后旧协程不得再触碰新 binding；递增代际使其结果全部失效。
        requestGeneration++
        messageObserveJob?.cancel()
        messageObserveJob = null
        realtimeObserveJob?.cancel()
        realtimeObserveJob = null
        observedRepository = null
        observedConversationId = 0L
        observedRealtimeRepository = null
        loading = false
        loadingOlder = false
        initialLoading = false
        openingConversation = false
        sending = false
        retryingIds.clear()
        pendingAnchor = null
        pendingScrollToBottom = false
        // View 重建后旧根布局已销毁，必须移除监听，避免回调继续持有 Fragment。
        imeInsetsRoot?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
        imeInsetsRoot = null
        lastImeBottom = 0
    }

    /**
     * 窗口未真正 resize 时，ImmersionBar 下 IME 仍可能覆盖底部输入栏。
     * 这里只消费 IME 底部高度抬高输入栏，不叠加系统栏 inset，避免手势导航双重留白；
     * 若窗口已 resize 且 IME inset 为 0，则保持 0，不重复上移。
     */
    private fun initImeInsets() {
        if (view == null) return
        val insetsRoot = binding.root
        val inputBar = binding.inputBar
        val messageList = binding.rv
        imeInsetsRoot = insetsRoot
        ViewCompat.setOnApplyWindowInsetsListener(insetsRoot) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeBottom != lastImeBottom) {
                val keyboardOpening = imeBottom > lastImeBottom
                // 键盘弹出前判断用户是否停留在最新消息处，避免把正在上滑阅读的用户拉回底部。
                val wasAtBottom = !messageList.canScrollVertically(1)
                lastImeBottom = imeBottom
                inputBar.updatePadding(bottom = imeBottom)
                if (keyboardOpening && imeBottom > 0 && wasAtBottom) {
                    messageList.post {
                        if (!messageList.isAttachedToWindow) return@post
                        val itemCount = messageList.adapter?.itemCount ?: 0
                        val layoutManager = messageList.layoutManager as? LinearLayoutManager
                        if (itemCount > 0) layoutManager?.scrollToPosition(itemCount - 1)
                    }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(insetsRoot)
    }

    private fun initMessageList() {
        binding.rv.liner()
        binding.rv.buildRefreshAdapter<ChatMessage>(header = true) {
            setDiff(
                areItemsSame = { old, new -> old.clientMessageId == new.clientMessageId },
                areContentsSame = { old, new -> old == new }
            )
            setViewTypeDelegate { _, item ->
                if (ChatUiSupport.isOwnMessage(item.senderId, currentUserId)) {
                    VIEW_TYPE_OWN
                } else {
                    VIEW_TYPE_PEER
                }
            }
            setLayout(VIEW_TYPE_OWN, ChatItemMessageOwnBinding::class.java)
            setLayout(VIEW_TYPE_PEER, ChatItemMessagePeerBinding::class.java)
            bindMulti { holder, _, item, _ ->
                if (holder.localViewType == VIEW_TYPE_OWN) {
                    bindOwnMessage(holder.binding as ChatItemMessageOwnBinding, item)
                } else {
                    bindPeerMessage(holder.binding as ChatItemMessagePeerBinding, item)
                }
                // 位置可能受刷新头影响；点击时按稳定键查找消息，不依赖 adapter position。
                holder.itemView.tag = item.clientMessageId
            }
            setOnItemClickListener { _, view, _ ->
                val clientMessageId = view.tag as? String ?: return@setOnItemClickListener
                retryMessage(clientMessageId)
            }
        }
        binding.rv.submitList(emptyList<ChatMessage>())
    }

    private fun bindPeerMessage(binding: ChatItemMessagePeerBinding, item: ChatMessage) {
        binding.tvContent.text = item.content
        bindTime(binding.tvTime, item)
    }

    private fun bindOwnMessage(binding: ChatItemMessageOwnBinding, item: ChatMessage) {
        binding.tvContent.text = item.content
        bindTime(binding.tvTime, item)
        val label = ChatUiSupport.messageStatusLabel(
            status = item.status,
            sendingLabel = getString(R.string.chat_message_sending),
            failedLabel = getString(R.string.chat_message_failed_retry)
        )
        if (label == null) {
            binding.tvStatus.text = null
            binding.tvStatus.gone()
        } else {
            binding.tvStatus.text = label
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.status == ChatMessageStatus.FAILED) {
                        CommonR.color.red
                    } else {
                        CommonR.color.color_999999
                    }
                )
            )
            binding.tvStatus.visible()
        }
    }

    private fun bindTime(view: android.widget.TextView, item: ChatMessage) {
        val time = ChatUiSupport.formatMessageTime(item.createdAt)
        if (time == null) {
            view.text = null
            view.gone()
        } else {
            view.text = time
            view.visible()
        }
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        if (!routeArgs.isValid) {
            // 参数非法时任何登录态都不发起请求；未登录也直接展示参数错误。
            loggedIn = state is AuthState.LoggedIn
            currentUserId = (state as? AuthState.LoggedIn)?.user?.id ?: 0L
            fatalError = getString(R.string.chat_detail_invalid_args)
            fatalRetryable = false
            showFullError(fatalError!!, retryable = false)
            return
        }
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
                currentUserId = ownerId
                updateTitle()
                if (repository == null || repositoryOwnerId != ownerId) {
                    resetForOwner(ownerId)
                } else {
                    resumeForCurrentView()
                }
            }
        }
    }

    /** 首次登录或账号变化：取消旧 Flow/任务，重建 Repository 后再加载。 */
    private fun resetForOwner(ownerId: Long) {
        requestGeneration++
        cancelObservation()
        renderedMessages = emptyList()
        binding.rv.submitList(emptyList<ChatMessage>())
        resetLoadState()
        openingConversation = false
        sending = false
        retryingIds.clear()
        conversationId = if (routeArgs.hasConversationId) routeArgs.conversationId else 0L
        peerNickname = routeArgs.peerNickname
        updateTitle()
        repository = ChatRepository()
        repositoryOwnerId = ownerId
        realtimeState = ChatRealtimeState.UNAVAILABLE
        showLoadingPage()
        repository?.let { startObservingRealtime(it) }
        when (ChatUiSupport.conversationEntry(routeArgs)) {
            ConversationEntry.CONVERSATION -> beginConversation(routeArgs.conversationId)
            ConversationEntry.PEER -> openPeerConversation()
            ConversationEntry.INVALID -> {
                fatalError = getString(R.string.chat_detail_invalid_args)
                fatalRetryable = false
                showFullError(fatalError!!, retryable = false)
            }
        }
    }

    /** 同一账号下 View 重建或 STARTED 恢复：重新挂载观察流，必要时补拉首屏。 */
    private fun resumeForCurrentView() {
        val repository = repository ?: return
        startObservingRealtime(repository)
        when {
            conversationId > 0L -> {
                startObservingMessages(repository, conversationId)
                if (!hasLoadedOnce && !loading) refreshFirstPage() else renderPage()
            }

            fatalError != null -> renderPage()
            routeArgs.hasPeerUserId && !openingConversation -> openPeerConversation()
            !routeArgs.isValid -> {
                fatalError = getString(R.string.chat_detail_invalid_args)
                fatalRetryable = false
                showFullError(fatalError!!, retryable = false)
            }

            else -> renderPage()
        }
    }

    private fun cancelObservation() {
        messageObserveJob?.cancel()
        messageObserveJob = null
        realtimeObserveJob?.cancel()
        realtimeObserveJob = null
        observedRepository = null
        observedConversationId = 0L
        observedRealtimeRepository = null
    }

    private fun openPeerConversation() {
        if (view == null || !loggedIn || openingConversation) return
        val repository = repository ?: return
        if (routeArgs.peerUserId <= 0L) return
        openingConversation = true
        initialLoading = true
        showLoadingPage()
        val generation = ++requestGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val conversation = repository.openConversation(routeArgs.peerUserId)
                if (!isCurrent(generation)) return@launch
                if (conversation.id <= 0L) {
                    fatalError = getString(R.string.chat_error_chat_not_found)
                    fatalRetryable = true
                    return@launch
                }
                if (peerNickname.isNullOrBlank()) {
                    peerNickname = conversation.peerNickname
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && !ChatUiSupport.isUnavailable(it) }
                }
                updateTitle()
                beginConversation(conversation.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent(generation)) handleOpenConversationError(e)
            } finally {
                // 打开成功后 beginConversation 会推进代际号，仍须复位进行中标记。
                openingConversation = false
                if (generation == requestGeneration) {
                    initialLoading = false
                    if (view != null) renderPage()
                }
            }
        }
    }

    private fun beginConversation(id: Long) {
        if (id <= 0L) {
            fatalError = getString(R.string.chat_detail_invalid_args)
            fatalRetryable = false
            showFullError(fatalError!!, retryable = false)
            return
        }
        conversationId = id
        updateTitle()
        val repository = repository ?: return
        startObservingMessages(repository, id)
        refreshFirstPage()
    }

    private fun startObservingMessages(repository: ChatRepository, conversationId: Long) {
        if (view == null) return
        if (messageObserveJob?.isActive == true &&
            observedRepository === repository &&
            observedConversationId == conversationId
        ) {
            return
        }
        messageObserveJob?.cancel()
        observedRepository = repository
        observedConversationId = conversationId
        messageObserveJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeMessages(conversationId).collect { items ->
                    if (view == null || !loggedIn) return@collect
                    if (this@ChatDetailFragment.repository !== repository) return@collect
                    if (this@ChatDetailFragment.conversationId != conversationId) return@collect
                    renderMessages(items)
                }
            }
        }
    }

    private fun startObservingRealtime(repository: ChatRepository) {
        if (view == null) return
        if (realtimeObserveJob?.isActive == true &&
            observedRealtimeRepository === repository
        ) {
            return
        }
        realtimeObserveJob?.cancel()
        observedRealtimeRepository = repository
        realtimeObserveJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.realtime.connectionState.collect { state ->
                    if (view == null || this@ChatDetailFragment.repository !== repository) {
                        return@collect
                    }
                    realtimeState = state
                    updateRealtimeBanner()
                }
            }
        }
    }

    private fun refreshFirstPage() {
        if (view == null || !loggedIn) return
        val repository = repository ?: return
        val targetConversationId = conversationId
        if (targetConversationId <= 0L) return
        if (loading || loadingOlder) {
            binding.rv.refreshComplete()
            return
        }
        val generation = ++requestGeneration
        loading = true
        val firstLoad = !hasLoadedOnce && renderedMessages.isEmpty()
        val wasAtBottom = isAtBottom()
        pendingAnchor = null
        pendingScrollToBottom = firstLoad || wasAtBottom
        if (firstLoad) {
            initialLoading = true
            showLoadingPage()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = repository.refreshMessages(targetConversationId)
                if (!isCurrent(generation)) return@launch
                nextCursor = page.nextCursor?.takeIf { it.isNotBlank() }
                hasLoadedOnce = true
                lastError = null
                retryLoadsOlder = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleLoadError(e, generation)
            } finally {
                if (generation == requestGeneration) {
                    loading = false
                    initialLoading = false
                    if (view != null) {
                        binding.rv.refreshComplete()
                        renderPage()
                    }
                }
            }
        }
    }

    private fun loadOlder() {
        if (view == null || !loggedIn) return
        val repository = repository ?: return
        val targetConversationId = conversationId
        val cursor = nextCursor ?: return
        if (targetConversationId <= 0L || loading || loadingOlder) return
        val anchor = captureAnchor()
        pendingAnchor = anchor
        loadingOlder = true
        renderLoadEarlier()
        val generation = requestGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = repository.refreshMessages(targetConversationId, before = cursor)
                if (!isCurrent(generation)) return@launch
                // 服务端游标未推进时停止继续加载，避免空页无限循环。
                nextCursor = page.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
                lastError = null
                retryLoadsOlder = false
                if (page.items.isEmpty() || nextCursor == null) pendingAnchor = null
            } catch (e: CancellationException) {
                pendingAnchor = null
                throw e
            } catch (e: Exception) {
                // 分页失败时不保留锚点，避免后续新消息提交被旧位置恢复抢走。
                pendingAnchor = null
                if (isCurrent(generation)) handleOlderError(e)
            } finally {
                if (generation == requestGeneration) {
                    loadingOlder = false
                    if (view != null) renderPage()
                }
            }
        }
    }

    private fun sendMessage() {
        if (view == null || !inputAllowed() || sending) return
        val repository = repository ?: return
        val targetConversationId = conversationId
        if (targetConversationId <= 0L) return
        val content = binding.etInput.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) return
        sending = true
        // 新消息必须优先滚动到底部，不能被仍在等待的“加载更早”锚点抢走。
        pendingAnchor = null
        pendingScrollToBottom = true
        binding.etInput.setText("")
        updateInputState()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.sendMessage(targetConversationId, content)
                if (view == null) return@launch
                lastError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (view != null) handleMessageActionError(e)
            } finally {
                sending = false
                if (view != null) {
                    updateInputState()
                    renderPage()
                }
            }
        }
    }

    private fun retryMessage(clientMessageId: String) {
        if (view == null || !inputAllowed()) return
        val item = renderedMessages.firstOrNull { it.clientMessageId == clientMessageId } ?: return
        if (!ChatUiSupport.isOwnMessage(item.senderId, currentUserId)) return
        if (item.status != ChatMessageStatus.FAILED) return
        if (!retryingIds.add(clientMessageId)) return
        val repository = repository ?: run {
            retryingIds.remove(clientMessageId)
            return
        }
        if (conversationId <= 0L) {
            retryingIds.remove(clientMessageId)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Repository 复用原 clientMessageId，成功后只更新原记录，不会新增第二条。
                repository.retryMessage(clientMessageId)
                if (view == null) return@launch
                lastError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (view != null) handleMessageActionError(e)
            } finally {
                retryingIds.remove(clientMessageId)
                if (view != null) {
                    updateInputState()
                    renderPage()
                }
            }
        }
    }

    private fun renderMessages(items: List<ChatMessage>) {
        if (view == null) return
        val wasAtBottom = isAtBottom()
        renderedMessages = items
        val anchor = pendingAnchor
        pendingAnchor = null
        // 显式滚动（首次加载、发送新消息）优先于分页锚点；锚点只用于非显式更新。
        val explicitScroll = pendingScrollToBottom || (firstCommit && items.isNotEmpty())
        val scrollBottom = explicitScroll || (items.isNotEmpty() && wasAtBottom)
        pendingScrollToBottom = false
        val data = items.toList()
        binding.rv.submitList(data, Runnable {
            if (view == null) return@Runnable
            when {
                explicitScroll -> scrollToBottom()
                anchor != null -> restoreAnchor(anchor, data)
                scrollBottom -> scrollToBottom()
            }
            firstCommit = false
        })
        renderPage()
    }

    private fun renderPage() {
        if (view == null) return
        updateDemoBanner()
        updateRealtimeBanner()
        if (!loggedIn) return
        val fatal = fatalError
        val blocked = blockedMessage
        when {
            fatal != null -> showFullError(fatal, fatalRetryable)
            blocked != null && renderedMessages.isEmpty() -> showBlockedFull(blocked)
            initialLoading && renderedMessages.isEmpty() -> showLoadingPage()
            lastError != null && renderedMessages.isEmpty() && !hasLoadedOnce ->
                showFullError(lastError!!, retryable = true)

            else -> {
                showContentPage()
                val inline = blocked ?: lastError
                if (inline == null) {
                    binding.llInlineError.gone()
                } else {
                    binding.tvInlineError.text = inline
                    binding.tvInlineRetry.visibility =
                        if (blocked != null) View.GONE else View.VISIBLE
                    binding.llInlineError.visible()
                }
                binding.tvEmpty.visibility =
                    if (renderedMessages.isEmpty() && hasLoadedOnce) View.VISIBLE else View.GONE
                renderLoadEarlier()
            }
        }
    }

    private fun renderLoadEarlier() {
        if (view == null) return
        val show = nextCursor != null && renderedMessages.isNotEmpty()
        if (!show) {
            binding.btnLoadEarlier.gone()
            return
        }
        binding.btnLoadEarlier.visible()
        binding.btnLoadEarlier.isEnabled = !loadingOlder
        binding.btnLoadEarlier.text = getString(
            if (loadingOlder) R.string.chat_load_earlier_loading else R.string.chat_load_earlier
        )
    }

    private fun showContentPage() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.rv.visible()
        if (conversationId > 0L && fatalError == null) {
            binding.inputBar.visible()
        } else {
            binding.inputBar.gone()
        }
        updateInputState()
    }

    private fun showLoadingPage() {
        if (view == null) return
        updateDemoBanner()
        updateRealtimeBanner()
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.btnLoadEarlier.gone()
        binding.inputBar.gone()
    }

    private fun showLoginRequired(expired: Boolean) {
        if (view == null) return
        updateDemoBanner()
        updateRealtimeBanner()
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.btnLoadEarlier.gone()
        binding.inputBar.gone()
        binding.tvLoginDesc.text = getString(
            if (expired) R.string.chat_login_expired_desc else R.string.chat_detail_login_required_desc
        )
        binding.llLoginRequired.visible()
    }

    private fun showBlockedFull(message: String) {
        if (view == null) return
        updateDemoBanner()
        updateRealtimeBanner()
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.btnLoadEarlier.gone()
        binding.inputBar.gone()
        binding.tvError.text = message
        binding.btnRetry.gone()
        binding.llError.visible()
    }

    private fun showFullError(message: String, retryable: Boolean) {
        if (view == null) return
        updateDemoBanner()
        updateRealtimeBanner()
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llInlineError.gone()
        binding.rv.gone()
        binding.tvEmpty.gone()
        binding.btnLoadEarlier.gone()
        binding.inputBar.gone()
        binding.tvError.text = message
        binding.btnRetry.visibility = if (retryable) View.VISIBLE else View.GONE
        binding.llError.visible()
    }

    private fun updateDemoBanner() {
        if (view == null) return
        binding.tvDemoBanner.visibility =
            if (loggedIn && repository?.isMock == true) View.VISIBLE else View.GONE
    }

    private fun updateRealtimeBanner() {
        if (view == null) return
        val text = ChatUiSupport.realtimeBannerText(
            state = realtimeState,
            unavailableText = getString(R.string.chat_realtime_unavailable),
            disconnectedText = getString(R.string.chat_realtime_disconnected),
            connectingText = getString(R.string.chat_realtime_connecting)
        )
        if (!loggedIn || text == null) {
            binding.tvRealtimeBanner.text = null
            binding.tvRealtimeBanner.gone()
        } else {
            binding.tvRealtimeBanner.text = text
            binding.tvRealtimeBanner.visible()
        }
    }

    private fun updateInputState() {
        if (view == null) return
        val allowed = inputAllowed()
        binding.etInput.isEnabled = allowed
        val hasText = binding.etInput.text?.toString()?.trim().orEmpty().isNotEmpty()
        binding.btnSend.isEnabled = allowed && !sending && hasText
    }

    private fun inputAllowed(): Boolean =
        loggedIn &&
            conversationId > 0L &&
            fatalError == null &&
            blockedMessage == null

    private fun retryPage() {
        if (view == null || !loggedIn) return
        fatalError = null
        fatalRetryable = false
        if (conversationId > 0L) {
            refreshFirstPage()
        } else if (routeArgs.hasPeerUserId) {
            openPeerConversation()
        }
    }

    private fun retryInline() {
        if (view == null || blockedMessage != null) return
        if (retryLoadsOlder) {
            loadOlder()
        } else {
            refreshFirstPage()
        }
    }

    private fun onPullRefresh() {
        if (view == null) return
        if (!loggedIn || conversationId <= 0L || fatalError != null || loading || loadingOlder) {
            binding.rv.refreshComplete()
            return
        }
        pendingAnchor = null
        refreshFirstPage()
    }

    private fun handleLoadError(error: Throwable, generation: Int) {
        if (!isCurrent(generation)) return
        if (error is AuthException && error.isUnauthorized) {
            onUnauthorized()
            return
        }
        val permission = permissionMessage(error)
        if (permission != null) {
            blockedMessage = permission
            lastError = null
            retryLoadsOlder = false
            return
        }
        lastError = errorMessage(error, getString(R.string.chat_error_default))
        retryLoadsOlder = false
    }

    private fun handleOlderError(error: Throwable) {
        if (error is AuthException && error.isUnauthorized) {
            onUnauthorized()
            return
        }
        val permission = permissionMessage(error)
        if (permission != null) {
            blockedMessage = permission
            lastError = null
            retryLoadsOlder = false
            return
        }
        lastError = errorMessage(error, getString(R.string.chat_error_default))
        retryLoadsOlder = true
    }

    private fun handleOpenConversationError(error: Throwable) {
        if (error is AuthException && error.isUnauthorized) {
            onUnauthorized()
            return
        }
        val permission = permissionMessage(error)
        if (permission != null) {
            fatalError = permission
            fatalRetryable = false
            return
        }
        fatalError = errorMessage(error, getString(R.string.chat_error_default))
        fatalRetryable = true
    }

    private fun handleMessageActionError(error: Throwable) {
        if (error is AuthException && error.isUnauthorized) {
            onUnauthorized()
            return
        }
        val permission = permissionMessage(error)
        if (permission != null) {
            blockedMessage = permission
            lastError = null
            retryLoadsOlder = false
            return
        }
        lastError = errorMessage(error, getString(R.string.chat_send_failed))
        retryLoadsOlder = false
    }

    /**
     * 403 非好友/无权限、404 会话不存在都是不可靠重试恢复的拒绝；
     * 返回 null 表示可按网络类错误重试。
     */
    private fun permissionMessage(error: Throwable): String? {
        val authException = error as? AuthException ?: return null
        return when (authException.code) {
            "NOT_FRIENDS" -> getString(R.string.chat_error_not_friends)
            "CHAT_FORBIDDEN" -> getString(R.string.chat_error_chat_forbidden)
            "CHAT_NOT_FOUND" -> getString(R.string.chat_error_chat_not_found)
            "FORBIDDEN" -> authException.message?.takeIf { it.isNotBlank() }
                ?: getString(R.string.chat_error_chat_forbidden)
            else -> when (authException.httpStatus) {
                403 -> authException.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.chat_error_chat_forbidden)

                404 -> getString(R.string.chat_error_chat_not_found)
                else -> null
            }
        }
    }

    private fun errorMessage(error: Throwable, fallback: String): String =
        (error as? AuthException)?.message?.takeIf { it.isNotBlank() } ?: fallback

    private fun onUnauthorized() {
        authExpired = true
        SessionManager.logout()
        switchToLoggedOut(expired = true)
    }

    private fun switchToLoggedOut(expired: Boolean) {
        loggedIn = false
        authExpired = expired
        requestGeneration++
        cancelObservation()
        repository = null
        repositoryOwnerId = 0L
        currentUserId = 0L
        conversationId = 0L
        openingConversation = false
        sending = false
        retryingIds.clear()
        renderedMessages = emptyList()
        resetLoadState()
        realtimeState = ChatRealtimeState.UNAVAILABLE
        if (view != null) {
            binding.rv.refreshComplete()
            binding.rv.submitList(emptyList<ChatMessage>())
            showLoginRequired(expired)
        }
    }

    private fun resetLoadState() {
        hasLoadedOnce = false
        initialLoading = false
        loading = false
        loadingOlder = false
        nextCursor = null
        lastError = null
        retryLoadsOlder = false
        blockedMessage = null
        fatalError = null
        fatalRetryable = false
        firstCommit = true
        pendingAnchor = null
        pendingScrollToBottom = false
    }

    private fun updateTitle() {
        if (view == null) return
        binding.titleBar.setTitle(
            ChatUiSupport.displayNickname(
                peerNickname,
                getString(R.string.chat_detail_title_default)
            )
        )
    }

    private fun isCurrent(generation: Int): Boolean =
        loggedIn && generation == requestGeneration && view != null

    private fun isAtBottom(): Boolean {
        if (view == null) return true
        return !binding.rv.canScrollVertically(1)
    }

    private fun captureAnchor(): MessageAnchor? {
        if (view == null) return null
        val layoutManager = binding.rv.layoutManager as? LinearLayoutManager ?: return null
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return null
        for (position in first..last) {
            val child = layoutManager.findViewByPosition(position) ?: continue
            val key = child.tag as? String ?: continue
            val topOffset = layoutManager.getDecoratedTop(child) - binding.rv.paddingTop
            return MessageAnchor(key, topOffset)
        }
        return null
    }

    private fun restoreAnchor(anchor: MessageAnchor, items: List<ChatMessage>) {
        val index = items.indexOfFirst { it.clientMessageId == anchor.key }
        if (index < 0) return
        val layoutManager = binding.rv.layoutManager as? LinearLayoutManager ?: return
        layoutManager.scrollToPositionWithOffset(index + headerCount(items.size), anchor.topOffset)
    }

    /** 刷新头占用 0 号位置；数据项在 LayoutManager 中的位置需要加上该偏移。 */
    private fun headerCount(dataSize: Int): Int {
        val total = binding.rv.adapter?.itemCount ?: dataSize
        return (total - dataSize).coerceAtLeast(0)
    }

    private fun scrollToBottom() {
        if (view == null) return
        val layoutManager = binding.rv.layoutManager as? LinearLayoutManager ?: return
        val count = binding.rv.adapter?.itemCount ?: 0
        if (count > 0) layoutManager.scrollToPosition(count - 1)
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

    private data class MessageAnchor(val key: String, val topOffset: Int)

    private companion object {
        const val VIEW_TYPE_PEER = 0
        const val VIEW_TYPE_OWN = 1
    }
}
