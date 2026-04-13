package com.rhys.mine

import com.android.common.base.BaseBindFragment
import com.android.common.utils.LogUtils
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_MINE
import com.marky.route.annotation.Route
import com.rhys.mine.databinding.MineFragmentMineBinding

@Route(PAGE_MINE)
class MineFragment : BaseBindFragment<MineFragmentMineBinding>(MineFragmentMineBinding::inflate) {


    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: MineFragmentMineBinding) {
        LogUtils.d("MineFragment 初始化成功")
    }

    override fun initData(binding: MineFragmentMineBinding) {

    }
}