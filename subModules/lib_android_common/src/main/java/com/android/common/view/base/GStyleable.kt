package com.android.common.view.base

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * GStyleable
 *
 * 通用样式能力接口。
 *
 * 作用：
 *
 * 1. 统一暴露背景、边框、圆角相关方法。
 * 2. 让不同类型的自定义 View 都可以拥有相同的样式 API。
 * 3. 减少 GTextView、GConstraintLayout、GLinearLayout 等类中的重复方法。
 *
 * 注意：
 *
 * Kotlin 接口不能直接保存字段。
 * 所以这里不能在接口里直接创建 GStyler。
 *
 * 每个具体 View 仍然需要自己创建：
 *
 * ```
 * override val gStyler = GStyler(this, context, attrs)
 * ```
 *
 * 接口只负责把方法统一代理给 gStyler。
 */
interface GStyleable {

    /**
     * 当前 View 对应的样式处理器。
     *
     * 具体实现类需要初始化它。
     */
    val gStyler: GStyler
    /**
     * 获取当前 View 对应的 GStyler。
     *
     * 如果接口中没有暴露某些高级方法，
     * 外部可以通过 getStyler() 直接访问。
     *
     * 示例：
     *
     * val styler = view.getStyler()
     */
    fun getStyler(): GStyler {
        return gStyler
    }

    /**
     * 设置背景 Drawable。
     */
    fun setGBackgroundDrawable(drawable: Drawable?) {
        gStyler.setBackgroundDrawable(drawable)
    }

    /**
     * 设置普通背景资源。
     *
     * 不要直接使用 View.setBackgroundResource，
     * 否则会替换掉 BaseDrawable。
     */
    fun setGBackgroundResource(@DrawableRes resId: Int) {
        gStyler.setBackgroundResource(resId)
    }

    /**
     * 设置 selected 选中态背景资源。
     */
    fun setGSelectedBackgroundResource(@DrawableRes resId: Int) {
        gStyler.setSelectedBackgroundResource(resId)
    }

    /**
     * 设置两色渐变背景。
     *
     * 调用示例：
     *
     * getStyler().setBackgroundColors(Color.RED, Color.BLUE)
     *
     * 或者：
     *
     * setGBackgroundColors(Color.RED, Color.BLUE)
     *
     * 这里不提供默认 centerColor，
     * 是为了避免两色渐变被错误地变成：
     *
     * start -> transparent -> end
     */
    fun setGBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setBackgroundColors(startColor, endColor)
    }

    /**
     * 设置三色渐变背景。
     *
     * 调用示例：
     *
     * setGBackgroundColors(
     *     Color.RED,
     *     Color.GREEN,
     *     Color.BLUE
     * )
     */
    fun setGBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setBackgroundColors(startColor, centerColor, endColor)
    }

    /**
     * 设置纯色背景。
     *
     * 这里不建议命名为 setBackgroundColor，
     * 因为 View 本身已经有 setBackgroundColor 方法。
     */
    fun setGBackgroundColor(@ColorInt color: Int) {
        gStyler.setBackgroundColor(color)
    }

    /**
     * 设置背景渐变角度。
     */
    fun setGBackgroundGradientAngle(angle: Float) {
        gStyler.setBackgroundGradientAngle(angle)
    }

    /**
     * 设置边框宽度。
     */
    fun setGBorderWidth(width: Float) {
        gStyler.setBorderWidth(width)
    }

    /**
     * 设置两色渐变边框。
     *
     * 注意：
     * 需要同时设置边框宽度，否则边框不会显示。
     *
     * 示例：
     *
     * setGBorderWidth(2f)
     * setGBorderColors(Color.RED, Color.BLUE)
     */
    fun setGBorderColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setBorderColors(startColor, endColor)
    }

    /**
     * 设置三色渐变边框。
     *
     * 注意：
     * 需要同时设置边框宽度，否则边框不会显示。
     *
     * 示例：
     *
     * setGBorderWidth(2f)
     * setGBorderColors(Color.RED, Color.GREEN, Color.BLUE)
     */
    fun setGBorderColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setBorderColors(startColor, centerColor, endColor)
    }

    /**
     * 设置纯色边框。
     */
    fun setGBorderColor(@ColorInt color: Int) {
        gStyler.setBorderColor(color)
    }

    /**
     * 设置边框渐变角度。
     */
    fun setGBorderGradientAngle(angle: Float) {
        gStyler.setBorderGradientAngle(angle)
    }

    /**
     * 设置统一圆角。
     */
    fun setGCornerRadius(radius: Float) {
        gStyler.setCornerRadius(radius)
    }

    /**
     * 设置四个角不同圆角。
     */
    fun setGCornerRadius(
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float
    ) {
        gStyler.setCornerRadius(
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft
        )
    }


    /**
     * 设置阴影。
     */
    fun setGShadow(
        @ColorInt color: Int,
        radius: Float,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        gStyler.setShadow(color, radius, dx, dy)
    }

    /**
     * 设置选中态图片背景。
     */
    fun setGSelectedBackgroundDrawable(drawable: Drawable?) {
        gStyler.setSelectedBackgroundDrawable(drawable)
    }

    /**
     * 设置选中态纯色背景。
     */
    fun setGSelectedBackgroundColor(@ColorInt color: Int) {
        gStyler.setSelectedBackgroundColor(color)
    }

    /**
     * 设置选中态两色渐变背景。
     */
    fun setGSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setSelectedBackgroundColors(startColor, endColor)
    }

    /**
     * 设置选中态三色渐变背景。
     */
    fun setGSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        gStyler.setSelectedBackgroundColors(startColor, centerColor, endColor)
    }

    /**
     * 设置选中态边框颜色。
     */
    fun setGSelectedBorderColor(@ColorInt color: Int) {
        gStyler.setSelectedBorderColor(color)
    }

    /**
     * 设置当前 View 是否选中。
     */
    fun setGSelected(selected: Boolean) {
        gStyler.setSelected(selected)
    }
}