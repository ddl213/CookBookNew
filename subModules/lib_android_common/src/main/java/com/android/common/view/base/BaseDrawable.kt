package com.android.common.view.base

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * BaseDrawable
 *
 * 一个通用背景 Drawable。
 *
 * 支持能力：
 *
 * 1. 普通纯色背景
 * 2. 普通 Drawable / 图片背景
 * 3. 普通渐变背景，两色 / 三色
 * 4. 背景渐变角度
 * 5. 边框宽度
 * 6. 普通纯色边框
 * 7. 普通渐变边框，两色 / 三色
 * 8. 边框渐变角度
 * 9. 统一圆角
 * 10. 四个角单独圆角
 * 11. android:background 接管绘制
 * 12. 阴影
 * 13. selected 选中态背景
 * 14. selected 选中态 Drawable / 图片背景
 * 15. selected 选中态渐变背景
 * 16. selected 选中态边框
 *
 * 注意：
 *
 * 该类不是 View，不应该被 View 继承。
 * 它应该作为 View.background 使用。
 *
 * XML 属性解析逻辑已经拆到 BaseDrawableAttrsParser.kt 中，
 * BaseDrawable 只负责：
 *
 * 1. 保存解析后的配置
 * 2. 根据状态选择当前背景 / 边框
 * 3. 绘制背景、阴影、边框
 * 4. 对外提供动态设置方法
 *
 * ------------------------------------------------------------------------
 * XML 属性总览（命名空间 app:，定义见 attrs_base_drawable.xml）
 * ------------------------------------------------------------------------
 *
 * 一、普通背景
 *
 * app:android:background   reference|color  系统背景，被 BaseDrawable 接管绘制，优先级最高，覆盖渐变/纯色
 * app:start_color          color           背景渐变起始色
 * app:center_color         color           背景渐变中心色（仅两色渐变时可不填）
 * app:end_color            color           背景渐变结束色
 * app:g_angle              float           背景渐变角度：0=左→右，90=上→下，180=右→左，270=下→上
 * app:g_center_position    float           背景渐变中间色位置，0~1，默认 0.5
 *
 * 二、普通边框
 *
 * app:border                       dimension  边框宽度，≤0 则不绘制
 * app:border_color                color      纯色边框颜色
 * app:border_start_color          color     边框渐变起始色
 * app:border_center_color         color     边框渐变中心色
 * app:border_end_color            color     边框渐变结束色
 * app:border_color_angle          float     边框渐变角度
 * app:border_center_position      float     边框渐变中间色位置，0~1，默认 0.5
 *
 * 三、圆角
 *
 * app:radius               dimension  统一圆角（四角相同）
 * app:radius_top_left      dimension  左上角圆角
 * app:radius_top_right     dimension  右上角圆角
 * app:radius_bottom_right  dimension  右下角圆角
 * app:radius_bottom_left   dimension  左下角圆角
 *
 * 四、阴影（需宿主 View 开软件层 LAYER_TYPE_SOFTWARE，由 GStyler 统一处理）
 *
 * app:g_shadow_color    color       阴影颜色
 * app:g_shadow_radius   dimension   阴影模糊半径（px）
 * app:g_shadow_dx       dimension   阴影 X 偏移（px，正→右，负→左）
 * app:g_shadow_dy       dimension   阴影 Y 偏移（px，正→下，负→上）
 *
 * 五、selected 选中态背景（isSelected=true 且选中态有内容时覆盖普通背景）
 *
 * app:g_selected_background             reference|color  选中态背景，可填 @drawable 或 #颜色，优先级最高
 * app:g_selected_start_color            color            选中态背景渐变起始色
 * app:g_selected_center_color           color            选中态背景渐变中心色
 * app:g_selected_end_color              color            选中态背景渐变结束色
 * app:g_selected_angle                  float            选中态背景渐变角度
 * app:g_selected_center_position        float            选中态背景渐变中间色位置，0~1
 *
 * 六、selected 选中态边框（仅控制颜色，宽度复用普通 app:border）
 *
 * app:g_selected_border_color            color   选中态纯色边框
 * app:g_selected_border_start_color      color   选中态边框渐变起始色
 * app:g_selected_border_center_color     color   选中态边框渐变中心色
 * app:g_selected_border_end_color        color   选中态边框渐变结束色
 * app:g_selected_border_angle            float   选中态边框渐变角度
 * app:g_selected_border_center_position  float   选中态边框渐变中间色位置，0~1
 *
 * 机制说明：
 *
 * 1. 背景优先级：Drawable(android:background / g_selected_background) > 渐变(start/end_color 等) > 纯色(代码 setBackgroundColor)
 * 2. selected 覆盖：isSelected 且选中态配置非空时，背景与边框各自被选中态替换，否则回落普通配置
 * 3. 渐变兜底：只填 start 或 end 也能用，未填色会用已填色补齐，避免 LinearGradient 崩溃
 * 4. 角度坐标系与 Android GradientDrawable 一致，0~360，按 cos/sin 计算渐变起止点
 * 5. 阴影实现为真外阴影：先画带 ShadowLayer 的形状，再用 CLEAR 模式清空内容区域，仅保留外部阴影
 * 6. Drawable 不能画到 View bounds 外，所以阴影按 radius + dx/dy 向内缩进预留空间
 *
 * 注意：以下属性仅属于 GTextView，作用于文字本身，BaseDrawable 不解析：
 * text_start_color / text_center_color / text_end_color / text_color_angle
 * g_selected_text_color / g_selected_text_start_color / g_selected_text_center_color
 * g_selected_text_end_color / g_selected_text_color_angle / text_drawable
 */
class BaseDrawable : Drawable() {

    /**
     * 背景画笔。
     *
     * 用于绘制：
     *
     * 1. 纯色背景
     * 2. 渐变背景
     */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * 边框画笔。
     *
     * 用于绘制：
     *
     * 1. 纯色边框
     * 2. 渐变边框
     */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /**
     * 阴影画笔。
     *
     * 注意：
     *
     * Paint.setShadowLayer 在硬件加速下可能不生效。
     * 如果要绘制阴影，宿主 View 需要开启软件层：
     *
     * view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
     *
     * 这个操作建议放在 GStyler 中统一处理。
     */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * 清除画笔。
     *
     * 用于实现真正的外阴影：
     *
     * 1. 先绘制带 ShadowLayer 的形状
     * 2. 再把内容区域清空
     * 3. 最终只保留内容区域外部的阴影
     */
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /**
     * 复用 Path。
     *
     * 避免 draw() 中频繁创建对象。
     */
    private val path = Path()

    /**
     * 当前 Drawable 实际绘制区域。
     *
     * 如果设置了阴影，rectF 会向内缩进，
     * 给阴影留出绘制空间，避免阴影被 bounds 裁剪。
     */
    private val rectF = RectF()

    /**
     * 边框绘制区域。
     *
     * 边框是居中绘制在线条上的，
     * 所以绘制边框时需要根据 borderWidth 向内缩进半个边框宽度。
     */
    private val borderRectF = RectF()

    // ------------------------------------------------------------------------
    // 配置对象
    // ------------------------------------------------------------------------

    /**
     * 普通背景配置。
     *
     * 来源：
     *
     * 1. android:background
     * 2. app:start_color
     * 3. app:center_color
     * 4. app:end_color
     * 5. app:g_angle
     */
    private var background = BackgroundSpec()

    /**
     * 普通边框配置。
     *
     * 来源：
     *
     * 1. app:border
     * 2. app:border_color
     * 3. app:border_start_color
     * 4. app:border_center_color
     * 5. app:border_end_color
     * 6. app:border_color_angle
     */
    private var border = BorderSpec()

    /**
     * 圆角配置。
     *
     * 来源：
     *
     * 1. app:radius
     * 2. app:radius_top_left
     * 3. app:radius_top_right
     * 4. app:radius_bottom_right
     * 5. app:radius_bottom_left
     */
    private var corner = CornerSpec()

    /**
     * 获取当前圆角数组。
     *
     * 顺序：
     * 左上、右上、右下、左下
     *
     * 每个角包含 x/y 两个 radius。
     */
    internal fun getCornerRadii(): FloatArray? {
        return corner.radii
    }

    /**
     * 阴影配置。
     *
     * 来源：
     *
     * 1. app:g_shadow_color
     * 2. app:g_shadow_radius
     * 3. app:g_shadow_dx
     * 4. app:g_shadow_dy
     */
    private var shadow = ShadowSpec()

    /**
     * selected 选中态背景配置。
     *
     * 来源：
     *
     * 1. app:g_selected_background
     * 2. app:g_selected_start_color
     * 3. app:g_selected_center_color
     * 4. app:g_selected_end_color
     * 5. app:g_selected_angle
     *
     * 其中：
     *
     * app:g_selected_background 支持颜色和 Drawable / 图片：
     *
     * app:g_selected_background="#FF5722"
     *
     * app:g_selected_background="@drawable/bg_selected"
     */
    private var selectedBackground = BackgroundSpec()

    /**
     * selected 选中态边框配置。
     *
     * 来源：
     *
     * 1. app:g_selected_border_color
     * 2. app:g_selected_border_start_color
     * 3. app:g_selected_border_center_color
     * 4. app:g_selected_border_end_color
     * 5. app:g_selected_border_angle
     *
     * 注意：
     *
     * 选中态边框只控制颜色 / 渐变颜色。
     * 边框宽度仍然使用普通边框宽度 app:border。
     */
    private var selectedBorder = BorderSpec()

    /**
     * 当前 Drawable 是否处于 selected 状态。
     *
     * 当宿主 View 调用：
     *
     * view.isSelected = true
     *
     * Drawable 会通过 onStateChange() 收到 android.R.attr.state_selected。
     */
    private var isSelectedState: Boolean = false

    /**
     * 从 XML AttributeSet 中解析通用属性。
     *
     * 具体解析逻辑已经移动到 BaseDrawableAttrsParser.kt。
     *
     * 这样 BaseDrawable 不需要维护大量繁琐的 obtainStyledAttributes 代码，
     * 只接收最终的 BaseDrawableConfig。
     */
    fun applyAttributes(context: Context, attrs: AttributeSet?) {
        applyConfig(
            BaseDrawableAttrsParser.parse(context, attrs)
        )
    }

    /**
     * 应用解析后的配置。
     *
     * 该方法保持 private，避免对外暴露内部数据结构。
     */
    private fun applyConfig(config: BaseDrawableConfig) {
        background = config.background
        border = config.border
        corner = config.corner
        shadow = config.shadow
        selectedBackground = config.selectedBackground
        selectedBorder = config.selectedBorder

        syncChildDrawableState()
        invalidateSelf()
    }

    /**
     * Drawable 绘制入口。
     *
     * 绘制顺序：
     *
     * 1. 计算绘制区域
     * 2. 绘制阴影
     * 3. 绘制背景
     * 4. 绘制边框
     */
    override fun draw(canvas: Canvas) {
        rectF.set(bounds)

        /**
         * 外阴影需要在 Drawable bounds 内预留空间。
         *
         * 注意：
         *
         * Drawable 本身不能绘制到 View bounds 外部，
         * 所以这里会把真正的背景内容区域向内缩进，
         * 外面留下的区域用来显示阴影。
         */
        if (hasShadow()) {
            rectF.set(
                bounds.left + shadowInsetLeft(),
                bounds.top + shadowInsetTop(),
                bounds.right - shadowInsetRight(),
                bounds.bottom - shadowInsetBottom()
            )
        }

        /**
         * 如果 View 太小，阴影 inset 后内容区域无效，则不绘制。
         */
        if (rectF.width() <= 0f || rectF.height() <= 0f) {
            return
        }

        drawShadow(canvas)
        drawBackground(canvas)
        drawBorder(canvas)
    }

    /**
     * 是否需要绘制阴影。
     */
    fun hasShadow(): Boolean {
        return shadow.hasShadow()
    }
    /**
     * 阴影左侧需要预留的空间。
     *
     * dx < 0 时，阴影向左偏移，左侧需要额外预留 -dx。
     */
    private fun shadowInsetLeft(): Float {
        return if (hasShadow()) {
            shadow.radius + max(0f, -shadow.dx)
        } else {
            0f
        }
    }

    /**
     * 阴影顶部需要预留的空间。
     *
     * dy < 0 时，阴影向上偏移，顶部需要额外预留 -dy。
     */
    private fun shadowInsetTop(): Float {
        return if (hasShadow()) {
            shadow.radius + max(0f, -shadow.dy)
        } else {
            0f
        }
    }

    /**
     * 阴影右侧需要预留的空间。
     *
     * dx > 0 时，阴影向右偏移，右侧需要额外预留 dx。
     */
    private fun shadowInsetRight(): Float {
        return if (hasShadow()) {
            shadow.radius + max(0f, shadow.dx)
        } else {
            0f
        }
    }

    /**
     * 阴影底部需要预留的空间。
     *
     * dy > 0 时，阴影向下偏移，底部需要额外预留 dy。
     */
    private fun shadowInsetBottom(): Float {
        return if (hasShadow()) {
            shadow.radius + max(0f, shadow.dy)
        } else {
            0f
        }
    }

    /**
     * 给宿主 View 提供背景 padding。
     *
     * 作用：
     *
     * 1. 为外阴影预留空间
     * 2. 避免 TextView 文本或子 View 内容压到阴影区域
     *
     * 注意：
     *
     * Drawable 不能绘制到 View bounds 外部，
     * 所以外阴影本质上是在 View bounds 内部预留空间实现的。
     */
    override fun getPadding(padding: Rect): Boolean {
        if (!hasShadow()) {
            padding.set(0, 0, 0, 0)
            return false
        }

        padding.set(
            ceil(shadowInsetLeft()).toInt(),
            ceil(shadowInsetTop()).toInt(),
            ceil(shadowInsetRight()).toInt(),
            ceil(shadowInsetBottom()).toInt()
        )

        return true
    }


    /**
     * 获取当前应该使用的背景。
     *
     * 优先级：
     *
     * 1. 如果当前是 selected 状态，并且 selectedBackground 有内容，则使用 selectedBackground
     * 2. 否则使用普通 background
     */
    private fun currentBackground(): BackgroundSpec {
        return if (isSelectedState && selectedBackground.hasContent()) {
            selectedBackground
        } else {
            background
        }
    }

    /**
     * 获取当前应该使用的边框。
     *
     * 优先级：
     *
     * 1. 如果当前是 selected 状态，并且 selectedBorder 有颜色配置，则使用 selectedBorder
     * 2. 否则使用普通 border
     *
     * 注意：
     *
     * selectedBorder 只影响颜色。
     * 边框宽度始终使用普通 border.width。
     */
    private fun currentBorder(): BorderSpec {
        return if (isSelectedState && selectedBorder.hasColor()) {
            selectedBorder
        } else {
            border
        }
    }

    /**
     * 绘制外阴影。
     *
     * 实现逻辑：
     *
     * 1. 开启离屏图层
     * 2. 绘制带 ShadowLayer 的内容形状
     * 3. 使用 CLEAR 模式清除内容区域
     * 4. restore 后只保留内容区域外部的阴影
     *
     * 这样可以避免阴影污染背景内部区域，
     * 实现真正意义上的外阴影。
     */
    private fun drawShadow(canvas: Canvas) {
        if (!hasShadow()) return

        val saveCount = canvas.saveLayer(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            null
        )

        shadowPaint.color = shadow.color
        shadowPaint.setShadowLayer(
            shadow.radius,
            shadow.dx,
            shadow.dy,
            shadow.color
        )

        /**
         * 第一步：绘制带阴影的形状。
         *
         * 这里会同时产生：
         *
         * 1. 外部阴影
         * 2. 内容区域填充
         */
        drawShape(canvas, rectF, shadowPaint)

        /**
         * 绘制完成后清除 ShadowLayer，
         * 避免影响后续绘制。
         */
        shadowPaint.clearShadowLayer()

        /**
         * 第二步：清除内容区域。
         *
         * 清除后，内容区域内部不再有阴影或填充，
         * 最终只保留 rectF 外部的阴影。
         */
        drawShape(canvas, rectF, clearPaint)

        canvas.restoreToCount(saveCount)
    }

    /**
     * 按当前圆角配置绘制形状。
     *
     * 阴影、背景、清除内容区域都可以复用这个方法。
     */
    private fun drawShape(
        canvas: Canvas,
        rect: RectF,
        targetPaint: Paint
    ) {
        if (corner.radii != null) {
            path.reset()
            path.addRoundRect(rect, corner.radii!!, Path.Direction.CW)
            canvas.drawPath(path, targetPaint)
        } else {
            canvas.drawRect(rect, targetPaint)
        }
    }

    /**
     * 绘制背景。
     *
     * 背景支持三种类型：
     *
     * 1. Drawable / 图片背景
     * 2. 渐变背景
     * 3. 纯色背景
     *
     * 优先级：
     *
     * 1. Drawable
     * 2. Gradient
     * 3. Color
     *
     * selected 状态下，如果 selectedBackground 有内容，
     * 会自动覆盖普通背景。
     */
    private fun drawBackground(canvas: Canvas) {
        val bg = currentBackground()

        if (!bg.hasContent()) return

        drawWithCornerClip(canvas) { c ->
            when {
                /**
                 * 1. 绘制 Drawable / 图片背景。
                 *
                 * 对应：
                 *
                 * android:background="@drawable/bg_default"
                 *
                 * 或：
                 *
                 * app:g_selected_background="@drawable/bg_selected"
                 */
                bg.drawable != null -> {
                    drawChildDrawable(c, bg.drawable!!)
                }

                /**
                 * 2. 绘制渐变背景。
                 *
                 * 对应：
                 *
                 * app:start_color="#FF0000"
                 * app:end_color="#00FF00"
                 *
                 * 或 selected：
                 *
                 * app:g_selected_start_color="#FF0000"
                 * app:g_selected_end_color="#00FF00"
                 */
                bg.gradient.hasColor() -> {
                    val colors = buildGradientColors(bg.gradient)
                    paint.shader = createLinearGradient(
                        colors = colors,
                        angle = bg.gradient.angle,
                        centerPosition = bg.gradient.centerPosition,
                        bounds = rectF
                    )

                    c.drawRect(rectF, paint)
                    paint.shader = null
                }

                /**
                 * 3. 绘制纯色背景。
                 *
                 * 主要用于代码动态设置：
                 *
                 * setBackgroundColor(...)
                 */
                bg.color != BD_UNSET -> {
                    paint.shader = null
                    paint.color = bg.color
                    c.drawRect(rectF, paint)
                }
            }
        }
    }

    /**
     * 绘制边框。
     *
     * 边框支持：
     *
     * 1. 纯色边框
     * 2. 渐变边框
     *
     * selected 状态下，如果 selectedBorder 有颜色配置，
     * 会自动覆盖普通边框颜色。
     */
    private fun drawBorder(canvas: Canvas) {
        val width = border.width

        if (width <= 0f) return

        val currentBorder = currentBorder()

        if (!currentBorder.hasColor()) return

        borderPaint.strokeWidth = width

        val hasGradient = currentBorder.gradient.hasColor()

        val colors = if (hasGradient) {
            buildGradientColors(currentBorder.gradient)
        } else {
            intArrayOf(currentBorder.color, currentBorder.color)
        }

        borderPaint.shader = createLinearGradient(
            colors = colors,
            angle = currentBorder.gradient.angle,
            centerPosition = currentBorder.gradient.centerPosition,
            bounds = rectF
        )


        borderRectF.set(rectF)

        val halfBorder = width / 2f
        borderRectF.inset(halfBorder, halfBorder)

        if (corner.radii != null) {
            val borderCornerRadii = FloatArray(8) { index ->
                0f.coerceAtLeast(corner.radii!![index] - halfBorder)
            }

            path.reset()
            path.addRoundRect(
                borderRectF,
                borderCornerRadii,
                Path.Direction.CW
            )
            canvas.drawPath(path, borderPaint)
        } else {
            canvas.drawRect(borderRectF, borderPaint)
        }

        borderPaint.shader = null
    }

    /**
     * 在圆角裁剪范围内执行绘制。
     *
     * Drawable / 图片背景、渐变背景、纯色背景都需要受圆角限制。
     */
    private inline fun drawWithCornerClip(
        canvas: Canvas,
        drawAction: (Canvas) -> Unit
    ) {
        val radii = corner.radii

        if (radii != null) {
            path.reset()
            path.addRoundRect(rectF, radii, Path.Direction.CW)

            canvas.save()
            canvas.clipPath(path)
            drawAction(canvas)
            canvas.restore()
        } else {
            drawAction(canvas)
        }
    }

    /**
     * 绘制子 Drawable。
     *
     * 这里用于绘制：
     *
     * 1. android:background 解析出的 Drawable
     * 2. app:g_selected_background 解析出的 Drawable
     */
    private fun drawChildDrawable(canvas: Canvas, drawable: Drawable) {
        drawable.setBounds(
            rectF.left.toInt(),
            rectF.top.toInt(),
            rectF.right.toInt(),
            rectF.bottom.toInt()
        )
        drawable.state = state
        drawable.draw(canvas)
    }

    /**
     * 创建线性渐变 Shader。
     *
     * angle 说明：
     *
     * 0   表示从左到右
     * 90  表示从上到下
     * 180 表示从右到左
     * 270 表示从下到上
     *
     * centerPosition 说明：
     *
     * 用于控制渐变中间位置，取值范围 0f ~ 1f。
     *
     * 两色渐变：
     *
     * colors = [startColor, endColor]
     *
     * 会被解释为：
     *
     * 1. 0f ~ centerPosition：
     *    保持 startColor
     *
     * 2. centerPosition ~ 1f：
     *    从 startColor 渐变到 endColor
     *
     * 所以：
     *
     * centerPosition = 0f
     * 表示普通 startColor -> endColor 渐变。
     *
     * centerPosition = 1f
     * 表示整个区域都是 startColor。
     *
     * 三色渐变：
     *
     * colors = [startColor, centerColor, endColor]
     *
     * 会被解释为：
     *
     * startColor -> centerColor -> endColor
     *
     * centerPosition 控制 centerColor 出现的位置。
     */
    private fun createLinearGradient(
        @ColorInt colors: IntArray,
        angle: Float,
        centerPosition: Float,
        bounds: RectF
    ): LinearGradient {
        val radians = Math.toRadians(angle.toDouble())

        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        val halfLen =
            abs(dx) * bounds.width() / 2f +
                    abs(dy) * bounds.height() / 2f

        val x0 = centerX - dx * halfLen
        val y0 = centerY - dy * halfLen
        val x1 = centerX + dx * halfLen
        val y1 = centerY + dy * halfLen

        /**
         * 限制中间点位置范围。
         */
        val safeCenterPosition = centerPosition.coerceIn(0f, 1f)

        val shaderColors: IntArray
        val positions: FloatArray?

        when (colors.size) {
            /**
             * 三色渐变：
             *
             * colors = [startColor, centerColor, endColor]
             *
             * centerPosition 控制 centerColor 出现的位置。
             */
            3 -> {
                when {
                    /**
                     * centerPosition = 0
                     *
                     * centerColor 从起点就开始出现，
                     * startColor 只存在于 0 这个边界点，肉眼几乎不可见。
                     *
                     * 为避免 positions = [0, 0, 1] 这种重复位置带来的兼容性问题，
                     * 这里直接退化成：
                     *
                     * centerColor -> endColor
                     */
                    safeCenterPosition <= 0f -> {
                        shaderColors = intArrayOf(
                            colors[1],
                            colors[2]
                        )
                        positions = null
                    }

                    /**
                     * centerPosition = 1
                     *
                     * centerColor 出现在终点，
                     * endColor 只存在于 1 这个边界点，肉眼几乎不可见。
                     *
                     * 为避免 positions = [0, 1, 1] 这种重复位置带来的兼容性问题，
                     * 这里直接退化成：
                     *
                     * startColor -> centerColor
                     */
                    safeCenterPosition >= 1f -> {
                        shaderColors = intArrayOf(
                            colors[0],
                            colors[1]
                        )
                        positions = null
                    }

                    else -> {
                        shaderColors = colors
                        positions = floatArrayOf(
                            0f,
                            safeCenterPosition,
                            1f
                        )
                    }
                }
            }

            /**
             * 两色渐变：
             *
             * colors = [startColor, endColor]
             *
             * centerPosition 表示 startColor 保持到哪个位置。
             *
             * 例如：
             *
             * centerPosition = 0.3
             *
             * 表示：
             *
             * 0%   ~ 30%  保持 startColor
             * 30%  ~ 100% startColor 渐变到 endColor
             */
            2 -> {
                when {
                    /**
                     * centerPosition = 0
                     *
                     * 不保留 startColor 区域，
                     * 整体就是普通的 startColor -> endColor 渐变。
                     */
                    safeCenterPosition <= 0f -> {
                        shaderColors = intArrayOf(
                            colors[0],
                            colors[1]
                        )
                        positions = null
                    }

                    /**
                     * centerPosition = 1
                     *
                     * startColor 保持到 100%，
                     * 整个区域都应该是 startColor。
                     *
                     * 这里不能再使用：
                     *
                     * colors = [startColor, startColor, endColor]
                     * positions = [0f, 1f, 1f]
                     *
                     * 因为重复的 1f stop 在不同 Android 版本 / GPU 上可能表现不稳定。
                     *
                     * 所以直接创建一个纯 startColor 的 LinearGradient。
                     */
                    safeCenterPosition >= 1f -> {
                        shaderColors = intArrayOf(
                            colors[0],
                            colors[0]
                        )
                        positions = null
                    }

                    else -> {
                        shaderColors = intArrayOf(
                            colors[0],
                            colors[0],
                            colors[1]
                        )
                        positions = floatArrayOf(
                            0f,
                            safeCenterPosition,
                            1f
                        )
                    }
                }
            }

            /**
             * 理论上不会走到这里。
             *
             * 但为了避免极端情况下 LinearGradient 因 colors 数量不足崩溃，
             * 做兜底处理。
             */
            1 -> {
                shaderColors = intArrayOf(
                    colors[0],
                    colors[0]
                )
                positions = null
            }

            else -> {
                shaderColors = intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                )
                positions = null
            }
        }

        return LinearGradient(
            x0,
            y0,
            x1,
            y1,
            shaderColors,
            positions,
            Shader.TileMode.CLAMP
        )
    }

    /**
     * 根据 GradientSpec 构建渐变颜色数组。
     *
     * 支持：
     *
     * 1. 只设置 start
     * 2. 只设置 center
     * 3. 只设置 end
     * 4. start + end
     * 5. start + center + end
     *
     * 如果某个颜色未设置，会使用其它已设置颜色补齐，
     * 避免 LinearGradient 因颜色不足出现异常。
     */
    private fun buildGradientColors(gradient: GradientSpec): IntArray {
        return buildGradientColors(
            startColor = gradient.startColor,
            centerColor = gradient.centerColor,
            endColor = gradient.endColor
        )
    }

    /**
     * 根据 start / center / end 构建渐变颜色数组。
     */
    private fun buildGradientColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ): IntArray {
        val realStartColor = when {
            startColor != BD_UNSET -> startColor
            centerColor != BD_UNSET -> centerColor
            endColor != BD_UNSET -> endColor
            else -> Color.TRANSPARENT
        }

        val realEndColor = when {
            endColor != BD_UNSET -> endColor
            centerColor != BD_UNSET -> centerColor
            startColor != BD_UNSET -> startColor
            else -> realStartColor
        }

        return if (centerColor != BD_UNSET) {
            intArrayOf(realStartColor, centerColor, realEndColor)
        } else {
            intArrayOf(realStartColor, realEndColor)
        }
    }

    /**
     * Drawable 是否有状态。
     *
     * 返回 true 后，View 的 selected 状态变化时，
     * Drawable 才会收到 onStateChange() 回调。
     */
    override fun isStateful(): Boolean {
        return true
    }

    /**
     * View 状态变化回调。
     *
     * 当调用：
     *
     * view.isSelected = true
     *
     * 会进入这里，并收到 android.R.attr.state_selected。
     */
    override fun onStateChange(stateSet: IntArray): Boolean {
        val selected = stateSet.contains(android.R.attr.state_selected)

        syncChildDrawableState(stateSet)

        return if (isSelectedState != selected) {
            isSelectedState = selected
            invalidateSelf()
            true
        } else {
            false
        }
    }

    /**
     * 同步子 Drawable 状态。
     *
     * 例如：
     *
     * 1. android:background 是 selector
     * 2. app:g_selected_background 是 selector
     *
     * 都需要同步 stateSet。
     */
    private fun syncChildDrawableState(stateSet: IntArray = state) {
        background.drawable?.state = stateSet
        selectedBackground.drawable?.state = stateSet
    }

    /**
     * 设置透明度。
     */
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        borderPaint.alpha = alpha
        shadowPaint.alpha = alpha

        background.drawable?.alpha = alpha
        selectedBackground.drawable?.alpha = alpha

        invalidateSelf()
    }

    /**
     * 设置 ColorFilter。
     */
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter

        background.drawable?.colorFilter = colorFilter
        selectedBackground.drawable?.colorFilter = colorFilter

        invalidateSelf()
    }

    /**
     * 返回 Drawable 透明度类型。
     */
    override fun getOpacity(): Int {
        return if (
            paint.alpha == 255 &&
            !hasShadow() &&
            background.drawable == null &&
            selectedBackground.drawable == null
        ) {
            PixelFormat.OPAQUE
        } else {
            PixelFormat.TRANSLUCENT
        }
    }

    // ------------------------------------------------------------------------
    // 动态设置背景方法
    // ------------------------------------------------------------------------

    /**
     * 设置两色渐变背景。
     */
    fun setBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        background.clear()
        background.gradient.startColor = startColor
        background.gradient.centerColor = BD_UNSET
        background.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置三色渐变背景。
     */
    fun setBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        background.clear()
        background.gradient.startColor = startColor
        background.gradient.centerColor = centerColor
        background.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置普通背景渐变中间点位置。
     *
     * @param position 渐变中间点位置，建议范围 0f ~ 1f。
     *
     * 例如：
     *
     * 0.3f 表示中间色出现在 30% 的位置。
     *
     * 如果当前是两色渐变，则第一个颜色会作为中间色使用。
     */
    fun setBackgroundGradientCenterPosition(position: Float) {
        val safePosition = position.coerceIn(0f, 1f)

        if (background.gradient.centerPosition != safePosition) {
            background.gradient.centerPosition = safePosition
            invalidateSelf()
        }
    }


    /**
     * 设置纯色背景。
     */
    fun setBackgroundColor(@ColorInt color: Int) {
        background.clear()
        background.color = color

        invalidateSelf()
    }

    /**
     * 设置 Drawable / 图片背景。
     */
    fun setBackgroundDrawable(drawable: Drawable?) {
        background.clear()
        background.drawable = drawable?.mutate()
        syncChildDrawableState()

        invalidateSelf()
    }

    /**
     * 设置背景渐变角度。
     */
    fun setBackgroundGradientAngle(angle: Float) {
        if (background.gradient.angle != angle) {
            background.gradient.angle = angle
            invalidateSelf()
        }
    }

    /**
     * 清除普通背景。
     */
    fun clearBackground() {
        background.clear()
        invalidateSelf()
    }

    // ------------------------------------------------------------------------
    // 动态设置边框方法
    // ------------------------------------------------------------------------

    /**
     * 设置边框宽度。
     */
    fun setBorderWidth(width: Float) {
        if (border.width != width) {
            border.width = width
            invalidateSelf()
        }
    }

    /**
     * 设置两色渐变边框。
     */
    fun setBorderColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        border.clearColor()
        border.gradient.startColor = startColor
        border.gradient.centerColor = BD_UNSET
        border.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置三色渐变边框。
     */
    fun setBorderColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        border.clearColor()
        border.gradient.startColor = startColor
        border.gradient.centerColor = centerColor
        border.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置普通边框渐变中间点位置。
     *
     * @param position 渐变中间点位置，建议范围 0f ~ 1f。
     */
    fun setBorderGradientCenterPosition(position: Float) {
        val safePosition = position.coerceIn(0f, 1f)

        if (border.gradient.centerPosition != safePosition) {
            border.gradient.centerPosition = safePosition
            invalidateSelf()
        }
    }


    /**
     * 设置纯色边框。
     */
    fun setBorderColor(@ColorInt color: Int) {
        border.clearColor()
        border.color = color
        border.gradient.angle = 0f

        invalidateSelf()
    }

    /**
     * 设置边框渐变角度。
     */
    fun setBorderGradientAngle(angle: Float) {
        if (border.gradient.angle != angle) {
            border.gradient.angle = angle
            invalidateSelf()
        }
    }

    /**
     * 清除普通边框颜色。
     *
     * 注意：
     *
     * 这里只清除颜色，不清除 border.width。
     */
    fun clearBorderColor() {
        border.clearColor()
        invalidateSelf()
    }

    // ------------------------------------------------------------------------
    // 动态设置圆角方法
    // ------------------------------------------------------------------------

    /**
     * 设置统一圆角。
     */
    fun setCornerRadius(radius: Float) {
        val changed = corner.radius != radius ||
                corner.radii == null ||
                corner.radii?.any { it != radius } == true

        if (changed) {
            corner.radius = radius
            corner.radii = if (radius > 0f) {
                FloatArray(8) { radius }
            } else {
                null
            }

            invalidateSelf()
        }
    }

    /**
     * 设置四个角不同圆角。
     */
    fun setCornerRadius(
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float
    ) {
        val old = corner.radii

        val changed = old == null ||
                old[0] != topLeft ||
                old[2] != topRight ||
                old[4] != bottomRight ||
                old[6] != bottomLeft

        if (changed) {
            corner.radius = 0f
            corner.radii = if (
                topLeft != 0f ||
                topRight != 0f ||
                bottomRight != 0f ||
                bottomLeft != 0f
            ) {
                floatArrayOf(
                    topLeft, topLeft,
                    topRight, topRight,
                    bottomRight, bottomRight,
                    bottomLeft, bottomLeft
                )
            } else {
                null
            }

            invalidateSelf()
        }
    }

    /**
     * 清除圆角。
     */
    fun clearCornerRadius() {
        corner.radius = 0f
        corner.radii = null

        invalidateSelf()
    }

    // ------------------------------------------------------------------------
    // 动态设置阴影方法
    // ------------------------------------------------------------------------

    /**
     * 设置阴影。
     *
     * 注意：
     *
     * 设置阴影后，需要宿主 View 使用软件层：
     *
     * view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
     *
     * 这个操作建议放在 GStyler 中处理。
     *
     * @param color 阴影颜色。
     * @param radius 阴影模糊半径，单位 px。
     * @param dx X 方向偏移，单位 px。
     * @param dy Y 方向偏移，单位 px。
     */
    fun setShadow(
        @ColorInt color: Int,
        radius: Float,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        shadow.color = color
        shadow.radius = radius
        shadow.dx = dx
        shadow.dy = dy

        invalidateSelf()
    }

    /**
     * 清除阴影。
     */
    fun clearShadow() {
        shadow.clear()
        invalidateSelf()
    }

    // ------------------------------------------------------------------------
    // 动态设置 selected 选中态背景方法
    // ------------------------------------------------------------------------

    /**
     * 设置 selected 选中态纯色背景。
     *
     * 内部使用 ColorDrawable 承载，
     * 和 XML 中写法一致：
     *
     * app:g_selected_background="#FF5722"
     */
    fun setSelectedBackgroundColor(@ColorInt color: Int) {
        selectedBackground.clear()
        selectedBackground.drawable = ColorDrawable(color)

        syncChildDrawableState()
        invalidateSelf()
    }

    /**
     * 设置 selected 选中态 Drawable / 图片背景。
     *
     * 等价于 XML：
     *
     * app:g_selected_background="@drawable/bg_selected"
     */
    fun setSelectedBackgroundDrawable(drawable: Drawable?) {
        selectedBackground.clear()
        selectedBackground.drawable = drawable?.mutate()

        syncChildDrawableState()
        invalidateSelf()
    }

    /**
     * 设置 selected 选中态两色渐变背景。
     */
    fun setSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        selectedBackground.clear()
        selectedBackground.gradient.startColor = startColor
        selectedBackground.gradient.centerColor = BD_UNSET
        selectedBackground.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置 selected 选中态三色渐变背景。
     */
    fun setSelectedBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        selectedBackground.clear()
        selectedBackground.gradient.startColor = startColor
        selectedBackground.gradient.centerColor = centerColor
        selectedBackground.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置 selected 选中态背景渐变角度。
     */
    fun setSelectedBackgroundGradientAngle(angle: Float) {
        if (selectedBackground.gradient.angle != angle) {
            selectedBackground.gradient.angle = angle
            invalidateSelf()
        }
    }

    /**
     * 设置 selected 选中态背景渐变中间点位置。
     *
     * @param position 渐变中间点位置，建议范围 0f ~ 1f。
     */
    fun setSelectedBackgroundGradientCenterPosition(position: Float) {
        val safePosition = position.coerceIn(0f, 1f)

        if (selectedBackground.gradient.centerPosition != safePosition) {
            selectedBackground.gradient.centerPosition = safePosition
            invalidateSelf()
        }
    }

    /**
     * 清除 selected 选中态背景。
     *
     * 清除后，selected 状态下会继续使用普通背景。
     */
    fun clearSelectedBackground() {
        selectedBackground.clear()
        invalidateSelf()
    }

    // ------------------------------------------------------------------------
    // 动态设置 selected 选中态边框方法
    // ------------------------------------------------------------------------

    /**
     * 设置 selected 选中态纯色边框。
     */
    fun setSelectedBorderColor(@ColorInt color: Int) {
        selectedBorder.clearColor()
        selectedBorder.color = color
        selectedBorder.gradient.angle = 0f

        invalidateSelf()
    }

    /**
     * 设置 selected 选中态两色渐变边框。
     */
    fun setSelectedBorderColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        selectedBorder.clearColor()
        selectedBorder.gradient.startColor = startColor
        selectedBorder.gradient.centerColor = BD_UNSET
        selectedBorder.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置 selected 选中态三色渐变边框。
     */
    fun setSelectedBorderColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        selectedBorder.clearColor()
        selectedBorder.gradient.startColor = startColor
        selectedBorder.gradient.centerColor = centerColor
        selectedBorder.gradient.endColor = endColor

        invalidateSelf()
    }

    /**
     * 设置 selected 选中态边框渐变角度。
     */
    fun setSelectedBorderGradientAngle(angle: Float) {
        if (selectedBorder.gradient.angle != angle) {
            selectedBorder.gradient.angle = angle
            invalidateSelf()
        }
    }

    /**
     * 设置 selected 选中态边框渐变中间点位置。
     *
     * @param position 渐变中间点位置，建议范围 0f ~ 1f。
     */
    fun setSelectedBorderGradientCenterPosition(position: Float) {
        val safePosition = position.coerceIn(0f, 1f)

        if (selectedBorder.gradient.centerPosition != safePosition) {
            selectedBorder.gradient.centerPosition = safePosition
            invalidateSelf()
        }
    }

    /**
     * 清除 selected 选中态边框。
     *
     * 清除后，selected 状态下会继续使用普通边框。
     */
    fun clearSelectedBorder() {
        selectedBorder.clearColor()
        invalidateSelf()
    }
}