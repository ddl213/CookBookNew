package com.rhys.cookbook

import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.MarginPageTransformer
import com.android.common.base.BaseBindFragment
import com.android.common.base.CommonFragmentAdapter
import com.android.common.bean.KEY_BUNDLE_PAGE_ID
import com.android.common.bean.TabInfo
import com.android.common.ext.attach
import com.android.common.ext.dp
import com.android.common.ext.setNavigator
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_ADD
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_CHILD
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.cookbook.databinding.CookbookFragmentCookBookBinding
import com.rhys.cookbook.view.CookBookTitleView

/**
 * 记录菜谱的主界面
 * 作为展示已发布和草稿的界面的容器
 * 可以跳转到添加菜谱的页面
 */
@Route(PAGE_COOK_BOOK)
class CookBookFragment : BaseBindFragment<CookbookFragmentCookBookBinding>(CookbookFragmentCookBookBinding::inflate) {
    private val tabList = listOf(
        TabInfo("已发布","publish",
            NRoute.getFragment(PAGE_COOK_BOOK_CHILD) ?: "",
            bundle = bundleOf(
                KEY_BUNDLE_PAGE_ID to "publish"
            )
        ),
        TabInfo("草稿","draft",
            NRoute.getFragment(PAGE_COOK_BOOK_CHILD) ?: "",
            bundle = bundleOf(
                KEY_BUNDLE_PAGE_ID to "draft"
            )
        )
    )

    override fun initTitleBar(): View {
        return binding.vTitleBar
    }

    override fun initView(binding: CookbookFragmentCookBookBinding) {
        LogUtils.d("CookBookFragment 初始化成功")
        initVp()

    }

    //初始化导航栏
    private fun initVp(){
        binding.indicator.setNavigator(requireContext(),tabList){info ,index ->
            CookBookTitleView(requireContext(), title = info.name).apply {
                setOnClickListener {
                    binding.vp.setCurrentItem(index,true)
                }
            }
        }

        binding.vp.adapter = CommonFragmentAdapter(tabList,this)
        binding.vp.attach(binding.indicator)
        binding.vp.setPageTransformer(MarginPageTransformer(8.dp().toInt()))
    }

    override fun initData(binding: CookbookFragmentCookBookBinding) {

    }

    override fun initListener(binding: CookbookFragmentCookBookBinding) {
        super.initListener(binding)
        binding.ivAdd.setOnClickListener {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_COOK_BOOK_ADD)
                .go()
        }
    }
}