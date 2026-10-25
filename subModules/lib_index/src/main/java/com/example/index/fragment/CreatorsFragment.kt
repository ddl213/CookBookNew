package com.example.index.fragment

import android.view.View
import androidx.lifecycle.lifecycleScope
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
import com.campaign.common.constants.RoutePath.PAGE_CREATORS_PUBLIC
import com.example.index.CreatorAggregator
import com.example.index.R
import com.example.index.bindIndexAvatar
import com.example.index.bindIndexRecipeCard
import com.example.index.databinding.IndexFragmentCreatorsBinding
import com.example.index.databinding.IndexItemCreatorBinding
import com.example.index.databinding.IndexItemRecipeBinding
import com.example.index.openIndexRecipeDetail
import com.example.index.openIndexUserProfile
import com.example.index.toastIndex
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserSummaryDto
import com.example.network.repository.RecipeRepository
import com.marky.route.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 美食创作者与公开菜谱：两个独立标签页，匿名可浏览。
 *
 * “推荐大师”只是公开菜谱作者按首次出现顺序去重，不包含评分、认证或个性化推荐；
 * “公开菜谱”直接使用公开菜谱接口，不混入好友动态。两个标签各自维护分页和请求状态，
 * 切换或销毁时通过 generation 丢弃迟到响应，失败只展示重试，不做登录引导。
 */
@Route(PAGE_CREATORS_PUBLIC)
class CreatorsFragment :
    BaseBindFragment<IndexFragmentCreatorsBinding>(IndexFragmentCreatorsBinding::inflate) {

    private enum class Tab { MASTERS, PUBLIC }

    private class TabState {
        var page = 1
        var hasMore = true
        var loaded = false
        var loading = false
        var failed = false
        var generation = 0
    }

    private class CreatorsPage(
        val creators: List<UserSummaryDto>,
        val nextPage: Int,
        val hasMore: Boolean
    )

    private val repository = RecipeRepository()

    private val creators = mutableListOf<UserSummaryDto>()
    private val creatorIds = mutableSetOf<Long>()
    private val publicRecipes = mutableListOf<RecipeDto>()

    private val mastersState = TabState()
    private val publicState = TabState()

    private var selectedTab = Tab.MASTERS

    private val currentState: TabState
        get() = stateOf(selectedTab)

    override fun initTitleBar(): View? = binding.titleBar

    override fun initView(binding: IndexFragmentCreatorsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        bindCreatorList(binding)
        bindPublicList(binding)
        updateTabUi()
        // View 重建（如从详情返回）时当前标签重新拉取首页，保证内容与可见性最新。
        loadFirstPage(selectedTab)
    }

    override fun initData(binding: IndexFragmentCreatorsBinding) {
    }

    override fun initListener(binding: IndexFragmentCreatorsBinding) {
        binding.llTabMasters.setOnClickListener { switchTab(Tab.MASTERS) }
        binding.llTabPublic.setOnClickListener { switchTab(Tab.PUBLIC) }
        binding.btnRetry.setOnClickListener { loadFirstPage(selectedTab) }
    }

    override fun onViewDestroy() {
        resetState(mastersState)
        resetState(publicState)
        creators.clear()
        creatorIds.clear()
        publicRecipes.clear()
    }

    private fun bindCreatorList(binding: IndexFragmentCreatorsBinding) {
        binding.rvCreators.liner().buildAdapter<UserSummaryDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemCreatorBinding::class.java)
            bind<IndexItemCreatorBinding> { holder, _, item, _ ->
                holder.binding.tvNickname.text = item.displayName
                bindIndexAvatar(
                    holder.binding.ivAvatar,
                    holder.binding.tvAvatarFallback,
                    item.displayName,
                    item.avatar
                )
            }
            setOnItemClickListener { _, _, position ->
                val item = creators.getOrNull(position) ?: return@setOnItemClickListener
                openIndexUserProfile(this@CreatorsFragment, item.id)
            }
        }
        binding.rvCreators.addItemDecoration(RvDecoration.Builder().setVertical(10).build())
        binding.rvCreators.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (selectedTab != Tab.MASTERS || dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore(Tab.MASTERS)
            }
        })
    }

    private fun bindPublicList(binding: IndexFragmentCreatorsBinding) {
        binding.rvPublic.liner().buildAdapter<RecipeDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(IndexItemRecipeBinding::class.java)
            bind<IndexItemRecipeBinding> { holder, _, item, _ ->
                bindIndexRecipeCard(holder.binding, item, this@CreatorsFragment)
            }
            setOnItemClickListener { _, _, position ->
                val item = publicRecipes.getOrNull(position) ?: return@setOnItemClickListener
                openIndexRecipeDetail(this@CreatorsFragment, item.id)
            }
        }
        binding.rvPublic.addItemDecoration(RvDecoration.Builder().setVertical(10).build())
        binding.rvPublic.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (selectedTab != Tab.PUBLIC || dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore(Tab.PUBLIC)
            }
        })
    }

    private fun switchTab(tab: Tab) {
        if (view == null || tab == selectedTab) return
        // 切走标签的迟到响应不再应用到列表；已加载内容保留，切回时无需重复拉取第一页。
        val previousState = stateOf(selectedTab)
        previousState.generation++
        previousState.loading = false
        selectedTab = tab
        updateTabUi()
        renderTabState()
        ensureFirstPage(tab)
    }

    private fun updateTabUi() {
        if (view == null) return
        val masters = selectedTab == Tab.MASTERS
        binding.tvTabMasters.isSelected = masters
        binding.tvTabPublic.isSelected = !masters
        binding.vTabMastersIndicator.visibility = if (masters) View.VISIBLE else View.INVISIBLE
        binding.vTabPublicIndicator.visibility = if (masters) View.INVISIBLE else View.VISIBLE
    }

    private fun ensureFirstPage(tab: Tab) {
        val state = stateOf(tab)
        if (state.loading) return
        // 已成功加载过的标签保留内容；只有未加载或首次加载失败时才重新请求第一页。
        if (state.loaded && !state.failed) return
        loadFirstPage(tab)
    }

    private fun loadFirstPage(tab: Tab) {
        if (view == null) return
        val state = stateOf(tab)
        if (state.loading) return
        val generation = ++state.generation
        state.loading = true
        state.loaded = false
        state.failed = false
        state.page = 1
        state.hasMore = true
        if (tab == selectedTab) showLoading()
        fetch(tab, state, generation, reset = true)
    }

    private fun loadMore(tab: Tab) {
        if (view == null || tab != selectedTab) return
        val state = stateOf(tab)
        if (state.loading || !state.loaded || !state.hasMore || state.failed) return
        state.loading = true
        fetch(tab, state, state.generation, reset = false)
    }

    private fun fetch(tab: Tab, state: TabState, generation: Int, reset: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (tab) {
                    Tab.MASTERS -> {
                        val page = fetchCreatorsPage(tab, state, generation, reset) ?: return@launch
                        if (!isCurrent(tab, state, generation)) return@launch
                        applyCreatorsPage(page, reset)
                        binding.rvCreators.submitList(creators.toList())
                    }

                    Tab.PUBLIC -> {
                        val pageToLoad = if (reset) 1 else state.page
                        val result = repository.listPublic(page = pageToLoad)
                        if (!isCurrent(tab, state, generation)) return@launch
                        if (reset) publicRecipes.clear()
                        publicRecipes.addAll(result.items)
                        state.page = result.page + 1
                        state.hasMore = result.hasMore
                        binding.rvPublic.submitList(publicRecipes.toList())
                    }
                }
                state.loaded = true
                state.failed = false
                if (isCurrent(tab, state, generation)) renderTabState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(tab, state, generation)) return@launch
                if (state.loaded) {
                    // 已有内容时加载下一页失败，保留列表并提示，可再次滚动重试。
                    toastIndex(this@CreatorsFragment, R.string.index_error_default)
                    renderTabState()
                } else {
                    state.failed = true
                    renderTabState()
                }
            } finally {
                if (generation == state.generation) state.loading = false
            }
        }
    }

    /**
     * 逐页拉取公开菜谱并聚合新增作者。
     * 当前页没有新作者且后端仍有下一页时继续请求，最多 [MAX_EMPTY_PAGES] 页，避免无限循环。
     */
    private suspend fun fetchCreatorsPage(
        tab: Tab,
        state: TabState,
        generation: Int,
        reset: Boolean
    ): CreatorsPage? {
        val knownIds = if (reset) emptySet() else creatorIds.toSet()
        val collected = mutableListOf<UserSummaryDto>()
        val collectedIds = mutableSetOf<Long>()
        var pageToLoad = if (reset) 1 else state.page
        var hasMore = true
        var attempts = 0

        while (attempts < MAX_EMPTY_PAGES) {
            attempts++
            val result = repository.listPublic(
                page = pageToLoad,
                pageSize = MASTER_PAGE_SIZE
            )
            if (!isCurrent(tab, state, generation)) return null
            hasMore = result.hasMore
            CreatorAggregator.collect(result.items).forEach { creator ->
                if (creator.id in knownIds) return@forEach
                if (collectedIds.add(creator.id)) collected.add(creator)
            }
            pageToLoad = result.page + 1
            if (collected.isNotEmpty() || !hasMore) break
        }
        return CreatorsPage(collected, pageToLoad, hasMore)
    }

    private fun applyCreatorsPage(page: CreatorsPage, reset: Boolean) {
        if (reset) {
            creators.clear()
            creatorIds.clear()
        }
        creators.addAll(page.creators)
        page.creators.forEach { creatorIds.add(it.id) }
        mastersState.page = page.nextPage
        mastersState.hasMore = page.hasMore
    }

    private fun renderTabState() {
        if (view == null) return
        if (selectedTab == Tab.MASTERS) {
            binding.rvCreators.visible()
            binding.rvPublic.gone()
        } else {
            binding.rvPublic.visible()
            binding.rvCreators.gone()
        }
        val state = currentState
        when {
            state.failed -> showError()
            state.loading && !state.loaded -> showLoading()
            state.loaded -> showLoaded()
            else -> showLoading()
        }
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llError.gone()
        binding.tvEmpty.gone()
    }

    private fun showLoaded() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llError.gone()
        val empty = if (selectedTab == Tab.MASTERS) creators.isEmpty() else publicRecipes.isEmpty()
        if (empty) {
            binding.tvEmpty.text = getString(
                if (selectedTab == Tab.MASTERS) {
                    R.string.index_creators_empty
                } else {
                    R.string.index_creators_public_empty
                }
            )
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
        }
    }

    private fun showError() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.tvEmpty.gone()
        binding.tvError.text = getString(R.string.index_error_default)
        binding.llError.visible()
    }

    private fun stateOf(tab: Tab): TabState =
        if (tab == Tab.MASTERS) mastersState else publicState

    private fun isCurrent(tab: Tab, state: TabState, generation: Int): Boolean {
        return view != null && tab == selectedTab && generation == state.generation
    }

    private fun resetState(state: TabState) {
        state.generation++
        state.page = 1
        state.hasMore = true
        state.loaded = false
        state.loading = false
        state.failed = false
    }

    private companion object {
        /** 推荐大师每次聚合使用的公开菜谱页大小（后端上限 50）。 */
        const val MASTER_PAGE_SIZE = 50

        /** 一次加载中连续无新增作者时最多拉取的页数。 */
        const val MAX_EMPTY_PAGES = 5
    }
}
