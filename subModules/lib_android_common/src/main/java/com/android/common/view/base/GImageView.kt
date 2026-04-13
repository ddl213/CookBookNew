package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * 通过持有 GStyler 实例获得渐变背景、边框和圆角功能。
 * 自身类中不再包含任何公共样式方法的声明或实现。
 */
class GImageView : AppCompatImageView {

    // 持有 GStyler 实例，以便在运行时需要动态修改样式时使用。
    // 如果你只通过 XML 设置样式，且不需要动态修改，这个字段甚至可以省略。
    private val styler: GStyler

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        // 将自身和 AttributeSet 传递给 GStyler 进行处理
        styler = GStyler(this, context, attrs)
    }

    fun getStyler(): GStyler {
        return styler
    }
}