package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * GLinearLayout (Gradient LinearLayout) 通过持有 GStyler 实例来获得渐变背景、边框和圆角功能。
 * 自身类中不再包含任何公共样式方法的声明或实现。
 */
class GLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),GStyleable {
    override val gStyler: GStyler = GStyler(this,context,attrs)
}