package com.rhys.main.home

import com.android.common.base.BaseBindFragment
import com.android.common.base.CommonFragmentAdapter
import com.android.common.bean.TabInfo
import com.android.common.ext.attach
import com.android.common.ext.setNavigator
import com.android.common.utils.LogUtils
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_CHAT
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK
import com.campaign.common.constants.RoutePath.PAGE_HOME
import com.campaign.common.constants.RoutePath.PAGE_INDEX
import com.campaign.common.constants.RoutePath.PAGE_MINE
import com.campaign.common.constants.RoutePath.PAGE_ORDER
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.main.R
import com.rhys.main.databinding.FragmentHomeBinding

@Route(path = PAGE_HOME, startPage = true)
class HomeFragment : BaseBindFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    /**
     * 配置tab图标
     */
    private val mTabIcon = listOf(
        R.drawable.selector_navigator_item_home,
        R.drawable.selector_navigator_item_recipes,
        R.drawable.selector_navigator_item_chat,
        R.drawable.selector_navigator_item_order,
        R.drawable.selector_navigator_item_mine,
    )

    /**
     * 配置viewpager2 tab信息
     */
    private val mTab = listOf(
        TabInfo("首页", "index", NRoute.getFragment(PAGE_INDEX)!!),
        TabInfo("菜谱", "cookbook", NRoute.getFragment(PAGE_COOK_BOOK)!!),
        TabInfo("消息", "chat", NRoute.getFragment(PAGE_CHAT)!!),
        TabInfo("订单", "order", NRoute.getFragment(PAGE_ORDER)!!),
        TabInfo("我的", "mine", NRoute.getFragment(PAGE_MINE)!!)
    )

    override fun initTitleBar(): TitleBar? {
        return null
    }

    override fun initView(binding: FragmentHomeBinding) {

        LogUtils.d("HomeFragment 初始化成功")

        binding.vp.isUserInputEnabled = false
        binding.vp.offscreenPageLimit = mTab.size

        binding.indicator.setNavigator(requireContext(), mTab){tabInfo, p1 ->
            BottomBarItem(
                context = requireContext(),
                title = tabInfo.name,
                resId = mTabIcon[p1]
            ).apply {
                setOnClickListener {
                    binding.vp.setCurrentItem(p1, false)

                }
            }
        }
        binding.vp.adapter = CommonFragmentAdapter(mTab, this)
        binding.vp.attach(binding.indicator)
    }

    override fun initData(binding: FragmentHomeBinding) {

    }
}