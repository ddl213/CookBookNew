package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * 支持渐变背景、边框、圆角的普通 View。
 *
 * 继承 View，保留 View 原有能力。
 * 实现 GStyleable，通过 GStyler 增加通用背景样式能力。
 */
class GView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GStyleable {

    /**
     * 通用样式处理器。
     *
     * 负责解析 XML 中的通用样式属性，并设置背景 Drawable。
     */
    override val gStyler: GStyler = GStyler(this, context, attrs)
}