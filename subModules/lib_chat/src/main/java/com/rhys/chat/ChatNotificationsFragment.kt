package com.rhys.chat

import android.os.Bundle
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
import com.campaign.common.constants.Constants.KEY_BUNDLE_ORDER_ID
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.RoutePath.PAGE_CHAT_NOTIFICATIONS
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_ORDER_DETAIL
import com.example.network.AuthException
import com.example.network.bean.PageResponse
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.chat.data.OrderNotificationRepository
import com.rhys.chat.databinding.ChatFragmentNotificationsBinding
import com.rhys.chat.databinding.ChatItemNotificationBinding
import com.rhys.chat.domain.ChatNotification
import com.rhys.chat.ui.ChatUiSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 独立通知列表：订单通知与菜谱分享通知。
 *
 * 数据源为 [OrderNotificationRepository]：跟随订单的 Mock/Remote 配置，使用
 * `GET notifications?page&pageSize`（不带 category）页码分页，同时展示
 * `recipe_share`；旧 P07 聊天通知 Room 缓存不再用于本页。
 * 通知仍不并入好友会话，也不展示未读/已读/红点/推送。
 *
 * 点击订单通知进入订单详情，点击菜谱分享通知进入菜谱详情；关联 ID 无效时只提示
 * “关联内容暂不可用”，不伪造跳转。从详情返回、进入页面和下拉刷新时都会
 * 重新拉取第一页，确保接受/拒绝/取消/完成后的通知状态更新。
 */
@Route(PAGE_CHAT_NOTIFICATIONS)
class ChatNotificationsFragment :
    BaseBindFragment<ChatFragmentNotificationsBinding>(ChatFragmentNotificationsBinding::inflate) {

    private val notifications = mutableListOf<ChatNotification>()

    private var repository: OrderNotificationRepository? = null
    private var repositoryOwnerId = 0L

    private var loggedIn = false
    private var authExpired = false
    private var hasLoadedOnce = false
    private var initialLoading = false
    private var loading = false
    private var loadingMore = false
    private var nextPage = 1
    private var total = 0
    private var hasMore = false
    private var lastError: String? = null
    private var requestGeneration = 0

    /** 首次进入由登录流触发加载；只有真正暂停后再回来才额外刷新。 */
    private var hasPausedSinceView = false

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

    override fun onResume() {
        super.onResume()
        if (!hasPausedSinceView) return
        hasPausedSinceView = false
        if (view == null || !loggedIn || !hasLoadedOnce) return
        // 从订单详情返回时刷新，使接受/拒绝/取消/完成后的通知及时更新。
        refreshFirstPage()
    }

    override fun onPause() {
        super.onPause()
        if (view != null) hasPausedSinceView = true
    }

    override fun onViewDestroy() {
        requestGeneration++
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
                    if (item.orderId?.let { it > 0L } == true) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
            }
            setOnItemClickListener { _, _, position ->
                val item = notifications.getOrNull(position) ?: return@setOnItemClickListener
                onNotificationClicked(item)
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
                    notifications.clear()
                    binding.rv.submitList(emptyList<ChatNotification>())
                    resetLoadState()
                    repository = OrderNotificationRepository()
                    repositoryOwnerId = ownerId
                    showLoadingPage()
                }
                if (!hasLoadedOnce && !loading) {
                    refreshFirstPage()
                } else {
                    renderPage()
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
                val page = repository.fetchAll(1, PAGE_SIZE)
                if (!isCurrent(generation)) return@launch
                applyFirstPage(page)
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

    private fun applyFirstPage(page: PageResponse<ChatNotification>) {
        notifications.clear()
        notifications.addAll(page.items)
        nextPage = 2
        total = page.total
        hasMore = page.hasMore
        binding.rv.submitList(notifications.toList())
    }

    private fun loadMore() {
        if (view == null || !loggedIn || loading || loadingMore) return
        if (!hasLoadedOnce || !hasMore) return
        val repository = repository ?: return
        loadingMore = true
        val generation = requestGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var page = nextPage
                var skippedEmptyPages = 0
                while (true) {
                    val response = repository.fetchAll(page, PAGE_SIZE)
                    if (!isCurrent(generation)) return@launch
                    val existingIds = notifications.mapTo(HashSet()) { it.id }
                    val additions = response.items.filter { existingIds.add(it.id) }
                    notifications.addAll(additions)
                    nextPage = page + 1
                    total = response.total
                    hasMore = response.hasMore
                    // 服务端按 category 过滤后可能出现空页但仍有后续页，不能当作末页。
                    if (response.items.isNotEmpty() || !hasMore) break
                    if (++skippedEmptyPages >= MAX_EMPTY_PAGE_SKIPS) break
                    page += 1
                }
                binding.rv.submitList(notifications.toList())
                renderPage()
                fillViewportIfNeeded()
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
        nextPage = 1
        total = 0
        hasMore = false
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
        if (view == null || notifications.isEmpty() || !hasMore) return
        binding.rv.post {
            if (view == null) return@post
            if (!binding.rv.canScrollVertically(1)) loadMore()
        }
    }

    private fun isCurrent(generation: Int): Boolean =
        loggedIn && generation == requestGeneration && view != null

    /**
     * 订单通知打开订单详情，菜谱分享通知打开菜谱详情；两者都没有有效关联 ID 时
     * 诚实提示“关联内容暂不可用”，不伪造参数，也不尝试打开他人内容。
     */
    private fun onNotificationClicked(item: ChatNotification) {
        if (view == null) return
        val orderId = item.orderId
        if (orderId != null && orderId > 0L) {
            openPage(
                route = PAGE_ORDER_DETAIL,
                args = Bundle().apply { putLong(KEY_BUNDLE_ORDER_ID, orderId) },
                failureRes = R.string.chat_order_navigation_failed
            )
            return
        }
        val recipeId = item.recipeId
        if (recipeId != null && recipeId > 0L) {
            openPage(
                route = PAGE_COOK_BOOK_DETAIL,
                args = Bundle().apply { putLong(KEY_BUNDLE_RECIPE_ID, recipeId) },
                failureRes = R.string.chat_recipe_navigation_failed
            )
            return
        }
        toast(R.string.chat_notification_unavailable)
    }

    private fun openPage(route: String, args: Bundle, failureRes: Int) {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(route)
                .args(args)
                .go()
        }.onFailure {
            toast(failureRes)
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

    private companion object {
        const val PAGE_SIZE = OrderNotificationRepository.DEFAULT_PAGE_SIZE

        /** 连续空页最多跳过 3 页，避免服务端异常时无限请求。 */
        const val MAX_EMPTY_PAGE_SKIPS = 3
    }
}
