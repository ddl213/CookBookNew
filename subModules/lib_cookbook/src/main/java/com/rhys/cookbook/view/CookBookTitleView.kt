package com.rhys.cookbook.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.android.common.ext.gone
import com.android.common.ext.visible
import com.rhys.cookbook.databinding.CookbookLayoutTitleViewBinding
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerTitleView

/**
 * 菜谱模块的viewpager2tab栏标签view
 * 显示左对齐的标签
 */
class CookBookTitleView@JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    def: Int = 0,
    title: String = ""
) : FrameLayout(context, attributeSet, def), IPagerTitleView {
    private val mBinding =
        CookbookLayoutTitleViewBinding.inflate(LayoutInflater.from(context), this, true)


    init {
        mBinding.tvTitle.text = title
    }

    override fun onSelected(p0: Int, p1: Int) {
        mBinding.tvTitle.isSelected = true
        mBinding.tvTitle.typeface = Typeface.DEFAULT_BOLD
        mBinding.vBottomLine.visible()
    }

    override fun onDeselected(p0: Int, p1: Int) {
        mBinding.tvTitle.isSelected = false
        mBinding.tvTitle.typeface = Typeface.DEFAULT
        mBinding.vBottomLine.gone()
    }

    override fun onLeave(p0: Int, p1: Int, p2: Float, p3: Boolean) {

    }

    override fun onEnter(p0: Int, p1: Int, p2: Float, p3: Boolean) {

    }
}