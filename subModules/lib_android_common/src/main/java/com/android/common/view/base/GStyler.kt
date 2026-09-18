package com.android.common.view.base

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.android.common.view.base.BaseDrawable

/**
 * GStyler
 *
 * 一个通用的样式辅助类，用于给任意 View 添加以下能力：
 *
 * 1. 渐变背景
 * 2. 纯色背景
 * 3. Drawable 背景
 * 4. 背景渐变角度
 * 5. 边框宽度
 * 6. 纯色边框
 * 7. 渐变边框
 * 8. 边框渐变角度
 * 9. 统一圆角
 * 10. 四个角分别设置圆角
 *
 * 设计目的：
 *
 * Android 自定义 View 通常只能继承一个父类，例如：
 *
 * - AppCompatTextView
 * - ConstraintLayout
 * - LinearLayout
 * - AppCompatImageView
 *
 * 如果把背景、边框、圆角逻辑直接写到每个 View 里，
 * 会造成大量重复代码。
 *
 * 所以这里使用“组合模式”：
 *
 * - BaseDrawable 负责真正的绘制逻辑
 * - GStyler 负责把 BaseDrawable 挂载到 View 上，并提供统一的调用入口
 * - 具体 View 只需要持有一个 GStyler 实例
 *
 * 示例：
 *
 * ```
 * class GTextView : AppCompatTextView {
 *
 *     private val styler: GStyler
 *
 *     constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
 *         styler = GStyler(this, context, attrs)
 *     }
 *
 *     fun getStyler(): GStyler {
 *         return styler
 *     }
 * }
 * ```
 *
 * XML 中的通用属性会由 BaseDrawable 统一解析：
 *
 * ```
 * app:start_color="#FF0000"
 * app:end_color="#00FF00"
 * app:g_angle="0"
 * app:border="1dp"
 * app:border_color="#FFFFFF"
 * app:radius="8dp"
 * ```
 *
 * 注意：
 *
 * 这个类不是 View，也不是 Drawable。
 * 它只是 View 和 BaseDrawable 之间的样式桥接器。
 *
 * @property view 当前需要应用渐变背景、边框、圆角效果的宿主 View。
 * @param context Context，用于读取 XML 属性。
 * @param attrs AttributeSet，用于解析 XML 中配置的 CommonGradientAttrs 属性。
 */
class GStyler(
    private val view: View,
    private val context: Context,
    attrs: AttributeSet?
) {

    /**
     * 内部真正负责绘制背景、边框、圆角的 Drawable。
     *
     * GStyler 本身不做绘制，所有绘制逻辑都委托给 BaseDrawable。
     *
     * 添加 @VisibleForTesting 的原因：
     * - 正常业务代码不建议直接访问 drawable
     * - 测试代码可以通过该属性验证解析结果或绘制逻辑
     *
     * 例如单元测试中可以检查：
     *
     * ```
     * val styler = GStyler(view, context, attrs)
     * assertNotNull(styler.drawable)
     * ```
     */
    @VisibleForTesting
    val drawable: BaseDrawable = BaseDrawable()

    /**
     * 初始化逻辑。
     *
     * 1. 先让 BaseDrawable 从 XML 中解析通用样式属性。
     * 2. 再把 BaseDrawable 设置为宿主 View 的 background。
     *
     * 为什么先 applyAttributes 再设置 background？
     *
     * - 先解析属性，可以让 drawable 初始化完成后再参与绘制。
     * - 再设置到 view.background，View 后续绘制背景时就会调用 BaseDrawable.draw()。
     *
     * 需要注意：
     *
     * 这里会覆盖宿主 View 原本的 background。
     *
     * 不过 BaseDrawable 内部已经会解析 android:background：
     *
     * ```
     * systemBackground = style.getDrawable(
     *     R.styleable.CommonGradientAttrs_android_background
     * )
     * ```
     *
     * 所以 XML 中原本设置的 android:background 并不会完全丢失，
     * 而是会被 BaseDrawable 接管后统一绘制。
     */
    init {
        drawable.applyAttributes(context, attrs)
        /**
         * 如果使用阴影，需要关闭硬件加速，
         * 否则 Paint.setShadowLayer 可能不生效。
         */
        if (drawable.hasShadow()) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        view.background = drawable
    }

    // ------------------------------------------------------------------------
    // 背景相关动态设置方法
    // ------------------------------------------------------------------------

    /**
     * 动态设置背景 Drawable。
     *
     * 设置后会清除 BaseDrawable 内部原来的纯色背景和渐变背景，
     * 改为使用传入的 Drawable 作为背景内容。
     *
     * 示例：
     *
     * ```
     * imageView.getStyler().setBackgroundDrawable(
     *     ContextCompat.getDrawable(context, R.drawable.bg_card)
     * )
     * ```
     *
     * @param drawable 要设置的背景 Drawable，可以为 null。
     */
    fun setBackgroundDrawable(drawable: Drawable?) {
        this.drawable.setBackgroundDrawable(drawable)

        /**
         * 通知宿主 View 重新绘制。
         *
         * BaseDrawable 内部也会调用 invalidateSelf()，
         * 这里再调用 view.invalidate() 是为了确保宿主 View 立即刷新。
         */
        view.invalidate()
    }

    /**
     * 动态设置普通背景资源。
     *
     * 注意：
     *
     * 不要直接调用 view.setBackgroundResource(resId)，
     * 因为那样会把 BaseDrawable 整个替换掉，
     * 导致 selected 背景、圆角、边框等能力失效。
     */
    fun setBackgroundResource(@DrawableRes resId: Int) {
        val drawable = if (resId != 0) {
            ContextCompat.getDrawable(context, resId)
        } else {
            null
        }

        this.drawable.setBackgroundDrawable(drawable)
        view.invalidate()
    }

    /**
     * 动态设置 selected 选中态背景资源。
     *
     * 支持：
     *
     * 1. shape
     * 2. bitmap
     * 3. vector
     * 4. selector
     * 5. layer-list
     */
    fun setSelectedBackgroundResource(@DrawableRes resId: Int) {
        val drawable = if (resId != 0) {
            ContextCompat.getDrawable(context, resId)
        } else {
            null
        }

        this.drawable.setSelectedBackgroundDrawable(drawable)
        view.invalidate()
    }

    /**
     * 动态设置两色渐变背景。
     *
     * 这里只做转发：
     * GStyler 不负责绘制，真正的绘制逻辑交给 BaseDrawable。
     *
     * @param startColor 渐变起始颜色。
     * @param endColor 渐变结束颜色。
     */
    fun setBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        // 交给 BaseDrawable 设置两色渐变背景
        this.drawable.setBackgroundColors(startColor, endColor)

        // 通知宿主 View 重新绘制
        view.invalidate()
    }

    /**
     * 动态设置三色渐变背景。
     *
     * @param startColor 渐变起始颜色。
     * @param centerColor 渐变中心颜色。
     * @param endColor 渐变结束颜色。
     */
    fun setBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        // 交给 BaseDrawable 设置三色渐变背景
        this.drawable.setBackgroundColors(startColor, centerColor, endColor)

        // 通知宿主 View 重新绘制
        view.invalidate()
    }

    /**
     * 动态设置纯色背景。
     *
     * 调用后会清除 BaseDrawable 内部的背景 Drawable，
     * 并把背景设置为指定纯色。
     *
     * @param color 背景颜色。
     */
    fun setBackgroundColor(@ColorInt color: Int) {
        this.drawable.setBackgroundColor(color)
        view.invalidate()
    }

    /**
     * 动态设置背景渐变角度。
     *
     * 角度说明：
     *
     * - 0   度：从左到右
     * - 90  度：从上到下
     * - 180 度：从右到左
     * - 270 度：从下到上
     *
     * @param angle 渐变角度。
     */
    fun setBackgroundGradientAngle(angle: Float) {
        this.drawable.setBackgroundGradientAngle(angle)
        view.invalidate()
    }

    // ------------------------------------------------------------------------
    // 边框相关动态设置方法
    // ------------------------------------------------------------------------

    /**
     * 动态设置边框宽度。
     *
     * 注意：
     *
     * 单独设置边框宽度不会一定显示边框。
     * 还需要设置边框颜色，例如：
     *
     * ```
     * getStyler().setBorderWidth(2f)
     * getStyler().setBorderColor(Color.RED)
     * ```
     *
     * @param width 边框宽度，单位 px。
     */
    fun setBorderWidth(width: Float) {
        this.drawable.setBorderWidth(width)
        view.invalidate()
    }

    /**
     * 动态设置两色渐变边框。
     *
     * 注意：
     * 如果 borderWidth <= 0，即使设置了边框颜色也不会显示。
     *
     * @param startColor 边框渐变起始颜色。
     * @param endColor 边框渐变结束颜色。
     */
    fun setBorderColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        // 交给 BaseDrawable 设置两色渐变边框
        this.drawable.setBorderColors(startColor, endColor)

        // 通知宿主 View 重新绘制
        view.invalidate()
    }

    /**
     * 动态设置三色渐变边框。
     *
     * 注意：
     * 如果 borderWidth <= 0，即使设置了边框颜色也不会显示。
     *
     * @param startColor 边框渐变起始颜色。
     * @param centerColor 边框渐变中心颜色。
     * @param endColor 边框渐变结束颜色。
     */
    fun setBorderColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        // 交给 BaseDrawable 设置三色渐变边框
        this.drawable.setBorderColors(startColor, centerColor, endColor)

        // 通知宿主 View 重新绘制
        view.invalidate()
    }

    /**
     * 动态设置纯色边框颜色。
     *
     * 注意：
     *
     * 如果 borderWidth <= 0，即使设置了颜色也不会显示边框。
     * 需要同时设置边框宽度：
     *
     * ```
     * getStyler().setBorderWidth(1.dp)
     * getStyler().setBorderColor(Color.WHITE)
     * ```
     *
     * @param color 边框颜色。
     */
    fun setBorderColor(@ColorInt color: Int) {
        this.drawable.setBorderColor(color)
        view.invalidate()
    }

    /**
     * 动态设置边框渐变角度。
     *
     * 只有使用渐变边框时，该设置才有明显效果。
     *
     * @param angle 边框渐变角度。
     */
    fun setBorderGradientAngle(angle: Float) {
        this.drawable.setBorderGradientAngle(angle)
        view.invalidate()
    }

    // ------------------------------------------------------------------------
    // 圆角相关动态设置方法
    // ------------------------------------------------------------------------

    /**
     * 动态设置统一圆角。
     *
     * 四个角都会使用相同半径。
     *
     * 注意：
     *
     * 这里传入的是 px，不是 dp。
     *
     * 如果你在业务代码中想传 dp，需要先转换，例如：
     *
     * ```
     * val radiusPx = 8.dp
     * getStyler().setCornerRadius(radiusPx)
     * ```
     *
     * @param radius 圆角半径，单位 px。
     */
    fun setCornerRadius(radius: Float) {
        this.drawable.setCornerRadius(radius)
        view.invalidate()
    }

    /**
     * 动态设置四个角不同的圆角。
     *
     * 参数顺序：
     *
     * 1. 左上角
     * 2. 右上角
     * 3. 右下角
     * 4. 左下角
     *
     * 示例：
     *
     * ```
     * getStyler().setCornerRadius(
     *     topLeft = 12f,
     *     topRight = 12f,
     *     bottomRight = 0f,
     *     bottomLeft = 0f
     * )
     * ```
     *
     * @param topLeft 左上角圆角半径，单位 px。
     * @param topRight 右上角圆角半径，单位 px。
     * @param bottomRight 右下角圆角半径，单位 px。
     * @param bottomLeft 左下角圆角半径，单位 px。
     */
    fun setCornerRadius(
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float
    ) {
        this.drawable.setCornerRadius(
            topLeft,
            topRight,
            bottomRight,
            bottomLeft
        )
        view.invalidate()
    }

    /**
     * 获取当前 View 使用的圆角数组。
     */
    internal fun getCornerRadii(): FloatArray? {
        return drawable.getCornerRadii()
    }


    /**
     * 动态设置阴影。
     */
    fun setShadow(
        @ColorInt color: Int,
        radius: Float,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        drawable.setShadow(color, radius, dx, dy)

        // 阴影依赖软件层
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        /**
         * 阴影会影响 Drawable padding，
         * 所以需要重新布局。
         */
        view.requestLayout()
        view.invalidate()
    }

    /**
     * 设置选中态图片背景。
     */
    fun setSelectedBackgroundDrawable(drawable: Drawable?) {
        this.drawable.setSelectedBackgroundDrawable(drawable)
        view.invalidate()
    }

    /**
     * 设置选中态纯色背景。
     */
    fun setSelectedBackgroundColor(@ColorInt color: Int) {
        drawable.setSelectedBackgroundColor(color)
        view.invalidate()
    }

    /**
     * 设置选中态两色渐变背景。
     */
    fun setSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        drawable.setSelectedBackgroundColors(startColor, endColor)
        view.invalidate()
    }

    /**
     * 设置选中态三色渐变背景。
     */
    fun setSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        drawable.setSelectedBackgroundColors(startColor, centerColor, endColor)
        view.invalidate()
    }

    /**
     * 设置选中态边框颜色。
     */
    fun setSelectedBorderColor(@ColorInt color: Int) {
        drawable.setSelectedBorderColor(color)
        view.invalidate()
    }

    /**
     * 设置 View 是否选中。
     */
    fun setSelected(selected: Boolean) {
        view.isSelected = selected
    }
}