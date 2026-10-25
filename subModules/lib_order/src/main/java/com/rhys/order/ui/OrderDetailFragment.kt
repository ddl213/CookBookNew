package com.rhys.order.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.common.R as CommonR
import com.android.common.base.BaseBindFragment
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.android.common.view.TitleBar
import com.campaign.common.constants.Constants.KEY_BUNDLE_ORDER_ID
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_ORDER_DETAIL
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderItemDto
import com.example.network.bean.OrderPartyDto
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.order.R
import com.rhys.order.data.OrderRepository
import com.rhys.order.databinding.OrderFragmentOrderDetailBinding
import com.rhys.order.databinding.OrderItemOrderDetailBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 订单详情：展示买卖双方、整单状态、计数、备注与全部明细。
 *
 * 操作按钮完全来自服务端 `allowedActions`；未知状态/动作不展示、不默认放行。
 * 动作提交使用当前明细/订单版本，成功用返回的完整订单刷新整个页面；
 * 任何非 401 失败都提示后锁定动作并重新拉取真实状态，不自动换版本重试；
 * 回查结束前保持动作禁用，避免拿旧版本重复提交。
 */
@Route(PAGE_ORDER_DETAIL)
class OrderDetailFragment :
    BaseBindFragment<OrderFragmentOrderDetailBinding>(OrderFragmentOrderDetailBinding::inflate) {

    private val actionButtons = mutableListOf<AppCompatButton>()

    private var repository: OrderRepository? = null
    private var repositoryOwnerId = 0L

    private var order: OrderDto? = null
    private var loggedIn = false
    private var loading = false
    private var loaded = false
    private var actionSubmitting = false

    /** 登录态、账号、View 重建与重新拉取时递增，丢弃迟到响应。 */
    private var generation = 0

    private val orderId: Long by lazy(LazyThreadSafetyMode.NONE) {
        readOrderIdArgument(arguments)
    }

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: OrderFragmentOrderDetailBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.btnCancelOrder.setOnClickListener { requestCancelAll() }
    }

    override fun initData(binding: OrderFragmentOrderDetailBinding) {
        if (orderId <= 0L) {
            showError(getString(R.string.order_detail_invalid_params), retryable = false)
        } else {
            showLoadingPage()
        }
    }

    override fun initListener(binding: OrderFragmentOrderDetailBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener {
            if (orderId > 0L) loadOrder()
        }
    }

    override fun initObserver(binding: OrderFragmentOrderDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        generation++
        actionButtons.clear()
        loading = false
        loaded = false
        actionSubmitting = false
        order = null
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        if (orderId <= 0L) {
            showError(getString(R.string.order_detail_invalid_params), retryable = false)
            return
        }
        when (state) {
            AuthState.Unknown -> {
                loggedIn = false
                loading = false
                loaded = false
                showMockBanner(false)
                showLoadingPage()
            }

            AuthState.LoggedOut -> switchToLoggedOut()

            is AuthState.LoggedIn -> {
                val ownerId = state.user.id
                if (repository == null || repositoryOwnerId != ownerId) {
                    // 切号必须清空旧订单与动作按钮，迟到响应由 generation 丢弃。
                    generation++
                    loading = false
                    loaded = false
                    actionSubmitting = false
                    order = null
                    actionButtons.clear()
                    repository = OrderRepository()
                    repositoryOwnerId = ownerId
                    showLoadingPage()
                }
                loggedIn = true
                showMockBanner(repository?.isMock == true)
                if (!loaded && !loading) {
                    loadOrder()
                } else if (loaded) {
                    order?.let { renderOrder(it) }
                }
            }
        }
    }

    private fun switchToLoggedOut() {
        loggedIn = false
        repository = null
        repositoryOwnerId = 0L
        generation++
        loading = false
        loaded = false
        actionSubmitting = false
        order = null
        actionButtons.clear()
        if (view != null) {
            showMockBanner(false)
            showLoginRequired()
        }
    }

    private fun loadOrder() {
        val repository = repository ?: return
        if (view == null || !loggedIn || loading) return
        val requestGeneration = ++generation
        loading = true
        loaded = false
        showLoadingPage()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.getOrder(orderId)
                if (!isCurrent(requestGeneration)) return@launch
                order = result
                loaded = true
                renderOrder(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                handleLoadError(e)
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                showError(getString(R.string.order_detail_load_failed), retryable = true)
            } finally {
                if (requestGeneration == generation) loading = false
            }
        }
    }

    private fun handleLoadError(e: AuthException) {
        when {
            e.isUnauthorized -> {
                loggedIn = false
                SessionManager.logout()
                showLoginRequired()
            }

            e.httpStatus == 404 || e.code == "ORDER_NOT_FOUND" -> {
                order = null
                loaded = false
                showError(getString(R.string.order_detail_not_found), retryable = false)
            }

            else -> {
                showError(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.order_detail_load_failed),
                    retryable = true
                )
            }
        }
    }

    private fun renderOrder(current: OrderDto) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.svContent.visible()

        binding.tvStatus.text = OrderUiSupport.orderStatusLabel(current.status)
        binding.tvCount.text = OrderUiSupport.countSummary(current.itemCounts, current.totalQuantity)
        val breakdown = OrderUiSupport.statusCountSummary(current.itemCounts)
        if (breakdown == null) {
            binding.tvStatusBreakdown.text = null
            binding.tvStatusBreakdown.gone()
        } else {
            binding.tvStatusBreakdown.text = breakdown
            binding.tvStatusBreakdown.visible()
        }

        bindParty(
            party = current.buyer,
            nameView = binding.tvBuyerName,
            usernameView = binding.tvBuyerUsername
        )
        bindParty(
            party = current.seller,
            nameView = binding.tvSellerName,
            usernameView = binding.tvSellerUsername
        )

        val note = current.note.orEmpty().trim()
        if (note.isEmpty()) {
            binding.llNote.gone()
            binding.tvNote.text = null
        } else {
            binding.tvNote.text = note
            binding.llNote.visible()
        }

        val createdAt = OrderUiSupport.formatTime(current.createdAt)
        if (createdAt == null) {
            binding.tvCreatedAt.text = null
            binding.tvCreatedAt.gone()
        } else {
            binding.tvCreatedAt.text = getString(R.string.order_detail_created, createdAt)
            binding.tvCreatedAt.visible()
        }
        val updatedAt = OrderUiSupport.formatTime(current.updatedAt)
        if (updatedAt == null) {
            binding.tvUpdatedAt.text = null
            binding.tvUpdatedAt.gone()
        } else {
            binding.tvUpdatedAt.text = getString(R.string.order_detail_updated, updatedAt)
            binding.tvUpdatedAt.visible()
        }

        renderItems(current.items)

        if (OrderUiSupport.canCancelAll(current.allowedActions)) {
            binding.btnCancelOrder.visible()
            binding.btnCancelOrder.isEnabled = !actionSubmitting
        } else {
            binding.btnCancelOrder.gone()
            binding.btnCancelOrder.isEnabled = false
        }
    }

    private fun bindParty(
        party: OrderPartyDto,
        nameView: TextView,
        usernameView: TextView
    ) {
        nameView.text = party.displayName.trim()
            .ifEmpty { getString(R.string.order_unknown_user) }
        if (party.username.isBlank()) {
            usernameView.text = null
            usernameView.gone()
        } else {
            usernameView.text = getString(R.string.order_detail_username, party.username)
            usernameView.visible()
        }
    }

    private fun renderItems(items: List<OrderItemDto>) {
        val inflater = LayoutInflater.from(requireContext())
        binding.llItems.removeAllViews()
        actionButtons.clear()
        items.forEach { item ->
            val row = OrderItemOrderDetailBinding.inflate(inflater, binding.llItems, false)
            row.tvDishName.text = item.recipe.name.trim()
                .ifEmpty { getString(R.string.order_dish_unknown) }
            row.tvQuantity.text = getString(R.string.order_detail_quantity, item.quantity)
            row.tvItemStatus.text = OrderUiSupport.itemStatusLabel(item.status)

            val imageUrl = NetworkConfig.resolveMediaUrl(item.recipe.imageUrl)
            if (imageUrl == null) {
                row.ivDishImage.setImageDrawable(null)
            } else {
                row.ivDishImage.load(imageUrl)
            }

            row.llItemActions.removeAllViews()
            val actions = OrderUiSupport.knownItemActions(item.allowedActions)
            if (actions.isEmpty()) {
                row.llItemActions.gone()
            } else {
                row.llItemActions.visible()
                actions.forEach { action ->
                    row.llItemActions.addView(
                        createActionButton(item, action),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (row.llItemActions.childCount > 0) marginStart = dp(8)
                        }
                    )
                }
            }
            binding.llItems.addView(row.root)
        }
    }

    private fun createActionButton(item: OrderItemDto, action: String): AppCompatButton {
        val primary = OrderUiSupport.isPrimaryAction(action)
        return AppCompatButton(requireContext()).apply {
            text = OrderUiSupport.actionLabel(action)
            textSize = 13f
            isAllCaps = false
            minHeight = dp(36)
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(
                if (primary) R.drawable.order_bg_primary_button
                else R.drawable.order_bg_secondary_button
            )
            setTextColor(
                requireContext().getColor(
                    if (primary) CommonR.color.white else CommonR.color.textTitle
                )
            )
            isEnabled = !actionSubmitting
            setOnClickListener { requestItemAction(item, action) }
        }.also { actionButtons += it }
    }

    private fun requestItemAction(item: OrderItemDto, action: String) {
        if (view == null || actionSubmitting || !loggedIn) return
        when (action) {
            OrderAction.REJECT -> confirmAction(
                titleRes = R.string.order_detail_confirm_reject_title,
                messageRes = R.string.order_detail_confirm_reject_message,
                positiveRes = R.string.order_detail_action_reject
            ) {
                submitItemAction(item, action)
            }

            OrderAction.CANCEL -> confirmAction(
                titleRes = R.string.order_detail_confirm_cancel_title,
                messageRes = R.string.order_detail_confirm_cancel_message,
                positiveRes = R.string.order_detail_action_cancel
            ) {
                submitItemAction(item, action)
            }

            OrderAction.COMPLETE -> confirmAction(
                titleRes = R.string.order_detail_confirm_complete_title,
                messageRes = R.string.order_detail_confirm_complete_message,
                positiveRes = R.string.order_detail_action_complete
            ) {
                submitItemAction(item, action)
            }

            else -> submitItemAction(item, action)
        }
    }

    private fun requestCancelAll() {
        if (view == null || actionSubmitting || !loggedIn) return
        val current = order ?: return
        if (!OrderUiSupport.canCancelAll(current.allowedActions)) return
        confirmAction(
            titleRes = R.string.order_detail_confirm_cancel_all_title,
            messageRes = R.string.order_detail_confirm_cancel_all_message,
            positiveRes = R.string.order_detail_action_cancel_all
        ) {
            submitCancelAll()
        }
    }

    private fun submitItemAction(item: OrderItemDto, action: String) {
        val current = order ?: return
        val repository = repository ?: return
        if (view == null || actionSubmitting) return
        actionSubmitting = true
        setActionsEnabled(false)
        val requestGeneration = generation
        viewLifecycleOwner.lifecycleScope.launch {
            var reloadAfterFailure = false
            try {
                val updated = repository.actOnItem(
                    orderId = current.id,
                    itemId = item.id,
                    action = action,
                    expectedVersion = item.version
                )
                if (!isCurrent(requestGeneration)) return@launch
                order = updated
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    loggedIn = false
                    SessionManager.logout()
                    showLoginRequired()
                    return@launch
                }
                // 任何非 401 失败都不能当成功：先提示可读错误，再锁住动作立即回查。
                toast(e.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.order_detail_action_failed))
                reloadAfterFailure = true
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                toast(R.string.order_detail_action_failed)
                reloadAfterFailure = true
            } finally {
                // 需要回查时保持 actionSubmitting=true，由回查统一解锁并渲染。
                if (requestGeneration == generation && !reloadAfterFailure) {
                    actionSubmitting = false
                    if (view != null && loggedIn) {
                        order?.let { renderOrder(it) }
                    }
                }
            }
            if (reloadAfterFailure && isCurrent(requestGeneration)) {
                reloadOrderSilently(requestGeneration)
            }
        }
    }

    private fun submitCancelAll() {
        val current = order ?: return
        val repository = repository ?: return
        if (view == null || actionSubmitting ||
            !OrderUiSupport.canCancelAll(current.allowedActions)
        ) {
            return
        }
        actionSubmitting = true
        setActionsEnabled(false)
        val requestGeneration = generation
        viewLifecycleOwner.lifecycleScope.launch {
            var reloadAfterFailure = false
            try {
                val updated = repository.cancelOrder(
                    orderId = current.id,
                    expectedVersion = current.version
                )
                if (!isCurrent(requestGeneration)) return@launch
                order = updated
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    loggedIn = false
                    SessionManager.logout()
                    showLoginRequired()
                    return@launch
                }
                // 任何非 401 失败都不能当成功：先提示可读错误，再锁住动作立即回查。
                toast(e.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.order_detail_action_failed))
                reloadAfterFailure = true
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                toast(R.string.order_detail_action_failed)
                reloadAfterFailure = true
            } finally {
                // 需要回查时保持 actionSubmitting=true，由回查统一解锁并渲染。
                if (requestGeneration == generation && !reloadAfterFailure) {
                    actionSubmitting = false
                    if (view != null && loggedIn) {
                        order?.let { renderOrder(it) }
                    }
                }
            }
            if (reloadAfterFailure && isCurrent(requestGeneration)) {
                reloadOrderSilently(requestGeneration)
            }
        }
    }

    /**
     * 动作失败后重新读取真实状态，不自动用新版本重试原动作。
     *
     * 回查期间保持 [actionSubmitting]，避免拿到新版本前重复提交；
     * 成功时先用完整订单渲染再解锁；404/无权、网络/其他失败、401 与取消
     * 也都在 generation（账号/View）仍有效时最终解锁。迟到响应直接丢弃。
     */
    private fun reloadOrderSilently(requestGeneration: Int) {
        val repository = repository
        if (repository == null) {
            finishActionReload(requestGeneration)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updated = repository.getOrder(orderId)
                if (!isCurrent(requestGeneration)) return@launch
                order = updated
                loaded = true
                renderOrder(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    loggedIn = false
                    SessionManager.logout()
                    showLoginRequired()
                } else if (e.httpStatus == 404 || e.code == "ORDER_NOT_FOUND") {
                    order = null
                    loaded = false
                    showError(getString(R.string.order_detail_not_found), retryable = false)
                } else {
                    toast(e.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.order_detail_load_failed))
                }
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                toast(R.string.order_detail_load_failed)
            } finally {
                finishActionReload(requestGeneration)
            }
        }
    }

    /** 回查结束后的唯一解锁点：仅在账号/View 代次未变时更新 UI。 */
    private fun finishActionReload(requestGeneration: Int) {
        if (requestGeneration != generation) return
        actionSubmitting = false
        if (view != null && loggedIn) {
            setActionsEnabled(true)
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        if (view == null) return
        actionButtons.forEach { it.isEnabled = enabled }
        binding.btnCancelOrder.isEnabled = enabled
    }

    private fun confirmAction(
        titleRes: Int,
        messageRes: Int,
        positiveRes: Int,
        onConfirm: () -> Unit
    ) {
        if (view == null) return
        // 确认框显示期间禁用动作，避免连续点击弹出多个确认或并发提交。
        setActionsEnabled(false)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setNegativeButton(R.string.order_detail_dismiss, null)
            .setPositiveButton(positiveRes) { _, _ -> onConfirm() }
            .create()
        dialog.setOnDismissListener {
            if (view != null && !actionSubmitting) setActionsEnabled(true)
        }
        dialog.show()
    }

    private fun showLoadingPage() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.svContent.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.svContent.gone()
        binding.llError.gone()
        binding.btnCancelOrder.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String, retryable: Boolean) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.svContent.gone()
        binding.llLoginRequired.gone()
        binding.btnCancelOrder.gone()
        binding.tvError.text = message
        if (retryable) binding.btnRetry.visible() else binding.btnRetry.gone()
        binding.llError.visible()
    }

    private fun showMockBanner(isMock: Boolean) {
        if (view == null) return
        if (isMock) binding.tvDemoBanner.visible() else binding.tvDemoBanner.gone()
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.order_detail_login_failed)
        }
    }

    private fun isCurrent(requestGeneration: Int): Boolean =
        view != null && requestGeneration == generation

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(messageRes: Int) {
        if (view == null) return
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        if (view == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

}

/**
 * 读取订单 ID 路由参数；优先读取已解析 Bundle，再回退到 deep link query。
 * 缺失、非数字或 <= 0 一律视为非法，详情页不得请求订单。
 */
private fun readOrderIdArgument(arguments: Bundle?): Long {
    if (arguments == null) return 0L
    val direct = runCatching { arguments.getLong(KEY_BUNDLE_ORDER_ID, 0L) }.getOrDefault(0L)
    if (direct > 0L) return direct
    return NRoute.getArgument(arguments, KEY_BUNDLE_ORDER_ID).toLongOrNull() ?: 0L
}
