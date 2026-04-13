package com.rhys.order


import com.android.common.base.BaseBindFragment
import com.android.common.utils.LogUtils
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_ORDER
import com.marky.route.annotation.Route
import com.rhys.order.databinding.OrderFragmentOrderBinding

@Route(PAGE_ORDER)
class OrderFragment : BaseBindFragment<OrderFragmentOrderBinding>(OrderFragmentOrderBinding::inflate) {


    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: OrderFragmentOrderBinding) {
        LogUtils.d("OrderFragment 初始化成功")
    }

    override fun initData(binding: OrderFragmentOrderBinding) {

    }
}