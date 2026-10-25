package com.example.index.fragment

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.campaign.common.constants.RoutePath.PAGE_INDEX_SEARCH
import com.example.index.bindIndexAvatar
import com.example.index.bindIndexRecipeCard
import com.example.index.openIndexLogin
import com.example.index.openIndexRecipeDetail
import com.example.index.openIndexUserProfile
import com.example.index.toastIndex
import com.example.index.R
import com.example.index.databinding.IndexFragmentSearchBinding
import com.example.index.databinding.IndexItemRecipeBinding
import com.example.index.databinding.IndexItemUserBinding
import com.example.network.AuthException
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserListItemDto
import com.example.network.repository.RecipeRepository
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 首页搜索：菜谱公开搜索与用户搜索两种模式。
 *
 * 菜谱模式匿名可用；用户模式需要登录，未登录时不请求 /users。
 * 账号切换、退出与 401 统一通过 SessionManager 状态流处理，并用 generation 丢弃迟到响应。
 */
@Route(PAGE_INDEX_SEARCH)
class IndexSearchFragment :
    BaseBindFragment<IndexFragmentSearchBinding>(IndexFragmentSearchBinding::inflate) {

    private val recipeRepository = RecipeRepository()
    private val userRepository = UserRepository()

    private val recipes = mutableListOf<RecipeDto>()
    private val users = mutableListOf<UserListItemDto>()

    private var mode = MODE_RECIPE
    private var keyword = ""
    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loaded = false
    private var loggedIn = false
    private var currentUserId = 0L
    /** 新搜索、模式切换、登录态/账号变化时递增，丢弃旧请求的迟到响应。 */
    private var requestGeneration = 0

    override fun initTitleBar(): View? = binding.titleBar

    override fun initView(binding: IndexFragmentSearchBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        if (keyword.isNotBlank()) {
            binding.etKeyword.setText(keyword)
            binding.etKeyword.setSelection(keyword.length)
        }
        bindRecipeList(binding)
        bindUserList(binding)
        updateModeUi()
        showInitial()
    }

    override fun initData(binding: IndexFragmentSearchBinding) {
    }

    override fun initListener(binding: IndexFragmentSearchBinding) {
        binding.btnSearch.setOnClickListener { submitSearch() }
        binding.etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        binding.btnRetry.setOnClickListener { searchFirstPage() }
        binding.btnLogin.setOnClickListener { openIndexLogin(this) }
        binding.llModeRecipe.setOnClickListener { switchMode(MODE_RECIPE) }
        binding.llModeUser.setOnClickListener { switchMode(MODE_USER) }
    }

    override fun initObserver(binding: IndexFragmentSearchBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        requestGeneration++
        loading = false
        loaded = false
        recipes.clear()
        users.clear()
    }

    private fun bindRecipeList(binding: IndexFragmentSearchBinding) {
        binding.rvRecipes.liner().buildAdapter<RecipeDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemRecipeBinding::class.java)
            bind<IndexItemRecipeBinding> { holder, _, item, _ ->
                bindIndexRecipeCard(holder.binding, item, this@IndexSearchFragment)
            }
            setOnItemClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemClickListener
                openIndexRecipeDetail(this@IndexSearchFragment, item.id)
            }
        }
        binding.rvRecipes.addItemDecoration(RvDecoration.Builder().setVertical(10).build())
        binding.rvRecipes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore()
            }
        })
    }

    private fun bindUserList(binding: IndexFragmentSearchBinding) {
        binding.rvUsers.liner().buildAdapter<UserListItemDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemUserBinding::class.java)
            bind<IndexItemUserBinding> { holder, _, item, _ ->
                holder.binding.tvNickname.text = item.displayName
                holder.binding.tvUsername.text = getString(R.string.index_username_format, item.username)
                bindIndexAvatar(
                    holder.binding.ivAvatar,
                    holder.binding.tvAvatarFallback,
                    item.displayName,
                    item.avatar
                )
            }
            setOnItemClickListener { _, _, position ->
                val item = users.getOrNull(position) ?: return@setOnItemClickListener
                openIndexUserProfile(this@IndexSearchFragment, item.id)
            }
        }
        binding.rvUsers.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                if (!loaded && !loading) showLoading()
            }

            AuthState.LoggedOut -> {
                val wasLoggedIn = loggedIn
                loggedIn = false
                currentUserId = 0L
                requestGeneration++
                loading = false
                if (wasLoggedIn) clearResults()
                if (mode == MODE_USER) {
                    clearResults()
                    showLoginRequired()
                } else if (keyword.isNotBlank()) {
                    if (!loaded) searchFirstPage()
                } else {
                    showInitial()
                }
            }

            is AuthState.LoggedIn -> {
                val accountChanged = currentUserId != 0L && currentUserId != state.user.id
                loggedIn = true
                currentUserId = state.user.id
                if (accountChanged) {
                    requestGeneration++
                    loading = false
                    clearResults()
                }
                if (keyword.isNotBlank()) {
                    if (!loaded && !loading) searchFirstPage()
                } else {
                    showInitial()
                }
            }
        }
    }

    private fun submitSearch() {
        if (view == null) return
        val input = binding.etKeyword.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) {
            keyword = ""
            resetContent()
            showInitial()
            toastIndex(this, R.string.index_search_initial)
            return
        }
        keyword = input
        hideKeyboard()
        searchFirstPage()
    }

    private fun switchMode(newMode: Int) {
        if (view == null || mode == newMode) return
        mode = newMode
        resetContent()
        updateModeUi()
        if (mode == MODE_USER && !loggedIn) {
            showLoginRequired()
        } else if (keyword.isBlank()) {
            showInitial()
        } else {
            searchFirstPage()
        }
    }

    private fun updateModeUi() {
        if (view == null) return
        val recipeMode = mode == MODE_RECIPE
        binding.tvModeRecipe.isSelected = recipeMode
        binding.tvModeUser.isSelected = !recipeMode
        binding.vModeRecipeIndicator.visibility = if (recipeMode) View.VISIBLE else View.INVISIBLE
        binding.vModeUserIndicator.visibility = if (recipeMode) View.INVISIBLE else View.VISIBLE
    }

    private fun searchFirstPage() {
        if (view == null) return
        if (keyword.isBlank()) {
            showInitial()
            return
        }
        if (mode == MODE_USER && !loggedIn) {
            showLoginRequired()
            return
        }
        val generation = ++requestGeneration
        loading = true
        loaded = false
        page = 1
        hasMore = true
        showLoading()
        fetch(generation, pageToLoad = 1, reset = true)
    }

    private fun loadMore() {
        if (view == null || loading || !loaded || !hasMore || keyword.isBlank()) return
        if (mode == MODE_USER && !loggedIn) return
        val generation = requestGeneration
        loading = true
        fetch(generation, pageToLoad = page, reset = false)
    }

    private fun fetch(generation: Int, pageToLoad: Int, reset: Boolean) {
        val requestMode = mode
        val requestKeyword = keyword
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (requestMode == MODE_RECIPE) {
                    val result = recipeRepository.listPublic(
                        category = null,
                        query = requestKeyword,
                        page = pageToLoad
                    )
                    if (!isCurrent(generation)) return@launch
                    if (reset) recipes.clear()
                    recipes.addAll(result.items)
                    hasMore = result.hasMore
                    page = result.page + 1
                    loaded = true
                    binding.rvRecipes.submitList(recipes.toList())
                } else {
                    val result = userRepository.search(keyword = requestKeyword, page = pageToLoad)
                    if (!isCurrent(generation)) return@launch
                    if (reset) users.clear()
                    users.addAll(result.items)
                    hasMore = result.hasMore
                    page = result.page + 1
                    loaded = true
                    binding.rvUsers.submitList(users.toList())
                }
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    // 状态流负责切换登录引导；不要把普通失败误显示成无权限。
                    SessionManager.logout()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toastIndex(this@IndexSearchFragment, e.message ?: getString(R.string.index_search_error))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (reset) {
                    showError(getString(R.string.index_search_error))
                } else {
                    toastIndex(this@IndexSearchFragment, R.string.index_search_error)
                }
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return view != null && generation == requestGeneration
    }

    /** 重新发起搜索或切换模式时使用：同时使在途请求失效。 */
    private fun resetContent() {
        requestGeneration++
        clearResults()
    }

    private fun clearResults() {
        loading = false
        loaded = false
        page = 1
        hasMore = true
        recipes.clear()
        users.clear()
        binding.rvRecipes.submitList(emptyList<RecipeDto>())
        binding.rvUsers.submitList(emptyList<UserListItemDto>())
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.llLoginRequired.gone()
        if (mode == MODE_RECIPE) {
            binding.rvUsers.gone()
            if (recipes.isEmpty()) {
                binding.rvRecipes.gone()
                binding.tvEmpty.text = getString(R.string.index_search_empty_recipes)
                binding.tvEmpty.visible()
            } else {
                binding.tvEmpty.gone()
                binding.rvRecipes.visible()
            }
        } else {
            binding.rvRecipes.gone()
            if (users.isEmpty()) {
                binding.rvUsers.gone()
                binding.tvEmpty.text = getString(R.string.index_search_empty_users)
                binding.tvEmpty.visible()
            } else {
                binding.tvEmpty.gone()
                binding.rvUsers.visible()
            }
        }
    }

    private fun showInitial() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.llLoginRequired.gone()
        binding.rvRecipes.gone()
        binding.rvUsers.gone()
        binding.tvEmpty.text = getString(R.string.index_search_initial)
        binding.tvEmpty.visible()
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llError.gone()
        binding.llLoginRequired.gone()
        binding.tvEmpty.gone()
        binding.rvRecipes.gone()
        binding.rvUsers.gone()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.tvEmpty.gone()
        binding.rvRecipes.gone()
        binding.rvUsers.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.index_search_error)
        binding.llError.visible()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rvRecipes.gone()
        binding.rvUsers.gone()
        binding.llLoginRequired.visible()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etKeyword.windowToken, 0)
        binding.etKeyword.clearFocus()
    }

    private companion object {
        const val MODE_RECIPE = 0
        const val MODE_USER = 1
    }
}
