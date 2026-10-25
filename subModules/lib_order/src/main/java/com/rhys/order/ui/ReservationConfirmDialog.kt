package com.rhys.order.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBottomSheetDialog
import com.android.common.ext.buildAdapter
import com.android.common.ext.dp
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.load
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.rhys.order.R
import com.rhys.order.data.OrderCreatePendingException
import com.rhys.order.data.OrderDemoContext
import com.rhys.order.data.OrderDemoRecipe
import com.rhys.order.data.OrderPendingConflictException
import com.rhys.order.data.OrderRepository
import com.rhys.order.data.pending.PendingOrderCreate
import com.rhys.order.databinding.OrderDialogReservationConfirmBinding
import com.rhys.order.databinding.OrderItemReservationDishBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 好友菜品预订确认弹窗。
 *
 * - 通过 [UserRepository.recipes] 拉取同一好友当前可预订的菜谱，过滤 `canReserve`；
 * - 支持同好友多菜品、数量与删除、整单备注；
 * - 提交统一走 [OrderRepository]，不直接访问 Retrofit 或 Mock 存储；
 * - Mock 模式构造 [OrderDemoContext]，明确展示演示标识；
 * - 超时/未知结果保留原幂等键，提供“重试确认/放弃本次”，失败不伪装成功。
 *
 * 弹窗使用独立协程作用域，`onStop` 取消；账号切换或退出登录时自动关闭。
 */
class ReservationConfirmDialog(
    context: Context,
    private val sellerId: Long,
    private val sellerName: String,
    private val currentRecipe: RecipeDto? = null,
    private val repository: OrderRepository = OrderRepository(),
    private val userRepository: UserRepository = UserRepository()
) : BaseBottomSheetDialog<OrderDialogReservationConfirmBinding>(
    context,
    OrderDialogReservationConfirmBinding::inflate
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val openedUserId =
        (SessionManager.state.value as? AuthState.LoggedIn)?.user?.id ?: 0L
    private val draft = ReservationDraft()

    private val recipes = mutableListOf<RecipeDto>()
    private val knownRecipes = LinkedHashMap<Long, RecipeDto>()
    private var dishRows: List<DishRow> = emptyList()

    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loadingMore = false
    private var loaded = false
    private var submitting = false
    private var viewAlive = false
    private var requestGeneration = 0
    private var baseBottomPadding = 0
    private var defaultListHeight = 0

    private var pending: PendingOrderCreate? = null
    private var currentRecipeUnavailable = false
    private var noteWatcher: TextWatcher? = null

    override fun initView(binding: OrderDialogReservationConfirmBinding) {
        viewAlive = true
        baseBottomPadding = binding.root.paddingBottom
        binding.tvReservationSubtitle.text = context.getString(
            R.string.order_reserve_subtitle,
            safeSellerName()
        )

        // 底部弹窗的最大高度按屏幕比例限制，避免菜品多时占满整屏。
        val maxListHeight = 280.dp().toInt()
        defaultListHeight = (context.resources.displayMetrics.heightPixels * 0.32f)
            .toInt()
            .coerceIn(120.dp().toInt(), maxListHeight)
        binding.rvReservation.layoutParams = binding.rvReservation.layoutParams.apply {
            height = defaultListHeight
        }

        binding.rvReservation.liner().buildAdapter<DishRow> {
            setDiff(
                areItemsSame = { old, new -> old.recipe.id == new.recipe.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(OrderItemReservationDishBinding::class.java)
            addChildClickViewIds(
                R.id.tv_quantity_minus,
                R.id.tv_quantity_plus,
                R.id.tv_dish_delete
            )
            bind<OrderItemReservationDishBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvDishName.text = item.recipe.name
                val metaParts = listOfNotNull(
                    item.recipe.category?.trim()?.takeIf { it.isNotEmpty() },
                    item.recipe.description?.trim()?.takeIf { it.isNotEmpty() }
                )
                val meta = metaParts.joinToString(" · ")
                if (meta.isEmpty()) {
                    itemBinding.tvDishMeta.gone()
                } else {
                    itemBinding.tvDishMeta.text = meta
                    itemBinding.tvDishMeta.visible()
                }

                val imageUrl = NetworkConfig.resolveMediaUrl(item.recipe.imageUrl)
                if (imageUrl == null) {
                    itemBinding.ivDishImage.setImageDrawable(null)
                } else {
                    itemBinding.ivDishImage.load(imageUrl)
                }

                itemBinding.cbDishSelected.isChecked = item.selected
                if (item.selected) {
                    itemBinding.llDishQuantity.visible()
                    itemBinding.tvDishQuantity.text = item.quantity.toString()
                    itemBinding.tvQuantityMinus.isEnabled = item.quantity > 1
                    itemBinding.tvQuantityPlus.isEnabled = item.quantity < Int.MAX_VALUE
                } else {
                    itemBinding.llDishQuantity.gone()
                }
            }
            setOnItemClickListener { _, _, position ->
                if (currentRecipeUnavailable || submitting || pending != null) {
                    return@setOnItemClickListener
                }
                val item = dishRows.getOrNull(position) ?: return@setOnItemClickListener
                toggleDish(item.recipe.id)
            }
            setOnItemChildClickListener { _, view, position ->
                if (currentRecipeUnavailable || submitting || pending != null) {
                    return@setOnItemChildClickListener
                }
                val item = dishRows.getOrNull(position) ?: return@setOnItemChildClickListener
                when (view.id) {
                    R.id.tv_quantity_minus -> draft.decrement(item.recipe.id)
                    R.id.tv_quantity_plus -> {
                        if (!draft.increment(item.recipe.id)) {
                            toast(R.string.order_reserve_quantity_max)
                        }
                    }

                    R.id.tv_dish_delete -> draft.remove(item.recipe.id)
                }
                renderDishes()
            }
        }
        binding.rvReservation.addItemDecoration(RvDecoration.Builder().setVertical(2).build())
        binding.rvReservation.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) {
                    loadMore()
                }
            }
        })

        // 键盘弹出时给底部弹窗增加 IME 高度内边距，保证备注与提交按钮可见。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            if (!viewAlive) return@setOnApplyWindowInsetsListener insets
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseBottomPadding + imeBottom
            )
            if (defaultListHeight > 0) {
                val targetHeight = if (imeBottom > 0) {
                    (defaultListHeight * 0.5f).toInt().coerceAtLeast(120.dp().toInt())
                } else {
                    defaultListHeight
                }
                if (binding.rvReservation.layoutParams.height != targetHeight) {
                    binding.rvReservation.layoutParams =
                        binding.rvReservation.layoutParams.apply { height = targetHeight }
                }
            }
            insets
        }
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun initListener(binding: OrderDialogReservationConfirmBinding) {
        binding.tvReservationCancel.setOnClickListener { dismissDialog() }
        binding.btnReservationRetry.setOnClickListener { loadFirstPage() }
        binding.btnPendingRetry.setOnClickListener { retryPending() }
        binding.btnPendingDiscard.setOnClickListener { discardPending() }
        binding.btnReservationSubmit.setOnClickListener { submit() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                draft.setNote(s)
                updateSummary()
            }
        }
        noteWatcher = watcher
        binding.etNote.addTextChangedListener(watcher)
    }

    override fun initData(binding: OrderDialogReservationConfirmBinding) {
        if (repository.isMock) {
            binding.tvMockBanner.visible()
        }

        val current = currentRecipe
        if (current != null) {
            if (current.id > 0L && current.canReserve) {
                knownRecipes[current.id] = current
                draft.select(current.id, 1)
            } else {
                currentRecipeUnavailable = true
                binding.tvCurrentUnavailable.visible()
            }
        }

        binding.tvNoteCount.text = context.getString(
            R.string.order_reserve_note_count,
            draft.note.length,
            ReservationDraft.MAX_NOTE_LENGTH
        )
        binding.etNote.setText(draft.note)
        updateSummary()
        observeSession()
        loadPendingThenFirstPage()
    }

    override fun onStop() {
        viewAlive = false
        noteWatcher?.let { bindingOrNull()?.etNote?.removeTextChangedListener(it) }
        noteWatcher = null
        scope.cancel()
        super.onStop()
    }

    private fun bindingOrNull(): OrderDialogReservationConfirmBinding? {
        return runCatching { binding }.getOrNull()
    }

    private fun observeSession() {
        scope.launch {
            SessionManager.state.collect { state ->
                when (state) {
                    AuthState.Unknown -> Unit
                    AuthState.LoggedOut -> dismissForAccountChange()
                    is AuthState.LoggedIn -> {
                        if (openedUserId <= 0L || state.user.id != openedUserId) {
                            dismissForAccountChange()
                        }
                    }
                }
            }
        }
    }

    private fun dismissForAccountChange() {
        if (!viewAlive) return
        dismissDialog()
    }

    private fun loadPendingThenFirstPage() {
        scope.launch {
            val existing = withContext(Dispatchers.IO) {
                runCatching { repository.pendingCreate() }.getOrNull()
            }
            if (!viewAlive) return@launch
            if (existing != null) {
                renderPending(existing)
            }
            loadFirstPage()
        }
    }

    private fun loadFirstPage() {
        if (loading || !viewAlive) return
        loaded = false
        page = 1
        hasMore = true
        load(reset = true)
    }

    private fun loadMore() {
        if (loading || loadingMore || !loaded || !hasMore || !viewAlive) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (loading || !viewAlive) return
        loading = true
        if (reset) {
            showLoading()
        } else {
            loadingMore = true
        }
        val generation = if (reset) ++requestGeneration else requestGeneration
        scope.launch {
            try {
                val result = userRepository.recipes(sellerId, page = page)
                if (!isCurrent(generation)) return@launch
                if (reset) recipes.clear()
                result.items.forEach { recipe ->
                    if (recipe.id > 0L && recipe.canReserve &&
                        recipes.none { it.id == recipe.id }
                    ) {
                        recipes += recipe
                        knownRecipes[recipe.id] = recipe
                    }
                }
                if (reset) ensureCurrentRecipe()
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                renderDishes()
                renderContent()
                updatePendingRetryState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    dismissDialog()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.order_reserve_error)
                    )
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (reset) {
                    showError(context.getString(R.string.order_reserve_error))
                } else {
                    toast(R.string.order_reserve_error)
                }
            } finally {
                if (generation == requestGeneration) {
                    loading = false
                    loadingMore = false
                }
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return viewAlive && generation == requestGeneration
    }

    /**
     * 当前菜谱必须出现在列表中并保持选中。
     * 即使它不在分页结果里也补到首位；服务端创建时仍会重新校验资格。
     */
    private fun ensureCurrentRecipe() {
        val current = currentRecipe ?: return
        if (current.id <= 0L || !current.canReserve) return
        knownRecipes[current.id] = current
        if (recipes.none { it.id == current.id }) {
            recipes.add(0, current)
        }
        if (!draft.isSelected(current.id)) {
            draft.select(current.id, 1)
        }
    }

    private fun toggleDish(recipeId: Long) {
        if (draft.isSelected(recipeId)) {
            draft.remove(recipeId)
        } else {
            draft.select(recipeId, 1)
        }
        renderDishes()
    }

    private fun renderDishes() {
        dishRows = recipes.map { recipe ->
            DishRow(
                recipe = recipe,
                selected = draft.isSelected(recipe.id),
                quantity = draft.quantityOf(recipe.id)
            )
        }
        if (!viewAlive) return
        binding.rvReservation.submitList(dishRows)
        updateSummary()
    }

    private fun renderContent() {
        if (!viewAlive) return
        binding.pbReservation.gone()
        binding.llReservationError.gone()
        if (recipes.isEmpty()) {
            binding.rvReservation.gone()
            binding.tvReservationEmpty.visible()
        } else {
            binding.tvReservationEmpty.gone()
            binding.rvReservation.visible()
        }
    }

    private fun showLoading() {
        if (!viewAlive) return
        binding.pbReservation.visible()
        binding.rvReservation.gone()
        binding.tvReservationEmpty.gone()
        binding.llReservationError.gone()
        updateSummary()
    }

    private fun showError(message: String?) {
        if (!viewAlive) return
        binding.pbReservation.gone()
        binding.rvReservation.gone()
        binding.tvReservationEmpty.gone()
        binding.tvReservationError.text = message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.order_reserve_error)
        binding.llReservationError.visible()
        updateSummary()
    }

    private fun submit() {
        if (!loaded || submitting || pending != null || currentRecipeUnavailable) return
        val items = draft.toSubmitItems()
        if (items.isEmpty()) {
            toast(R.string.order_reserve_empty)
            return
        }
        if (repository.isMock && items.any { !knownRecipes.containsKey(it.recipeId) }) {
            toast(R.string.order_reserve_error)
            return
        }
        setSubmitting(true)
        scope.launch {
            try {
                val demoContext = buildDemoContext()
                withContext(Dispatchers.IO) {
                    repository.createOrder(
                        items = items,
                        note = draft.normalizedNote(),
                        demoContext = demoContext
                    )
                }
                if (!viewAlive) return@launch
                dismissAfterSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OrderCreatePendingException) {
                if (!viewAlive) return@launch
                renderPending(e.pending)
                toast(R.string.order_pending_message)
            } catch (e: OrderPendingConflictException) {
                if (!viewAlive) return@launch
                renderPending(e.pending)
                toast(R.string.order_pending_conflict)
            } catch (e: AuthException) {
                if (!viewAlive) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    dismissDialog()
                    toast(R.string.order_reserve_session_expired)
                } else {
                    toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.order_reserve_failed)
                    )
                }
            } catch (e: Exception) {
                if (!viewAlive) return@launch
                toast(R.string.order_reserve_failed)
            } finally {
                if (viewAlive) setSubmitting(false)
            }
        }
    }

    private fun retryPending() {
        val current = pending ?: return
        if (submitting) return
        if (!current.readable) {
            toast(R.string.order_pending_unreadable)
            return
        }
        if (!canBuildDemoContextFor(current)) {
            toast(R.string.order_pending_missing_recipes)
            updatePendingRetryState()
            return
        }
        setSubmitting(true)
        scope.launch {
            try {
                val demoContext = buildDemoContext()
                withContext(Dispatchers.IO) {
                    repository.retryPendingCreate(demoContext)
                }
                if (!viewAlive) return@launch
                dismissAfterSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OrderCreatePendingException) {
                if (!viewAlive) return@launch
                renderPending(e.pending)
                toast(R.string.order_pending_still_unknown)
            } catch (e: AuthException) {
                if (!viewAlive) return@launch
                when {
                    e.isUnauthorized -> {
                        SessionManager.logout()
                        dismissDialog()
                        toast(R.string.order_reserve_session_expired)
                    }

                    e.code == PENDING_NOT_FOUND -> {
                        clearPendingUi()
                        toast(R.string.order_pending_not_found)
                    }

                    e.code == PENDING_UNREADABLE -> {
                        pending = current.copy(readable = false)
                        updatePendingRetryState()
                        toast(R.string.order_pending_unreadable)
                    }

                    else -> toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.order_reserve_failed)
                    )
                }
            } catch (e: Exception) {
                if (!viewAlive) return@launch
                toast(R.string.order_pending_still_unknown)
            } finally {
                if (viewAlive) setSubmitting(false)
            }
        }
    }

    private fun discardPending() {
        if (submitting) return
        if (pending == null) return
        setSubmitting(true)
        scope.launch {
            val discarded = withContext(Dispatchers.IO) {
                runCatching { repository.discardPending() }.isSuccess
            }
            if (!viewAlive) return@launch
            setSubmitting(false)
            if (discarded) {
                clearPendingUi()
                toast(R.string.order_pending_discarded)
            } else {
                toast(R.string.order_reserve_failed)
            }
        }
    }

    private fun dismissAfterSuccess() {
        dismissDialog()
        toast(R.string.order_reserve_success)
    }

    private fun setSubmitting(value: Boolean) {
        submitting = value
        if (!viewAlive) return
        binding.tvReservationCancel.isEnabled = !value
        binding.etNote.isEnabled = !value
        binding.btnPendingDiscard.isEnabled = !value
        updatePendingRetryState()
        updateSummary()
    }

    private fun updatePendingRetryState() {
        if (!viewAlive) return
        val current = pending ?: return
        val canRetry = !submitting &&
            current.readable &&
            canBuildDemoContextFor(current)
        binding.btnPendingRetry.isEnabled = canRetry
        binding.btnPendingDiscard.isEnabled = !submitting
        binding.tvPendingMessage.text = when {
            !current.readable -> context.getString(R.string.order_pending_unreadable)
            !canBuildDemoContextFor(current) ->
                context.getString(R.string.order_pending_missing_recipes)

            else -> context.getString(R.string.order_pending_message)
        }
    }

    private fun renderPending(value: PendingOrderCreate) {
        if (!viewAlive) return
        pending = value
        binding.llPending.visible()
        binding.tvPendingTitle.text = context.getString(R.string.order_pending_title)
        updatePendingRetryState()
        updatePendingVisibility()
        updateSummary()
    }

    private fun clearPendingUi() {
        pending = null
        if (viewAlive) {
            binding.llPending.gone()
            updatePendingVisibility()
            updateSummary()
        }
    }

    /**
     * 待确认期间隐藏备注与提交，但保留菜品列表只读滚动：
     * 列表可继续分页，帮助 Mock 模式找回原 pending 的菜谱快照；
     * 列表项点击/数量修改已由 pending 判断拦截。
     */
    private fun updatePendingVisibility() {
        if (!viewAlive) return
        val hasPending = pending != null
        binding.tvNoteLabel.visibility = if (hasPending) View.GONE else View.VISIBLE
        binding.etNote.visibility = if (hasPending) View.GONE else View.VISIBLE
        binding.llReservationSummary.visibility = if (hasPending) View.GONE else View.VISIBLE
        binding.btnReservationSubmit.visibility = if (hasPending) View.GONE else View.VISIBLE
        // 仅在列表已成功加载时恢复内容；加载中/失败状态保持原样，避免用空状态覆盖错误提示。
        if (!hasPending && loaded) {
            renderContent()
        }
    }

    /**
     * 仅 Mock 需要提供方与菜谱快照；Remote 传 null。
     * 同一好友所有菜品使用同一提供方，`isFriend=true` 由入口资格与后端校验保证。
     */
    private fun buildDemoContext(): OrderDemoContext? {
        if (!repository.isMock) return null
        val displayName = safeSellerName()
        val demoRecipes = LinkedHashMap<Long, OrderDemoRecipe>()
        knownRecipes.forEach { (id, recipe) ->
            demoRecipes[id] = OrderDemoRecipe(
                name = recipe.name,
                imageUrl = recipe.imageUrl.orEmpty(),
                sellerId = sellerId,
                sellerName = displayName
            )
        }
        return OrderDemoContext(
            sellerId = sellerId,
            sellerName = displayName,
            isFriend = true,
            recipes = demoRecipes
        )
    }

    private fun safeSellerName(): String {
        return sellerName.trim().ifBlank {
            context.getString(R.string.order_reserve_seller_fallback, sellerId)
        }
    }

    private fun canBuildDemoContextFor(value: PendingOrderCreate): Boolean {
        if (!repository.isMock) return true
        return value.items.all { knownRecipes.containsKey(it.recipeId) }
    }

    private fun updateSummary() {
        if (!viewAlive) return
        binding.tvReservationTotal.text = context.getString(
            R.string.order_reserve_total_count,
            draft.selectedCount,
            draft.totalQuantity
        )
        binding.tvNoteCount.text = context.getString(
            R.string.order_reserve_note_count,
            draft.note.length,
            ReservationDraft.MAX_NOTE_LENGTH
        )
        binding.btnReservationSubmit.text = when {
            submitting -> context.getString(R.string.order_reserve_submitting)
            draft.selectedCount > 0 -> context.getString(
                R.string.order_reserve_submit_count,
                draft.selectedCount
            )

            else -> context.getString(R.string.order_reserve_submit)
        }
        binding.btnReservationSubmit.isEnabled =
            !submitting &&
                pending == null &&
                !currentRecipeUnavailable &&
                loaded &&
                draft.canSubmit
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private data class DishRow(
        val recipe: RecipeDto,
        val selected: Boolean,
        val quantity: Int
    )

    private companion object {
        const val PENDING_NOT_FOUND = "ORDER_PENDING_NOT_FOUND"
        const val PENDING_UNREADABLE = "ORDER_PENDING_UNREADABLE"
    }
}
