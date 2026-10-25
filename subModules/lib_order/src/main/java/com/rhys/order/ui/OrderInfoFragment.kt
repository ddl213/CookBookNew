package com.rhys.order.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
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
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.Constants.KEY_USER_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_ORDER_INFO
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserProfileDto
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.order.R
import com.rhys.order.databinding.OrderFragmentOrderInfoBinding
import com.rhys.order.databinding.OrderItemReservableRecipeBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 3.9 菜品预订详情页。
 *
 * 展示某位好友的公开资料与其当前可预订的菜谱；只展示后端返回 `canReserve=true`
 * 的菜品，最终好友关系与预订资格仍由后端/Mock 数据源校验。
 *
 * 评分与预计送达时间没有真实业务来源，统一显示“暂无”，不伪造数值。
 */
@Route(PAGE_ORDER_INFO)
class OrderInfoFragment :
    BaseBindFragment<OrderFragmentOrderInfoBinding>(OrderFragmentOrderInfoBinding::inflate) {

    private val userRepository = UserRepository()
    private val recipes = mutableListOf<RecipeDto>()

    private val userId: Long by lazy(LazyThreadSafetyMode.NONE) {
        readUserIdArgument(arguments)
    }

    private var profile: UserProfileDto? = null
    private var currentUserId = 0L
    private var loggedIn = false
    private var loading = false
    private var loaded = false
    private var page = 1
    private var hasMore = true
    private var generation = 0
    private var reservationDialog: ReservationConfirmDialog? = null

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: OrderFragmentOrderInfoBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }

        binding.rvDishes.liner().buildAdapter<RecipeDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(OrderItemReservableRecipeBinding::class.java)
            addChildClickViewIds(R.id.btn_reserve_dish)
            bind<OrderItemReservableRecipeBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvDishName.text = item.name

                val description = item.description.orEmpty().trim()
                itemBinding.tvDishDescription.text = description
                if (description.isEmpty()) {
                    itemBinding.tvDishDescription.gone()
                } else {
                    itemBinding.tvDishDescription.visible()
                }

                val category = item.category.orEmpty().trim()
                itemBinding.tvDishCategory.text = category
                if (category.isEmpty()) {
                    itemBinding.tvDishCategory.gone()
                } else {
                    itemBinding.tvDishCategory.visible()
                }

                val imageUrl = NetworkConfig.resolveMediaUrl(item.imageUrl)
                if (imageUrl == null) {
                    itemBinding.ivDishImage.setImageDrawable(null)
                } else {
                    itemBinding.ivDishImage.load(imageUrl)
                }
            }
            setOnItemClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemClickListener
                openRecipeDetail(item)
            }
            setOnItemChildClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemChildClickListener
                openReservation(item)
            }
        }
        binding.rvDishes.addItemDecoration(RvDecoration.Builder().setVertical(2).build())
        binding.rvDishes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
    }

    override fun initData(binding: OrderFragmentOrderInfoBinding) {
        binding.tvRating.text = getString(R.string.order_info_rating)
        binding.tvDelivery.text = getString(R.string.order_info_delivery)
        if (userId <= 0L) {
            showError(getString(R.string.order_info_not_found))
        } else {
            showLoading()
        }
    }

    override fun initListener(binding: OrderFragmentOrderInfoBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener { loadFirstPage() }
    }

    override fun initObserver(binding: OrderFragmentOrderInfoBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        reservationDialog?.dismissDialog()
        reservationDialog = null
        generation++
        loading = false
        loaded = false
        profile = null
        recipes.clear()
        page = 1
        hasMore = true
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        if (userId <= 0L) {
            showError(getString(R.string.order_info_not_found))
            return
        }
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                currentUserId = 0L
                resetData()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                val previousUserId = currentUserId
                val accountChanged = previousUserId != 0L && previousUserId != state.user.id
                loggedIn = true
                currentUserId = state.user.id
                when {
                    userId == currentUserId -> {
                        resetData()
                        showError(getString(R.string.order_info_self))
                    }

                    accountChanged -> {
                        resetData()
                        loadFirstPage()
                    }

                    !loaded && !loading -> loadFirstPage()
                    loaded -> {
                        // View 重建后 Adapter 是新的，用缓存状态恢复头像、资料与菜品列表。
                        profile?.let { renderProfile(it) }
                        binding.rvDishes.submitList(recipes.toList())
                        renderRecipeContent()
                    }
                }
            }
        }
    }

    private fun resetData() {
        reservationDialog?.dismissDialog()
        reservationDialog = null
        generation++
        loading = false
        loaded = false
        profile = null
        recipes.clear()
        page = 1
        hasMore = true
    }

    private fun loadFirstPage() {
        if (!loggedIn || loading || userId <= 0L) return
        if (userId == currentUserId) {
            showError(getString(R.string.order_info_self))
            return
        }
        loading = true
        loaded = false
        page = 1
        hasMore = true
        showLoading()
        val requestGeneration = ++generation
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userProfile = userRepository.profile(userId)
                if (!isCurrent(requestGeneration)) return@launch
                profile = userProfile
                renderProfile(userProfile)
                if (!userProfile.isFriend) {
                    recipes.clear()
                    loaded = true
                    renderRecipeContent()
                    return@launch
                }
                showRecipeLoading()
                val firstPage = userRepository.recipes(userId, page = 1)
                if (!isCurrent(requestGeneration)) return@launch
                recipes.clear()
                firstPage.items.forEach { recipe ->
                    if (recipe.id > 0L && recipe.canReserve &&
                        recipes.none { it.id == recipe.id }
                    ) {
                        recipes += recipe
                    }
                }
                hasMore = firstPage.hasMore
                page = firstPage.page + 1
                loaded = true
                binding.rvDishes.submitList(recipes.toList())
                renderRecipeContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                when {
                    e.isUnauthorized -> {
                        SessionManager.logout()
                        showLoginRequired()
                    }

                    e.httpStatus == 404 -> showError(getString(R.string.order_info_not_found))
                    else -> showError(e.message)
                }
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                showError(getString(R.string.order_info_load_failed))
            } finally {
                if (requestGeneration == generation) loading = false
            }
        }
    }

    private fun loadMore() {
        val current = profile ?: return
        if (!loggedIn || loading || !loaded || !hasMore || !current.isFriend) return
        if (!isCurrent(generation)) return
        loading = true
        val requestGeneration = generation
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nextPage = userRepository.recipes(userId, page = page)
                if (!isCurrent(requestGeneration)) return@launch
                nextPage.items.forEach { recipe ->
                    if (recipe.id > 0L && recipe.canReserve &&
                        recipes.none { it.id == recipe.id }
                    ) {
                        recipes += recipe
                    }
                }
                hasMore = nextPage.hasMore
                page = nextPage.page + 1
                binding.rvDishes.submitList(recipes.toList())
                renderRecipeContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(requestGeneration)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.order_info_load_failed)
                    )
                }
            } catch (e: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                toast(R.string.order_info_load_failed)
            } finally {
                if (requestGeneration == generation) loading = false
            }
        }
    }

    private fun renderProfile(userProfile: UserProfileDto) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.llContent.visible()

        binding.tvNickname.text = userProfile.displayName
        binding.tvUsername.text = "@${userProfile.username}"
        val bio = userProfile.bio.orEmpty().trim()
        binding.tvBio.text = bio
        if (bio.isEmpty()) binding.tvBio.gone() else binding.tvBio.visible()

        bindOrderAvatar(
            binding.ivAvatar,
            binding.tvAvatarFallback,
            userProfile.displayName,
            userProfile.avatar
        )

        binding.tvFriendCount.text = getString(
            R.string.order_info_stats_friends,
            userProfile.friendCount
        )
        binding.tvRecipeCount.text = getString(
            R.string.order_info_stats_recipes,
            userProfile.recipeCount
        )
        binding.tvRating.text = getString(R.string.order_info_rating)
        binding.tvDelivery.text = getString(R.string.order_info_delivery)
    }

    private fun renderRecipeContent() {
        if (view == null) return
        binding.pbLoading.gone()
        val current = profile
        when {
            current == null -> {
                binding.rvDishes.gone()
                binding.tvEmpty.gone()
            }

            !current.isFriend -> {
                binding.rvDishes.gone()
                binding.tvEmpty.text = getString(R.string.order_info_not_friend)
                binding.tvEmpty.visible()
            }

            recipes.isEmpty() -> {
                binding.rvDishes.gone()
                binding.tvEmpty.text = getString(R.string.order_info_empty)
                binding.tvEmpty.visible()
            }

            else -> {
                binding.tvEmpty.gone()
                binding.rvDishes.visible()
            }
        }
    }

    private fun showRecipeLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.rvDishes.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
    }

    private fun openReservation(item: RecipeDto) {
        val current = profile ?: return
        if (!loggedIn || !current.isFriend || userId == currentUserId) return
        if (!item.canReserve || item.id <= 0L) return
        reservationDialog?.dismissDialog()
        val dialog = ReservationConfirmDialog(
            context = requireContext(),
            sellerId = current.id,
            sellerName = current.displayName,
            currentRecipe = item
        )
        reservationDialog = dialog
        dialog.setOnDismissListener {
            if (reservationDialog === dialog) reservationDialog = null
        }
        dialog.show()
    }

    private fun openRecipeDetail(item: RecipeDto) {
        if (item.id <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_COOK_BOOK_DETAIL)
                .args(bundleOf(KEY_BUNDLE_RECIPE_ID to item.id))
                .go()
        }.onFailure {
            toast(R.string.order_info_navigation_failed)
        }
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.order_info_login_failed)
        }
    }

    private fun isCurrent(requestGeneration: Int): Boolean {
        return view != null && requestGeneration == generation
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llContent.gone()
        binding.rvDishes.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llContent.gone()
        binding.rvDishes.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llContent.gone()
        binding.rvDishes.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.order_info_load_failed)
        binding.llError.visible()
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

/**
 * 读取用户 ID 路由参数；优先读取已解析 Bundle，再回退到 deep link query。
 */
private fun readUserIdArgument(arguments: Bundle?): Long {
    if (arguments == null) return 0L
    val direct = arguments.getLong(KEY_USER_ID, 0L)
    if (direct > 0L) return direct
    return NRoute.getArgument(arguments, KEY_USER_ID).toLongOrNull() ?: 0L
}

private fun bindOrderAvatar(
    imageView: ImageView,
    fallbackView: TextView,
    displayName: String,
    avatarUrl: String?
) {
    val url = NetworkConfig.resolveMediaUrl(avatarUrl)
    if (url == null) {
        imageView.gone()
        fallbackView.visible()
        fallbackView.text = orderFirstCharacter(displayName)
    } else {
        fallbackView.gone()
        imageView.visible()
        imageView.load(url)
    }
}

/** 取昵称首字作为头像占位，避免截断 emoji 等代理对字符。 */
private fun orderFirstCharacter(text: String): String {
    if (text.isEmpty()) return ""
    val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
    return text.substring(0, end)
}
