package com.rhys.main.home

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.rhys.main.databinding.LayoutBottomBarItemBinding
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerTitleView

/**
 * @Description: 首页底部bottombar item view
 * @Author: marky
 * @CreateDate: 2024/5/23 11:52
 * @Version: 1.0
 */
class BottomBarItem @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    def: Int = 0,
    title: String = "",
    resId: Int = -1
) : FrameLayout(context, attributeSet, def), IPagerTitleView {
    private val mBinding =
        LayoutBottomBarItemBinding.inflate(LayoutInflater.from(context), this, true)


    init {
        mBinding.iv.setImageResource(resId)
        mBinding.tv.text = title
    }

    override fun onSelected(p0: Int, p1: Int) {
        mBinding.iv.isSelected = true
        mBinding.tv.isSelected = true
    }

    override fun onDeselected(p0: Int, p1: Int) {
        mBinding.iv.isSelected = false
        mBinding.tv.isSelected = false
    }

    override fun onLeave(p0: Int, p1: Int, p2: Float, p3: Boolean) {

    }

    override fun onEnter(p0: Int, p1: Int, p2: Float, p3: Boolean) {

    }


}