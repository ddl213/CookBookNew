package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * GLinearLayout (Gradient LinearLayout) 通过持有 GStyler 实例来获得渐变背景、边框和圆角功能。
 * 自身类中不再包含任何公共样式方法的声明或实现。
 */
class GLinearLayout : LinearLayout {

    private val styler: GStyler

    constructor(context: Context) : this(context, null)
    constructor(context: Context,  attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context,  attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        styler = GStyler(this, context, attrs)
    }

    fun getStyler(): GStyler {
        return styler
    }
}