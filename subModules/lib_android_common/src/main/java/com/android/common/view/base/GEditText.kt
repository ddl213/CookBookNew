package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 支持渐变背景、边框、圆角的 EditText。
 *
 * 继承 AppCompatEditText，保留 EditText 原有输入能力。
 * 实现 GStyleable，通过 GStyler 增加通用背景样式能力。
 */
class GEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr), GStyleable {

    override val gStyler: GStyler = GStyler(this, this.context, attrs)

    init {
        // 一般 EditText 默认就是可聚焦的，这里保留你原来的处理
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }
}