package com.android.common.view.base

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import android.graphics.Color // 确保引入 Color 类
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * `GStyler` 是一个辅助类，封装了 `CommonGradientAttrs` 定义的背景、边框和圆角样式
 * 的解析和应用逻辑。
 *
 * 它的设计目的是让 `GView` 系列组件 (如 `CView`, `GConstraintLayout`, `GLinearLayout`)
 * 能够复用这些通用的样式功能，从而避免代码重复。
 *
 * @property view `GStyler` 所作用的 `View` 实例。
 */
class GStyler(
    private val view: View,
    context: Context,
    attrs: AttributeSet?
) {

    /**
     * 内部持有的 `BaseDrawable` 实例，负责实际的绘制工作。
     * `VisibleForTesting` 注解表示此属性对测试代码可见，但不是公共 API 的一部分。
     */
    @VisibleForTesting
    val drawable: BaseDrawable = BaseDrawable()

    init {
        // 将宿主 View 的背景设置为 `BaseDrawable` 实例
        view.background = drawable
        // 将 `AttributeSet` 传递给 `BaseDrawable`，让它解析并应用 XML 中定义的样式属性
        drawable.applyAttributes(context, attrs)
    }

    /**
     * 动态设置背景 Drawable。
     *
     * @param drawable 要设置的 Drawable。
     */
    fun setBackgroundDrawable(drawable: Drawable?) {
        this.drawable.setBackgroundDrawable(drawable)
    }

    /**
     * 动态设置背景渐变颜色。支持两色或三色渐变。
     *
     * @param startColor 渐变起始颜色。
     * @param centerColor 渐变中心颜色 (可选，默认为透明，表示两色渐变)。
     * @param endColor 渐变结束颜色。
     */
    fun setBackgroundColors(@ColorInt startColor: Int, @ColorInt centerColor: Int = Color.TRANSPARENT, @ColorInt endColor: Int) {
        this.drawable.setBackgroundColors(startColor, centerColor, endColor)
    }

    /**
     * 动态设置纯色背景颜色。
     *
     * @param color 背景颜色。
     */
    fun setBackgroundColor(@ColorInt color: Int) {
        this.drawable.setBackgroundColor(color)
    }

    /**
     * 动态设置背景渐变角度。
     *
     * @param angle 渐变角度（0-360度）。
     */
    fun setBackgroundGradientAngle(angle: Float) {
        this.drawable.setBackgroundGradientAngle(angle)
    }

    /**
     * 动态设置边框宽度。
     *
     * @param width 边框宽度（像素）。
     */
    fun setBorderWidth(width: Float) {
        this.drawable.setBorderWidth(width)
    }

    /**
     * 动态设置边框渐变颜色。支持两色或三色渐变。
     *
     * @param startColor 渐变起始颜色。
     * @param centerColor 渐变中心颜色 (可选，默认为透明，表示两色渐变)。
     * @param endColor 渐变结束颜色。
     */
    fun setBorderColors(@ColorInt startColor: Int, @ColorInt centerColor: Int = Color.TRANSPARENT, @ColorInt endColor: Int) {
        this.drawable.setBorderColors(startColor, centerColor, endColor)
    }

    /**
     * 动态设置纯色边框颜色。
     *
     * @param color 边框颜色。
     */
    fun setBorderColor(@ColorInt color: Int) {
        this.drawable.setBorderColor(color)
    }

    /**
     * 动态设置边框渐变角度。
     *
     * @param angle 渐变角度（0-360度）。
     */
    fun setBorderGradientAngle(angle: Float) {
        this.drawable.setBorderGradientAngle(angle)
    }

    /**
     * 动态设置统一的圆角半径。
     *
     * @param radius 圆角半径（像素）。
     */
    fun setCornerRadius(radius: Float) {
        this.drawable.setCornerRadius(radius)
    }

    /**
     * 动态设置各个角落的圆角半径。
     *
     * @param topLeft 左上角圆角半径。
     * @param topRight 右上角圆角半径。
     * @param bottomRight 右下角圆角半径。
     * @param bottomLeft 左下角圆角半径。
     */
    fun setCornerRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        this.drawable.setCornerRadius(topLeft, topRight, bottomRight, bottomLeft)
    }
}