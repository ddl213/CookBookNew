package com.rhys.relation.friend

import android.widget.Toast
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
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_FRIEND_REQUESTS
import com.campaign.common.constants.RoutePath.PAGE_FRIEND_SEARCH
import com.campaign.common.constants.RoutePath.PAGE_FRIENDS
import com.example.network.AuthException
import com.example.network.bean.FriendDto
import com.example.network.repository.FriendRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.relation.R
import com.rhys.relation.bindRelationAvatar
import com.rhys.relation.databinding.RelationFragmentFriendsBinding
import com.rhys.relation.databinding.RelationItemFriendBinding
import com.rhys.relation.openLoginPage
import com.rhys.relation.openUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 好友列表。
 *
 * 登录态由 SessionManager 统一提供；好友关系与数据由后端判定，页面只负责展示与导航。
 */
@Route(PAGE_FRIENDS)
class FriendsFragment :
    BaseBindFragment<RelationFragmentFriendsBinding>(RelationFragmentFriendsBinding::inflate) {

    private val repository = FriendRepository()
    private val items = mutableListOf<FriendDto>()

    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loaded = false
    private var loggedIn = false
    private var refreshOnResume = false

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: RelationFragmentFriendsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.titleBar.setRightText(getString(R.string.relation_search_entry))
        binding.titleBar.setRightClick { openSearch() }

        binding.rvFriends.liner().buildAdapter<FriendDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(RelationItemFriendBinding::class.java)
            bind<RelationItemFriendBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvNickname.text = item.displayName
                itemBinding.tvUsername.text = "@${item.username}"
                bindRelationAvatar(
                    itemBinding.ivAvatar,
                    itemBinding.tvAvatarFallback,
                    item.displayName,
                    item.avatar
                )
            }
            setOnItemClickListener { _, _, position ->
                val friend = items.getOrNull(position) ?: return@setOnItemClickListener
                refreshOnResume = true
                openUserProfile(this@FriendsFragment, friend.id)
            }
        }
        binding.rvFriends.addItemDecoration(
            RvDecoration.Builder().setVertical(4).build()
        )
        binding.rvFriends.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

    override fun initData(binding: RelationFragmentFriendsBinding) {
    }

    override fun initListener(binding: RelationFragmentFriendsBinding) {
        binding.tvSearchEntry.setOnClickListener { openSearch() }
        binding.tvRequestsEntry.setOnClickListener { openRequests() }
        binding.btnLogin.setOnClickListener {
            if (!openLoginPage(this)) toast(R.string.relation_login_failed)
        }
        binding.btnRetry.setOnClickListener { loadFirst() }
    }

    override fun initObserver(binding: RelationFragmentFriendsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    renderAuthState(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (refreshOnResume && loggedIn && !loading) {
            refreshOnResume = false
            loadFirst()
        }
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                loaded = false
                items.clear()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                loggedIn = true
                if (!loaded && !loading) loadFirst()
            }
        }
    }

    private fun loadFirst() {
        if (!loggedIn || loading) return
        page = 1
        hasMore = true
        loaded = false
        loadPage(reset = true)
    }

    private fun loadMore() {
        if (!loggedIn || loading || !loaded || !hasMore) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        if (!loggedIn || loading) return
        loading = true
        if (reset) showLoading() else hideError()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listFriends(page = page)
                if (reset) items.clear()
                items.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                binding.rvFriends.submitList(items.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                }
            } catch (e: Exception) {
                if (reset) showError(getString(R.string.relation_error_default))
            } finally {
                loading = false
            }
        }
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        if (items.isEmpty()) {
            binding.rvFriends.gone()
            binding.tvEmpty.text = getString(R.string.relation_friend_empty)
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
            binding.rvFriends.visible()
        }
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rvFriends.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvFriends.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvFriends.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.relation_error_default)
        binding.llError.visible()
    }

    private fun hideError() {
        if (view == null) return
        binding.llError.gone()
    }

    private fun openSearch() {
        refreshOnResume = true
        navigate(PAGE_FRIEND_SEARCH)
    }

    private fun openRequests() {
        refreshOnResume = true
        navigate(PAGE_FRIEND_REQUESTS)
    }

    private fun navigate(path: String) {
        runCatching {
            NRoute.withNavController(findNavController()).deepLink(path).go()
        }.onFailure {
            toast(R.string.relation_error_default)
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
