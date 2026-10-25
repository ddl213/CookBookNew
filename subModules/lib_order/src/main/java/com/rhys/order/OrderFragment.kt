package com.rhys.order

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.load
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.Constants.KEY_BUNDLE_ORDER_ID
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_ORDER
import com.campaign.common.constants.RoutePath.PAGE_ORDER_DETAIL
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderStatus
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.order.data.OrderDataSource
import com.rhys.order.data.OrderRepository
import com.rhys.order.databinding.OrderFragmentOrderBinding
import com.rhys.order.databinding.OrderItemOrderBinding
import com.rhys.order.ui.OrderUiSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 订单 Tab：我的订单 / 我的销售双标签，按整单状态筛选并分页。
 *
 * 同一笔订单由买家、卖家两个角色视角查看；切换标签或筛选都会重置分页并重新加载。
 * 列表数据只来自 [OrderRepository]，不直接依赖 Retrofit/Mock；Mock 模式明确展示演示标识。
 * 登录态未知时等待，退出/切号立即清空旧列表并通过 generation 丢弃迟到响应。
 */
@Route(PAGE_ORDER)
class OrderFragment : BaseBindFragment<OrderFragmentOrderBinding>(OrderFragmentOrderBinding::inflate) {

    private enum class Role(val value: String, val emptyTextRes: Int) {
        BUYER(OrderDataSource.ROLE_BUYER, R.string.order_empty_orders),
        SELLER(OrderDataSource.ROLE_SELLER, R.string.order_empty_sales)
    }

    /** 角色参与列表项内容比较，避免切换买家/卖家时同一订单不重新绑定对方名称。 */
    private data class OrderRow(val order: OrderDto, val role: Role)

    private val orders = mutableListOf<OrderDto>()

    private var repository: OrderRepository? = null
    private var repositoryOwnerId = 0L

    private var loggedIn = false
    private var loading = false
    private var loaded = false
    private var failed = false
    private var page = 1
    private var hasMore = true

    /** 登录态、账号、标签、筛选或 View 重建时递增，丢弃旧请求迟到结果。 */
    private var generation = 0

    private var selectedRole = Role.BUYER
    private var selectedStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_ROLE)?.let { saved ->
            selectedRole = Role.entries.firstOrNull { it.value == saved } ?: Role.BUYER
        }
        if (savedInstanceState?.containsKey(STATE_STATUS) == true) {
            selectedStatus = savedInstanceState.getString(STATE_STATUS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ROLE, selectedRole.value)
        outState.putString(STATE_STATUS, selectedStatus)
        super.onSaveInstanceState(outState)
    }

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: OrderFragmentOrderBinding) {
        binding.titleBar.setTitle(getString(R.string.order_title))
        bindOrderList()
        updateTabUi()
        updateFilterUi()
        repository?.let { showMockBanner(it.isMock) }
    }

    override fun initData(binding: OrderFragmentOrderBinding) {
        showLoadingPage()
    }

    override fun initListener(binding: OrderFragmentOrderBinding) {
        binding.llTabBuyer.setOnClickListener { switchRole(Role.BUYER) }
        binding.llTabSeller.setOnClickListener { switchRole(Role.SELLER) }
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener { loadFirstPage() }
        binding.tvFilterAll.setOnClickListener { selectStatus(null) }
        binding.tvFilterPending.setOnClickListener { selectStatus(OrderStatus.PENDING_ACCEPT) }
        binding.tvFilterAccepted.setOnClickListener { selectStatus(OrderStatus.ACCEPTED) }
        binding.tvFilterWaitingPickup.setOnClickListener { selectStatus(OrderStatus.WAITING_PICKUP) }
        binding.tvFilterCompleted.setOnClickListener { selectStatus(OrderStatus.COMPLETED) }
        binding.tvFilterRejected.setOnClickListener { selectStatus(OrderStatus.REJECTED) }
        binding.tvFilterCancelled.setOnClickListener { selectStatus(OrderStatus.CANCELLED) }
        binding.tvFilterClosed.setOnClickListener { selectStatus(OrderStatus.CLOSED) }
    }

    override fun initObserver(binding: OrderFragmentOrderBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        // 旧 View 的协程不得再触碰新 binding；返回详情页后重新拉取当前标签首页。
        generation++
        loading = false
        loaded = false
        failed = false
        page = 1
        hasMore = true
        orders.clear()
    }

    private fun bindOrderList() {
        binding.rvOrders.liner().buildAdapter<OrderRow> {
            setDiff(
                areItemsSame = { old, new -> old.order.id == new.order.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(OrderItemOrderBinding::class.java)
            bind<OrderItemOrderBinding> { holder, _, row, _ ->
                val itemBinding = holder.binding
                val item = row.order
                val counterpart = OrderUiSupport.counterpart(item, row.role.value)
                val name = counterpart.displayName.trim()
                itemBinding.tvCounterpartName.text =
                    name.ifEmpty { getString(R.string.order_unknown_user) }
                itemBinding.tvCounterpartRole.text =
                    OrderUiSupport.counterpartLabel(row.role.value)
                itemBinding.tvStatus.text = OrderUiSupport.orderStatusLabel(item.status)

                val dishSummary = OrderUiSupport.dishSummary(item.items)
                if (dishSummary == null) {
                    itemBinding.tvDishSummary.text = getString(R.string.order_dish_unknown)
                } else {
                    itemBinding.tvDishSummary.text = dishSummary
                }

                val countSummary = OrderUiSupport.countSummary(item.itemCounts, item.totalQuantity)
                val statusSummary = OrderUiSupport.statusCountSummary(item.itemCounts)
                itemBinding.tvCounts.text = if (statusSummary == null) {
                    countSummary
                } else {
                    "$countSummary · $statusSummary"
                }

                val time = OrderUiSupport.formatTime(item.createdAt)
                if (time == null) {
                    itemBinding.tvCreatedAt.text = null
                    itemBinding.tvCreatedAt.gone()
                } else {
                    itemBinding.tvCreatedAt.text = getString(R.string.order_created_at, time)
                    itemBinding.tvCreatedAt.visible()
                }

                val imageUrl = item.items.firstOrNull()
                    ?.let { NetworkConfig.resolveMediaUrl(it.recipe.imageUrl) }
                if (imageUrl == null) {
                    itemBinding.ivDishImage.setImageDrawable(null)
                } else {
                    itemBinding.ivDishImage.load(imageUrl)
                }
            }
            setOnItemClickListener { _, _, position ->
                val row = orders.getOrNull(position) ?: return@setOnItemClickListener
                openOrderDetail(row.id)
            }
        }
        binding.rvOrders.addItemDecoration(RvDecoration.Builder().setVertical(10).build())
        binding.rvOrders.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                loading = false
                loaded = false
                showMockBanner(false)
                showLoadingPage()
            }

            AuthState.LoggedOut -> switchToLoggedOut()

            is AuthState.LoggedIn -> {
                val ownerId = state.user.id
                if (repository == null || repositoryOwnerId != ownerId) {
                    // 每个账号使用独立 Repository，避免 Mock owner 或迟到响应串号。
                    generation++
                    loading = false
                    loaded = false
                    failed = false
                    page = 1
                    hasMore = true
                    orders.clear()
                    submitRows()
                    repository = OrderRepository()
                    repositoryOwnerId = ownerId
                    showLoadingPage()
                }
                loggedIn = true
                showMockBanner(repository?.isMock == true)
                if (!loaded && !loading) loadFirstPage()
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
        failed = false
        page = 1
        hasMore = true
        orders.clear()
        if (view != null) {
            submitRows()
            showMockBanner(false)
            showLoginRequired()
        }
    }

    private fun switchRole(role: Role) {
        if (view == null || role == selectedRole) return
        selectedRole = role
        updateTabUi()
        reloadForSelectionChange()
    }

    private fun selectStatus(status: String?) {
        if (view == null || status == selectedStatus) return
        selectedStatus = status
        updateFilterUi()
        reloadForSelectionChange()
    }

    /** 切换标签/筛选：作废在途请求、重置分页并重新拉取第一页。 */
    private fun reloadForSelectionChange() {
        generation++
        loading = false
        loaded = false
        failed = false
        page = 1
        hasMore = true
        orders.clear()
        submitRows()
        if (loggedIn) {
            loadFirstPage()
        } else {
            showLoginRequired()
        }
    }

    private fun updateTabUi() {
        if (view == null) return
        val buyer = selectedRole == Role.BUYER
        binding.tvTabBuyer.isSelected = buyer
        binding.tvTabSeller.isSelected = !buyer
        binding.vTabBuyerIndicator.visibility = if (buyer) View.VISIBLE else View.INVISIBLE
        binding.vTabSellerIndicator.visibility = if (buyer) View.INVISIBLE else View.VISIBLE
    }

    private fun updateFilterUi() {
        if (view == null) return
        val chips = listOf(
            binding.tvFilterAll to null,
            binding.tvFilterPending to OrderStatus.PENDING_ACCEPT,
            binding.tvFilterAccepted to OrderStatus.ACCEPTED,
            binding.tvFilterWaitingPickup to OrderStatus.WAITING_PICKUP,
            binding.tvFilterCompleted to OrderStatus.COMPLETED,
            binding.tvFilterRejected to OrderStatus.REJECTED,
            binding.tvFilterCancelled to OrderStatus.CANCELLED,
            binding.tvFilterClosed to OrderStatus.CLOSED
        )
        chips.forEach { (view, status) ->
            view.isSelected = status == selectedStatus
        }
    }

    private fun loadFirstPage() {
        val repository = repository ?: return
        if (view == null || !loggedIn || loading) return
        val requestGeneration = ++generation
        loading = true
        loaded = false
        failed = false
        page = 1
        hasMore = true
        orders.clear()
        submitRows()
        showLoadingPage()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listOrders(
                    role = selectedRole.value,
                    status = selectedStatus,
                    page = 1,
                    pageSize = PAGE_SIZE
                )
                if (!isCurrent(requestGeneration)) return@launch
                orders.clear()
                orders.addAll(OrderUiSupport.mergeOrders(emptyList(), result.items))
                page = result.page + 1
                hasMore = result.hasMore
                loaded = true
                submitRows()
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    failed = true
                    showError(e.message ?: getString(R.string.order_load_failed))
                }
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                failed = true
                showError(getString(R.string.order_load_failed))
            } finally {
                if (requestGeneration == generation) loading = false
            }
        }
    }

    private fun loadMore() {
        val repository = repository ?: return
        if (view == null || !loggedIn || loading || !loaded || failed || !hasMore) return
        val requestGeneration = generation
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listOrders(
                    role = selectedRole.value,
                    status = selectedStatus,
                    page = page,
                    pageSize = PAGE_SIZE
                )
                if (!isCurrent(requestGeneration)) return@launch
                val merged = OrderUiSupport.mergeOrders(orders.toList(), result.items)
                orders.clear()
                orders.addAll(merged)
                page = result.page + 1
                hasMore = result.hasMore
                submitRows()
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.order_load_failed))
                }
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                toast(R.string.order_load_failed)
            } finally {
                if (requestGeneration == generation) loading = false
            }
        }
    }

    /** 列表项携带当前角色，切换买家/卖家后即使订单数据相同也会重新绑定对方信息。 */
    private fun submitRows() {
        if (view == null) return
        binding.rvOrders.submitList(orders.map { OrderRow(it, selectedRole) })
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        if (orders.isEmpty()) {
            binding.rvOrders.gone()
            binding.tvEmpty.text = getString(selectedRole.emptyTextRes)
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
            binding.rvOrders.visible()
        }
    }

    private fun showLoadingPage() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.rvOrders.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvOrders.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvOrders.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.tvError.text = message
        binding.llError.visible()
    }

    private fun showMockBanner(isMock: Boolean) {
        if (view == null) return
        if (isMock) binding.tvDemoBanner.visible() else binding.tvDemoBanner.gone()
    }

    private fun openOrderDetail(orderId: Long) {
        if (orderId <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_ORDER_DETAIL)
                .args(bundleOf(KEY_BUNDLE_ORDER_ID to orderId))
                .go()
        }.onFailure {
            toast(R.string.order_navigation_failed)
        }
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.order_login_failed)
        }
    }

    private fun isCurrent(requestGeneration: Int): Boolean =
        view != null && requestGeneration == generation

    private fun toast(messageRes: Int) {
        if (view == null) return
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        if (view == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val PAGE_SIZE = OrderDataSource.DEFAULT_PAGE_SIZE
        const val STATE_ROLE = "order_state_role"
        const val STATE_STATUS = "order_state_status"
    }
}
