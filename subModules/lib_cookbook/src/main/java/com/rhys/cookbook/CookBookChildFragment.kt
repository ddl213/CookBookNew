package com.rhys.cookbook

import android.view.View
import android.widget.PopupMenu
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
import com.android.common.bean.KEY_BUNDLE_PAGE_ID
import com.android.common.ext.buildRefreshAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.load
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_ADD
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_CHILD
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.example.network.repository.RecipeRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.cookbook.databinding.CookbookFragmentCookBookChildBinding
import com.rhys.cookbook.databinding.CookbookLayoutChildBinding
import com.rhys.cookbook.form.RecipeFormOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * “我的菜谱”列表：按当前 Tab 只请求已发布或草稿。
 *
 * 草稿只在作者自己的列表中出现，是否可见由后端判定；本页面不扩大任何可见范围。
 * 卡片正文点击仍为详情占位，编辑与删除走“更多”菜单。
 */
@Route(PAGE_COOK_BOOK_CHILD)
class CookBookChildFragment :
    BaseBindFragment<CookbookFragmentCookBookChildBinding>(CookbookFragmentCookBookChildBinding::inflate) {

    private val repository = RecipeRepository()
    private val recipes = mutableListOf<RecipeDto>()

    private val status: String by lazy(LazyThreadSafetyMode.NONE) {
        when (arguments?.getString(KEY_BUNDLE_PAGE_ID)) {
            RecipeFormOptions.STATUS_DRAFT -> RecipeFormOptions.STATUS_DRAFT
            else -> RecipeFormOptions.STATUS_PUBLISHED
        }
    }

    private var loggedIn = false
    private var currentUserId = 0L
    private var loading = false
    private var loaded = false
    private var page = 1
    private var hasMore = true
    private var deletingId = 0L
    private var refreshPending = false
    /** 登录态/账号切换时递增，丢弃旧账号仍在途的响应，避免把上一账号数据渲染出来。 */
    private var requestGeneration = 0

    override fun initTitleBar(): View? = null

    override fun initView(binding: CookbookFragmentCookBookChildBinding) {
        initRecyclerView()
    }

    override fun initData(binding: CookbookFragmentCookBookChildBinding) {
        binding.tvEmpty.text = if (status == RecipeFormOptions.STATUS_DRAFT) {
            getString(R.string.cookbook_empty_draft)
        } else {
            getString(R.string.cookbook_empty_published)
        }
    }

    override fun initListener(binding: CookbookFragmentCookBookChildBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener {
            if (loggedIn) loadFirstPage() else openLogin()
        }
    }

    override fun initObserver(binding: CookbookFragmentCookBookChildBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从添加/编辑页返回时自动刷新，避免列表停留在旧数据。
        if (loggedIn && loaded && !loading) loadFirstPage()
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                currentUserId = 0L
                loaded = false
                loading = false
                page = 1
                hasMore = true
                refreshPending = false
                requestGeneration++
                recipes.clear()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                loggedIn = true
                val userIdChanged = currentUserId != 0L && currentUserId != state.user.id
                currentUserId = state.user.id
                if (!loaded || userIdChanged) {
                    loadFirstPage()
                }
            }
        }
    }

    private fun initRecyclerView() {
        binding.rv.addItemDecoration(
            RvDecoration.Builder()
                .setVertical(6)
                .build()
        )
        binding.rv.liner().buildRefreshAdapter<RecipeDto>(header = true) {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(CookbookLayoutChildBinding::class.java)
            bind<CookbookLayoutChildBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                val imageUrl = NetworkConfig.resolveMediaUrl(item.imageUrl)
                if (imageUrl == null) {
                    itemBinding.ivRecipePic.setImageDrawable(null)
                } else {
                    itemBinding.ivRecipePic.load(imageUrl)
                }
                itemBinding.tvRecipeName.text = item.name
                val description = item.description.orEmpty().trim()
                itemBinding.tvRecipeDesc.text = description
                if (description.isEmpty()) itemBinding.tvRecipeDesc.gone() else itemBinding.tvRecipeDesc.visible()

                itemBinding.tvRecipeMeta.text = getString(
                    R.string.cookbook_meta_format,
                    statusLabel(item),
                    visibilityLabel(item)
                )

                val category = item.category.orEmpty().trim()
                itemBinding.tvRecipeCategory.text = category
                if (category.isEmpty()) itemBinding.tvRecipeCategory.gone() else itemBinding.tvRecipeCategory.visible()

                val authorName = item.author?.displayName?.trim().orEmpty()
                itemBinding.tvRecipeAuthor.text = getString(
                    R.string.cookbook_author_format,
                    authorName.ifEmpty { getString(R.string.cookbook_my_name) }
                )

                itemBinding.tvMore.setOnClickListener { showItemMenu(it, item) }
            }
            setOnItemClickListener { _, _, position ->
                if (position in recipes.indices) {
                    toast(R.string.cookbook_detail_placeholder)
                }
            }
        }
        binding.rv.setOnRefreshListener { loadFirstPage() }
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

    private fun statusLabel(item: RecipeDto): String {
        return if (RecipeFormOptions.statusOf(item.status) == RecipeFormOptions.STATUS_DRAFT) {
            getString(R.string.cookbook_status_draft)
        } else {
            getString(R.string.cookbook_status_published)
        }
    }

    private fun visibilityLabel(item: RecipeDto): String {
        return if (RecipeFormOptions.visibilityOf(item.visibility) == RecipeFormOptions.VISIBILITY_PUBLIC) {
            getString(R.string.cookbook_visibility_public)
        } else {
            getString(R.string.cookbook_visibility_friends)
        }
    }

    private fun loadFirstPage() {
        if (!loggedIn || loading) return
        loading = true
        val generation = ++requestGeneration
        val isInitial = !loaded
        if (isInitial) showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listMine(status, page = 1)
                if (!isCurrent(generation)) return@launch
                recipes.clear()
                recipes.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                binding.rv.submitList(recipes.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else if (isInitial) {
                    showError(e.message)
                } else {
                    toast(e.message ?: getString(R.string.cookbook_error_default))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (isInitial) {
                    showError(getString(R.string.cookbook_error_default))
                } else {
                    toast(R.string.cookbook_error_default)
                }
            } finally {
                if (generation == requestGeneration) {
                    loading = false
                    if (view != null) binding.rv.refreshComplete()
                    if (refreshPending) {
                        refreshPending = false
                        loadFirstPage()
                    }
                }
            }
        }
    }

    private fun loadMore() {
        if (!loggedIn || loading || !loaded || !hasMore) return
        loading = true
        val generation = requestGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listMine(status, page = page)
                if (!isCurrent(generation)) return@launch
                recipes.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                binding.rv.submitList(recipes.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.cookbook_error_default))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                toast(R.string.cookbook_error_default)
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return loggedIn && generation == requestGeneration && view != null
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        if (recipes.isEmpty()) {
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
        }
        binding.rv.visible()
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rv.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rv.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.tvEmpty.gone()
        binding.rv.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.cookbook_error_default)
        binding.llError.visible()
    }

    private fun showItemMenu(anchor: View, item: RecipeDto) {
        if (view == null) return
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_EDIT, 0, getString(R.string.cookbook_menu_edit))
        popup.menu.add(0, MENU_DELETE, 1, getString(R.string.cookbook_menu_delete))
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_EDIT -> openEdit(item)
                MENU_DELETE -> confirmDelete(item)
            }
            true
        }
        popup.show()
    }

    private fun openEdit(item: RecipeDto) {
        if (item.id <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_COOK_BOOK_ADD)
                .args(bundleOf(KEY_BUNDLE_RECIPE_ID to item.id))
                .go()
        }.onFailure {
            toast(R.string.cookbook_error_default)
        }
    }

    private fun confirmDelete(item: RecipeDto) {
        if (deletingId != 0L) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.cookbook_delete_title)
            .setMessage(getString(R.string.cookbook_delete_message, item.name))
            .setNegativeButton(R.string.cookbook_delete_cancel, null)
            .setPositiveButton(R.string.cookbook_delete_confirm) { _, _ ->
                deleteRecipe(item)
            }
            .show()
    }

    private fun deleteRecipe(item: RecipeDto) {
        if (deletingId != 0L || !loggedIn) return
        deletingId = item.id
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.delete(item.id)
                toast(R.string.cookbook_delete_success)
                reloadAfterMutation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.cookbook_error_default))
                    if (e.code == "RECIPE_NOT_FOUND") {
                        // 其他端已删除，刷新以恢复真实列表。
                        reloadAfterMutation()
                    }
                }
            } catch (e: Exception) {
                toast(R.string.cookbook_error_default)
            } finally {
                deletingId = 0L
            }
        }
    }

    /** 删除等写操作完成后刷新；若当前有请求在途，待其结束后再拉第一页。 */
    private fun reloadAfterMutation() {
        if (loading) {
            refreshPending = true
        } else {
            loadFirstPage()
        }
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.cookbook_login_failed)
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
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
