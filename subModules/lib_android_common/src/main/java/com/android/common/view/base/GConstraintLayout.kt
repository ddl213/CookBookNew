package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * 支持渐变背景、边框、圆角的 ConstraintLayout。
 *
 * 它本身仍然继承 ConstraintLayout，
 * 所以 ConstraintLayout 原有的所有功能都还在。
 *
 * 同时实现 GStyleable，
 * 通过 GStyler 增加通用样式能力。
 */
class GConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), GStyleable {

    /**
     * 通用样式处理器。
     *
     * 负责解析 XML 中的 CommonGradientAttrs，
     * 并把 BaseDrawable 设置为当前 View 的 background。
     */
    override val gStyler: GStyler = GStyler(this, context, attrs)
}