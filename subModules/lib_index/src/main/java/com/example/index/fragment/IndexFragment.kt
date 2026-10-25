package com.example.index.fragment

import android.view.View
import androidx.annotation.StringRes
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
import com.campaign.common.constants.RoutePath.PAGE_INDEX
import com.campaign.common.constants.RoutePath.PAGE_INDEX_SEARCH
import com.example.index.bindIndexRecipeCard
import com.example.index.openIndexLogin
import com.example.index.openIndexRecipeDetail
import com.example.index.toastIndex
import com.example.index.R
import com.example.index.databinding.IndexFragmentIndexBinding
import com.example.index.databinding.IndexItemRecipeBinding
import com.example.index.databinding.IndexItemTagBinding
import com.example.network.AuthException
import com.example.network.bean.RecipeDto
import com.example.network.repository.RecipeRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 首页：公开菜谱与好友动态。
 *
 * “推荐/热门”明确回退为公开菜谱的最新排序，不实现也未宣称推荐算法。
 * 好友动态未登录时不请求 /feed，只展示登录引导；公开内容匿名可浏览。
 */
@Route(PAGE_INDEX)
class IndexFragment : BaseBindFragment<IndexFragmentIndexBinding>(IndexFragmentIndexBinding::inflate) {

    private enum class TagKind { PUBLIC, FEED }

    private data class IndexTag(
        @StringRes val labelRes: Int,
        val kind: TagKind,
        val category: String?,
        val selected: Boolean = false
    )

    private val repository = RecipeRepository()
    private val recipes = mutableListOf<RecipeDto>()

    private var selectedTagIndex = 0
    private var loggedIn = false
    private var currentUserId = 0L
    private var loading = false
    private var loaded = false
    private var page = 1
    private var hasMore = true
    /** 标签切换、登录态/账号变化时递增，丢弃旧请求的迟到响应。 */
    private var requestGeneration = 0

    private val currentTag: IndexTag
        get() = tagItems().getOrElse(selectedTagIndex) { tagItems().first() }

    override fun initView(binding: IndexFragmentIndexBinding) {
        bindTags(binding)
        bindFeed(binding)
    }

    override fun initData(binding: IndexFragmentIndexBinding) {
    }

    override fun initListener(binding: IndexFragmentIndexBinding) {
        binding.tvSearch.setOnClickListener { openSearch() }
        binding.ivSearch.setOnClickListener { openSearch() }
        binding.btnRetry.setOnClickListener { loadFirstPage() }
        binding.btnLogin.setOnClickListener { openIndexLogin(this) }
        binding.appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val total = appBarLayout.totalScrollRange
            if (total <= 0) {
                // 无折叠范围说明 AppBar 未折叠，顶部搜索框仍可见，右侧按钮保持隐藏。
                binding.ivSearch.gone()
                binding.ivSearch.alpha = 0f
                return@addOnOffsetChangedListener
            }
            val fraction = abs(verticalOffset).toFloat() / total
            binding.ivSearch.alpha = fraction
            if (fraction > 0.5f) {
                binding.ivSearch.visible()
            } else if (fraction < 0.2f) {
                binding.ivSearch.gone()
            }
        }
    }

    override fun initObserver(binding: IndexFragmentIndexBinding) {
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
    }

    override fun initTitleBar(): View? = null

    private fun bindTags(binding: IndexFragmentIndexBinding) {
        binding.rvTags.liner(RecyclerView.HORIZONTAL).buildAdapter<IndexTag> {
            setDiff(
                areItemsSame = { old, new -> old.labelRes == new.labelRes },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemTagBinding::class.java)
            bind<IndexItemTagBinding> { holder, _, item, _ ->
                holder.binding.tvTag.setText(item.labelRes)
                holder.binding.tvTag.isSelected = item.selected
            }
            setOnItemClickListener { _, _, position -> selectTag(position) }
            setList(tagItems())
        }
        binding.rvTags.addItemDecoration(RvDecoration.Builder().setHorizontal(8).build())
    }

    private fun bindFeed(binding: IndexFragmentIndexBinding) {
        binding.rvFeed.liner().buildAdapter<RecipeDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemRecipeBinding::class.java)
            bind<IndexItemRecipeBinding> { holder, _, item, _ ->
                bindIndexRecipeCard(holder.binding, item, this@IndexFragment)
            }
            setOnItemClickListener { _, _, position ->
                val item = recipes.getOrNull(position) ?: return@setOnItemClickListener
                openIndexRecipeDetail(this@IndexFragment, item.id)
            }
        }
        binding.rvFeed.addItemDecoration(RvDecoration.Builder().setVertical(10).build())
        binding.rvFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore()
            }
        })
    }

    private fun tagItems(): List<IndexTag> {
        return listOf(
            IndexTag(R.string.index_tab_recommend, TagKind.PUBLIC, null, selectedTagIndex == 0),
            IndexTag(R.string.index_tab_feed, TagKind.FEED, null, selectedTagIndex == 1),
            IndexTag(R.string.index_tab_popular, TagKind.PUBLIC, null, selectedTagIndex == 2),
            IndexTag(R.string.index_tab_home_cooking, TagKind.PUBLIC, CATEGORY_HOME_COOKING, selectedTagIndex == 3),
            IndexTag(R.string.index_tab_breakfast, TagKind.PUBLIC, CATEGORY_BREAKFAST, selectedTagIndex == 4),
            IndexTag(R.string.index_tab_dessert, TagKind.PUBLIC, CATEGORY_DESSERT, selectedTagIndex == 5)
        )
    }

    private fun selectTag(position: Int) {
        if (view == null || position == selectedTagIndex) return
        if (position !in 0 until tagItems().size) return
        selectedTagIndex = position
        binding.rvTags.submitList(tagItems())
        clearContent()
        if (currentTag.kind == TagKind.FEED && !loggedIn) {
            showLoginRequired()
        } else {
            loadFirstPage()
        }
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
                if (wasLoggedIn) clearContent()
                if (currentTag.kind == TagKind.FEED) {
                    clearContent()
                    showLoginRequired()
                } else if (!loaded) {
                    // 公开内容匿名可读；退出/首次进入时重新拉取，返回页面时不重复加载。
                    loadFirstPage()
                }
            }

            is AuthState.LoggedIn -> {
                val accountChanged = currentUserId != 0L && currentUserId != state.user.id
                loggedIn = true
                currentUserId = state.user.id
                if (accountChanged) {
                    requestGeneration++
                    loading = false
                    clearContent()
                }
                if (!loaded || accountChanged) loadFirstPage()
            }
        }
    }

    private fun loadFirstPage() {
        if (view == null || !canRequest()) return
        val generation = ++requestGeneration
        loading = true
        loaded = false
        page = 1
        hasMore = true
        showLoading()
        fetch(generation, pageToLoad = 1, reset = true)
    }

    private fun loadMore() {
        if (view == null || loading || !loaded || !hasMore) return
        if (!canRequest()) return
        val generation = requestGeneration
        loading = true
        fetch(generation, pageToLoad = page, reset = false)
    }

    private fun canRequest(): Boolean {
        return currentTag.kind == TagKind.PUBLIC || loggedIn
    }

    private fun fetch(generation: Int, pageToLoad: Int, reset: Boolean) {
        val tag = currentTag
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = if (tag.kind == TagKind.FEED) {
                    repository.listFeed(page = pageToLoad)
                } else {
                    repository.listPublic(category = tag.category, page = pageToLoad)
                }
                if (!isCurrent(generation)) return@launch
                if (reset) recipes.clear()
                recipes.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                binding.rvFeed.submitList(recipes.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    // 状态流负责切换为公开/登录引导；不把网络失败误判成无权限。
                    SessionManager.logout()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toastIndex(this@IndexFragment, e.message ?: getString(R.string.index_error_default))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (reset) {
                    showError(getString(R.string.index_error_default))
                } else {
                    toastIndex(this@IndexFragment, R.string.index_error_default)
                }
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return view != null && generation == requestGeneration
    }

    private fun clearContent() {
        requestGeneration++
        loading = false
        loaded = false
        page = 1
        hasMore = true
        recipes.clear()
        binding.rvFeed.submitList(emptyList<RecipeDto>())
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        binding.llLoginRequired.gone()
        // 列表始终保留尺寸，状态视图以 rv_feed 为锚点才能稳定居中。
        binding.rvFeed.visible()
        if (recipes.isEmpty()) {
            binding.tvEmpty.text = if (currentTag.kind == TagKind.FEED) {
                getString(R.string.index_empty_feed)
            } else {
                getString(R.string.index_empty)
            }
            binding.llEmpty.visible()
        } else {
            binding.llEmpty.gone()
        }
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.gone()
        binding.rvFeed.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llEmpty.gone()
        binding.llLoginRequired.gone()
        binding.rvFeed.visible()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.index_error_default)
        binding.llError.visible()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llEmpty.gone()
        binding.llError.gone()
        binding.rvFeed.visible()
        binding.llLoginRequired.visible()
    }

    private fun openSearch() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_INDEX_SEARCH)
                .go()
        }.onFailure {
            toastIndex(this, R.string.index_open_failed)
        }
    }

    private companion object {
        const val CATEGORY_HOME_COOKING = "家常菜"
        const val CATEGORY_BREAKFAST = "早餐"
        const val CATEGORY_DESSERT = "甜品"
    }
}
