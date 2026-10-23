package com.rhys.relation.search

import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.R as CommonR
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_FRIEND_SEARCH
import com.example.network.AuthException
import com.example.network.bean.UserListItemDto
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.rhys.relation.R
import com.rhys.relation.bindRelationAvatar
import com.rhys.relation.databinding.RelationFragmentFriendSearchBinding
import com.rhys.relation.databinding.RelationItemUserBinding
import com.rhys.relation.openLoginPage
import com.rhys.relation.openUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 用户搜索/添加好友入口。
 * 搜索结果只展示后端返回的公开资料与当前好友关系，不自行推断权限。
 */
@Route(PAGE_FRIEND_SEARCH)
class FriendSearchFragment :
    BaseBindFragment<RelationFragmentFriendSearchBinding>(RelationFragmentFriendSearchBinding::inflate) {

    private val repository = UserRepository()
    private val items = mutableListOf<UserListItemDto>()

    private var keyword = ""
    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loaded = false
    private var loggedIn = false
    private var currentUserId = 0L
    private var requestGeneration = 0

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: RelationFragmentFriendSearchBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.rvUsers.liner().buildAdapter<UserListItemDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(RelationItemUserBinding::class.java)
            bind<RelationItemUserBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvNickname.text = item.displayName
                itemBinding.tvUsername.text = "@${item.username}"
                itemBinding.tvRelation.text = if (item.isFriend) {
                    getString(R.string.relation_relation_friend)
                } else {
                    getString(R.string.relation_relation_not_friend)
                }
                itemBinding.tvRelation.setTextColor(
                    requireContext().getColor(
                        if (item.isFriend) CommonR.color.color_primary else CommonR.color.textNormal
                    )
                )
                bindRelationAvatar(
                    itemBinding.ivAvatar,
                    itemBinding.tvAvatarFallback,
                    item.displayName,
                    item.avatar
                )
            }
            setOnItemClickListener { _, _, position ->
                val user = items.getOrNull(position) ?: return@setOnItemClickListener
                openUserProfile(this@FriendSearchFragment, user.id)
            }
        }
        binding.rvUsers.addItemDecoration(RvDecoration.Builder().setVertical(4).build())
        binding.rvUsers.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
        showInitial()
    }

    override fun initData(binding: RelationFragmentFriendSearchBinding) {
    }

    override fun initListener(binding: RelationFragmentFriendSearchBinding) {
        binding.btnSearch.setOnClickListener { submitSearch() }
        binding.etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        binding.btnLogin.setOnClickListener {
            if (!openLoginPage(this)) toast(R.string.relation_login_failed)
        }
        binding.btnRetry.setOnClickListener { searchFirstPage() }
    }

    override fun initObserver(binding: RelationFragmentFriendSearchBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    renderAuthState(state)
                }
            }
        }
    }

    override fun onViewDestroy() {
        requestGeneration++
        loading = false
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                currentUserId = 0L
                resetContent()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                if (currentUserId != state.user.id) {
                    currentUserId = state.user.id
                    resetContent()
                }
                loggedIn = true
                if (loaded) {
                    renderContent()
                } else if (!loading) {
                    if (keyword.isBlank()) showInitial() else searchFirstPage()
                }
            }
        }
    }

    private fun resetContent() {
        requestGeneration++
        loading = false
        loaded = false
        page = 1
        hasMore = true
        items.clear()
        binding.rvUsers.submitList(emptyList())
    }

    private fun submitSearch() {
        if (loading) return
        val newKeyword = binding.etKeyword.text?.toString()?.trim().orEmpty()
        if (newKeyword.isEmpty()) {
            keyword = ""
            resetContent()
            showInitial()
            toast(R.string.relation_search_hint)
            return
        }
        if (!loggedIn) {
            showLoginRequired()
            return
        }
        keyword = newKeyword
        searchFirstPage()
    }

    private fun searchFirstPage() {
        if (!loggedIn || loading) return
        if (keyword.isBlank()) {
            showInitial()
            return
        }
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
        if (!loggedIn || loading || keyword.isBlank()) return
        loading = true
        val generation = requestGeneration
        if (reset) showLoading() else binding.llError.gone()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.search(keyword = keyword, page = page)
                if (!isCurrent(generation)) return@launch
                if (reset) items.clear()
                items.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (reset) showError(getString(R.string.relation_error_default))
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        val state = SessionManager.state.value as? AuthState.LoggedIn
        return view != null && loggedIn && generation == requestGeneration && state?.user?.id == currentUserId
    }

    private fun renderContent() {
        if (view == null) return
        // 同一账号的已完成搜索在 View 重建后恢复，包括空结果与分页状态。
        binding.rvUsers.submitList(items.toList())
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        if (items.isEmpty()) {
            binding.rvUsers.gone()
            binding.tvEmpty.text = getString(R.string.relation_search_empty)
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
            binding.rvUsers.visible()
        }
    }

    private fun showInitial() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.rvUsers.gone()
        binding.tvEmpty.text = getString(R.string.relation_search_initial)
        binding.tvEmpty.visible()
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rvUsers.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvUsers.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvUsers.gone()
        binding.tvEmpty.gone()
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
