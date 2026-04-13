package com.android.common.ext

import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue


object DensityUtil {
    private var displayMetrics: DisplayMetrics? = null

    @JvmStatic
    fun getDisplayMetrics(): DisplayMetrics {
        if (displayMetrics == null) {
            displayMetrics = Resources.getSystem().displayMetrics
        }
        return displayMetrics!!
    }
}

fun Int.dp(): Float {
    // 获取当前手机的像素密度（1个dp对应几个px）
    return if (this == 0)
        0f
    else
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), DensityUtil.getDisplayMetrics())
}


fun Int.sp(): Float {
    // 获取当前手机的像素密度（1个sp对应几个px）
    return if (this == 0) 0f
    else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), DensityUtil.getDisplayMetrics())
}