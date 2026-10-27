package com.rhys.cookbook.collections

import android.widget.Toast
import androidx.core.os.bundleOf
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
import com.android.common.ext.load
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_MY_COLLECTIONS
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.example.network.repository.RecipeRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.cookbook.R
import com.rhys.cookbook.databinding.CookbookFragmentCollectionsBinding
import com.rhys.cookbook.databinding.CookbookLayoutChildBinding
import com.rhys.cookbook.form.RecipeFormOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * “我的收藏”列表：展示后端返回的当前账号收藏菜谱。
 *
 * 收藏内容需要登录；可见性、收藏范围与排序都由后端判定，客户端只读取展示。
 * 卡片正文点击进入菜谱详情，不提供取消收藏或批量操作。
 */
@Route(PAGE_MY_COLLECTIONS)
class CollectedRecipesFragment :
    BaseBindFragment<CookbookFragmentCollectionsBinding>(CookbookFragmentCollectionsBinding::inflate) {

    private val repository = RecipeRepository()
    private val recipes = mutableListOf<RecipeDto>()

    private var loggedIn = false
    private var currentUserId = 0L
    private var loading = false
    private var loaded = false
    private var page = 1
    private var hasMore = true
    /** 登录态/账号切换时递增，丢弃旧账号仍在途的响应，避免把上一账号数据渲染出来。 */
    private var requestGeneration = 0

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: CookbookFragmentCollectionsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        initRecyclerView()
    }

    override fun initData(binding: CookbookFragmentCollectionsBinding) {
        binding.tvEmpty.text = getString(R.string.cookbook_collections_empty)
    }

    override fun initListener(binding: CookbookFragmentCollectionsBinding) {
        binding.btnLogin.setOnClickListener { openLogin() }
        binding.btnRetry.setOnClickListener {
            if (loggedIn) loadFirstPage() else openLogin()
        }
    }

    override fun initObserver(binding: CookbookFragmentCollectionsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从详情页返回时重新拉取第一页，收藏状态与内容保持同步。
        if (loggedIn && loaded && !loading) loadFirstPage()
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                currentUserId = 0L
                loading = false
                loaded = false
                page = 1
                hasMore = true
                requestGeneration++
                recipes.clear()
                binding.rv.submitList(emptyList<RecipeDto>())
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                loggedIn = true
                val userIdChanged = currentUserId != 0L && currentUserId != state.user.id
                currentUserId = state.user.id
                if (userIdChanged) {
                    // 账号切换：清空上一账号的收藏并丢弃在途响应。
                    requestGeneration++
                    loading = false
                    loaded = false
                    page = 1
                    hasMore = true
                    recipes.clear()
                    binding.rv.submitList(emptyList<RecipeDto>())
                    showLoading()
                    loadFirstPage()
                } else if (!loaded && !loading) {
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

                itemBinding.tvRecipeMeta.text = visibilityLabel(item)

                val category = item.category.orEmpty().trim()
                itemBinding.tvRecipeCategory.text = category
                if (category.isEmpty()) itemBinding.tvRecipeCategory.gone() else itemBinding.tvRecipeCategory.visible()

                val authorName = item.author?.displayName?.trim().orEmpty()
                if (authorName.isEmpty()) {
                    itemBinding.tvRecipeAuthor.text = ""
                    itemBinding.tvRecipeAuthor.gone()
                } else {
                    itemBinding.tvRecipeAuthor.text = getString(R.string.cookbook_author_format, authorName)
                    itemBinding.tvRecipeAuthor.visible()
                }

                // 本页没有编辑/删除语义，隐藏“更多”菜单入口。
                itemBinding.tvMore.setOnClickListener(null)
                itemBinding.tvMore.gone()
            }
            setOnItemClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemClickListener
                openDetail(item)
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

    private fun visibilityLabel(item: RecipeDto): String {
        return if (RecipeFormOptions.visibilityOf(item.visibility) == RecipeFormOptions.VISIBILITY_PUBLIC) {
            getString(R.string.cookbook_visibility_public)
        } else {
            getString(R.string.cookbook_visibility_friends)
        }
    }

    private fun loadFirstPage() {
        if (!loggedIn || loading) {
            binding.rv.refreshComplete()
            return
        }
        loading = true
        val generation = ++requestGeneration
        val isInitial = !loaded
        if (isInitial) showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listCollections(page = 1)
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
                    // 刷新失败时保留已展示的列表，只提示真实错误。
                    toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.cookbook_collections_error_default)
                    )
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (isInitial) {
                    showError(getString(R.string.cookbook_collections_error_default))
                } else {
                    toast(R.string.cookbook_collections_error_default)
                }
            } finally {
                if (generation == requestGeneration) {
                    loading = false
                    if (view != null) binding.rv.refreshComplete()
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
                val result = repository.listCollections(page = page)
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
                    toast(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.cookbook_collections_error_default)
                    )
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                toast(R.string.cookbook_collections_error_default)
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
            ?: getString(R.string.cookbook_collections_error_default)
        binding.llError.visible()
    }

    private fun openDetail(item: RecipeDto) {
        if (item.id <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_COOK_BOOK_DETAIL)
                .args(bundleOf(KEY_BUNDLE_RECIPE_ID to item.id))
                .go()
        }.onFailure {
            toast(R.string.cookbook_error_default)
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
}
