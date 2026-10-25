package com.rhys.relation.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_AVATAR
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_NICKNAME
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_USER_ID
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.Constants.KEY_USER_ID
import com.campaign.common.constants.RoutePath.PAGE_CHAT_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_ORDER_INFO
import com.campaign.common.constants.RoutePath.PAGE_USER_PROFILE
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserProfileDto
import com.example.network.repository.FriendRepository
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.relation.R
import com.rhys.relation.bindRelationAvatar
import com.rhys.relation.databinding.RelationFragmentUserProfileBinding
import com.rhys.relation.databinding.RelationItemRecipeBinding
import com.rhys.relation.model.UserProfileAction
import com.rhys.relation.model.UserProfileActionResolver
import com.rhys.relation.openLoginPage
import com.rhys.relation.readUserIdArgument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 用户主页：公开资料、好友关系操作与当前查看者可见的菜谱列表。
 *
 * 可见菜谱与好友关系均由后端判定；页面只根据返回结果展示，不自行扩大权限。
 */
@Route(PAGE_USER_PROFILE)
class UserProfileFragment :
    BaseBindFragment<RelationFragmentUserProfileBinding>(RelationFragmentUserProfileBinding::inflate) {

    private val userRepository = UserRepository()
    private val friendRepository = FriendRepository()
    private val recipes = mutableListOf<RecipeDto>()

    private val userId: Long by lazy(LazyThreadSafetyMode.NONE) {
        readUserIdArgument(arguments)
    }

    private var profile: UserProfileDto? = null
    private var currentUserId: Long = 0L
    private var loggedIn = false
    private var pendingRequested = false
    private var loading = false
    private var loaded = false
    private var page = 1
    private var hasMore = true

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: RelationFragmentUserProfileBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }

        binding.rvRecipes.liner().buildAdapter<RecipeDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(RelationItemRecipeBinding::class.java)
            bind<RelationItemRecipeBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvName.text = item.name
                val description = item.description.orEmpty().trim()
                itemBinding.tvDescription.text = description
                if (description.isEmpty()) itemBinding.tvDescription.gone() else itemBinding.tvDescription.visible()
                val category = item.category.orEmpty().trim()
                itemBinding.tvCategory.text = category
                if (category.isEmpty()) itemBinding.tvCategory.gone() else itemBinding.tvCategory.visible()

                val imageUrl = NetworkConfig.resolveMediaUrl(item.imageUrl)
                if (imageUrl == null) {
                    itemBinding.ivImage.setImageDrawable(null)
                } else {
                    itemBinding.ivImage.load(imageUrl)
                }
            }
            setOnItemClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemClickListener
                openRecipeDetail(item)
            }
        }
        binding.rvRecipes.addItemDecoration(RvDecoration.Builder().setVertical(2).build())
        binding.rvRecipes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2 && hasMore) {
                    loadMoreRecipes()
                }
            }
        })
    }

    override fun initData(binding: RelationFragmentUserProfileBinding) {
        if (userId <= 0L) {
            showError(getString(R.string.relation_user_not_found))
        }
    }

    override fun initListener(binding: RelationFragmentUserProfileBinding) {
        binding.btnAddFriend.setOnClickListener { sendFriendRequest() }
        binding.btnMessage.setOnClickListener { openChat() }
        binding.btnUnfriend.setOnClickListener { confirmUnfriend() }
        binding.btnReserveDishes.setOnClickListener { openReserveDishes() }
        binding.btnLogin.setOnClickListener {
            if (!openLoginPage(this)) toast(R.string.relation_login_failed)
        }
        binding.btnRetry.setOnClickListener {
            if (userId <= 0L) {
                showError(getString(R.string.relation_user_not_found))
            } else {
                loadProfileFirstPage()
            }
        }
    }

    override fun initObserver(binding: RelationFragmentUserProfileBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    renderAuthState(state)
                }
            }
        }
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                profile = null
                loaded = false
                recipes.clear()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                val previousUserId = currentUserId
                val accountChanged =
                    previousUserId != 0L && previousUserId != state.user.id
                loggedIn = true
                currentUserId = state.user.id
                if (accountChanged) {
                    // 账号切换后旧资料与可见菜谱均失效，清空后按新账号重新拉取。
                    profile = null
                    recipes.clear()
                    loaded = false
                    page = 1
                    hasMore = true
                    pendingRequested = false
                }
                when {
                    userId <= 0L -> showError(getString(R.string.relation_user_not_found))
                    accountChanged -> loadProfileFirstPage()
                    profile == null && !loading -> loadProfileFirstPage()
                    profile != null -> {
                        // View 重建后 Adapter 是新的，必须用缓存状态重新绑定并提交列表。
                        renderProfile(profile!!)
                        binding.rvRecipes.submitList(recipes.toList())
                        renderRecipeContent()
                    }
                }
            }
        }
    }

    private fun loadProfileFirstPage() {
        if (!loggedIn || loading || userId <= 0L) return
        loading = true
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userProfile = userRepository.profile(userId)
                val firstPage = userRepository.recipes(userId, page = 1)
                profile = userProfile
                recipes.clear()
                recipes.addAll(firstPage.items)
                hasMore = firstPage.hasMore
                page = firstPage.page + 1
                loaded = true
                renderProfile(userProfile)
                binding.rvRecipes.submitList(recipes.toList())
                renderRecipeContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    showError(e.message)
                }
            } catch (e: Exception) {
                showError(getString(R.string.relation_error_default))
            } finally {
                loading = false
            }
        }
    }

    private fun loadMoreRecipes() {
        if (!loggedIn || loading || !loaded || !hasMore) return
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nextPage = userRepository.recipes(userId, page = page)
                recipes.addAll(nextPage.items)
                hasMore = nextPage.hasMore
                page = nextPage.page + 1
                binding.rvRecipes.submitList(recipes.toList())
                renderRecipeContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                }
            } catch (e: Exception) {
                toast(R.string.relation_error_default)
            } finally {
                loading = false
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

        bindRelationAvatar(
            binding.ivAvatar,
            binding.tvAvatarFallback,
            userProfile.displayName,
            userProfile.avatar
        )

        binding.tvFriendCount.text = getString(R.string.relation_stats_friends, userProfile.friendCount)
        binding.tvRecipeCount.text = getString(R.string.relation_stats_recipes, userProfile.recipeCount)

        val isSelf = currentUserId > 0L && currentUserId == userProfile.id
        when (UserProfileActionResolver.resolve(loggedIn, isSelf, userProfile.isFriend)) {
            UserProfileAction.LOGIN_REQUIRED -> showLoginRequired()
            UserProfileAction.NONE -> hideRelationActions()
            UserProfileAction.MESSAGE_AND_UNFRIEND -> {
                binding.btnAddFriend.gone()
                binding.btnMessage.visible()
                binding.btnUnfriend.visible()
                binding.btnUnfriend.isEnabled = true
                val canReserve = UserProfileActionResolver.canReserveDishes(
                    isLoggedIn = loggedIn,
                    isSelf = isSelf,
                    isFriend = userProfile.isFriend
                )
                if (canReserve) binding.btnReserveDishes.visible() else binding.btnReserveDishes.gone()
            }

            UserProfileAction.ADD_FRIEND -> {
                binding.btnMessage.gone()
                binding.btnUnfriend.gone()
                binding.btnReserveDishes.gone()
                binding.btnAddFriend.visible()
                binding.btnAddFriend.isEnabled = !pendingRequested
                binding.btnAddFriend.text = if (pendingRequested) {
                    getString(R.string.relation_action_requested)
                } else {
                    getString(R.string.relation_action_add_friend)
                }
            }
        }
    }

    private fun hideRelationActions() {
        binding.btnAddFriend.gone()
        binding.btnMessage.gone()
        binding.btnUnfriend.gone()
        binding.btnReserveDishes.gone()
    }

    private fun renderRecipeContent() {
        if (view == null) return
        if (recipes.isEmpty()) {
            binding.rvRecipes.gone()
            binding.tvRecipeEmpty.visible()
        } else {
            binding.tvRecipeEmpty.gone()
            binding.rvRecipes.visible()
        }
    }

    private fun sendFriendRequest() {
        val current = profile ?: return
        if (!loggedIn || loading || pendingRequested) return
        binding.btnAddFriend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            var reloading = false
            var unauthorized = false
            try {
                friendRepository.sendRequest(current.id)
                pendingRequested = true
                renderProfile(current)
                toast(R.string.relation_pending_hint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    unauthorized = true
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    when (e.code) {
                        "FRIEND_REQUEST_EXISTS" -> {
                            pendingRequested = true
                            renderProfile(current)
                        }

                        "ALREADY_FRIENDS" -> {
                            toast(e.message ?: getString(R.string.relation_error_default))
                            reloading = true
                            loadProfileFirstPage()
                        }

                        else -> toast(e.message ?: getString(R.string.relation_error_default))
                    }
                }
            } catch (e: Exception) {
                toast(R.string.relation_error_default)
            } finally {
                if (!reloading && !unauthorized && view != null && profile != null) {
                    renderProfile(profile!!)
                }
            }
        }
    }

    private fun confirmUnfriend() {
        val current = profile ?: return
        if (!loggedIn || loading) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.relation_unfriend_title)
            .setMessage(R.string.relation_unfriend_message)
            .setNegativeButton(R.string.relation_unfriend_cancel, null)
            .setPositiveButton(R.string.relation_unfriend_confirm) { _, _ ->
                performUnfriend(current)
            }
            .show()
    }

    private fun performUnfriend(current: UserProfileDto) {
        if (loading) return
        binding.btnUnfriend.isEnabled = false
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            var reloading = false
            var unauthorized = false
            try {
                friendRepository.deleteFriend(current.id)
                toast(R.string.relation_unfriend_success)
                pendingRequested = false
                // 重新拉取主页与菜谱，好友可见内容立即从列表消失。
                // 先释放本协程的 loading，再交给新的加载任务。
                reloading = true
                loading = false
                loadProfileFirstPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    unauthorized = true
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                    if (e.code == "FRIEND_NOT_FOUND") {
                        // 关系已在其他端变化，重新拉取以恢复真实状态。
                        reloading = true
                        loading = false
                        loadProfileFirstPage()
                    }
                }
            } catch (e: Exception) {
                toast(R.string.relation_error_default)
            } finally {
                if (!reloading) {
                    loading = false
                    if (!unauthorized && view != null && profile != null) {
                        renderProfile(profile!!)
                    }
                }
            }
        }
    }

    /**
     * 好友主页“发消息”：只带对方用户 ID 与可用的公开资料，由聊天详情页
     * 调用服务端创建/打开会话。好友关系仍由服务端判定，客户端不自行放行。
     */
    private fun openChat() {
        val current = profile ?: return
        if (current.id <= 0L) return
        val arguments = Bundle().apply {
            putLong(KEY_BUNDLE_PEER_USER_ID, current.id)
            current.displayName.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { putString(KEY_BUNDLE_PEER_NICKNAME, it) }
            current.avatar?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { putString(KEY_BUNDLE_PEER_AVATAR, it) }
        }
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_CHAT_DETAIL)
                .args(arguments)
                .go()
        }.onFailure {
            toast(R.string.relation_error_default)
        }
    }

    /**
     * 好友主页“预订菜品”：只带对方用户 ID 进入 3.9 页面。
     * 可预订菜品与好友关系由后端与 3.9 页面再次判定，入口本身不放宽权限。
     */
    private fun openReserveDishes() {
        val current = profile ?: return
        if (!loggedIn || current.id <= 0L || !current.isFriend) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_ORDER_INFO)
                .args(bundleOf(KEY_USER_ID to current.id))
                .go()
        }.onFailure {
            toast(R.string.relation_navigation_failed)
        }
    }

    private fun openRecipeDetail(item: RecipeDto) {
        if (item.id <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_COOK_BOOK_DETAIL)
                .args(bundleOf(KEY_BUNDLE_RECIPE_ID to item.id))
                .go()
        }.onFailure {
            toast(R.string.relation_error_default)
        }
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llContent.gone()
        binding.rvRecipes.gone()
        binding.tvRecipeEmpty.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llContent.gone()
        binding.rvRecipes.gone()
        binding.tvRecipeEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llContent.gone()
        binding.rvRecipes.gone()
        binding.tvRecipeEmpty.gone()
        binding.llLoginRequired.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.relation_error_default)
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
