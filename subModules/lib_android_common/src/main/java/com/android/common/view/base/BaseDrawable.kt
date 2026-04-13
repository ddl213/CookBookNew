package com.android.common.view.base

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import com.android.common.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BaseDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val path = Path()
    private val rectF = RectF()
    private val borderRectF = RectF()

    // --- 背景属性 ---
    @ColorInt
    private var backgroundColor: Int = Color.TRANSPARENT

    @ColorInt
    private var backgroundStartColor: Int = Color.TRANSPARENT

    @ColorInt
    private var backgroundCenterColor: Int = Color.TRANSPARENT

    @ColorInt
    private var backgroundEndColor: Int = Color.TRANSPARENT

    private var backgroundGradientAngle: Float = 0f

    // 系统背景Drawable（通过android:background设置）
    private var systemBackground: Drawable? = null

    // --- 边框属性 ---
    private var borderWidth: Float = 0f

    @ColorInt
    private var borderColor: Int = Color.TRANSPARENT

    @ColorInt
    private var borderStartColor: Int = Color.TRANSPARENT

    @ColorInt
    private var borderCenterColor: Int = Color.TRANSPARENT

    @ColorInt
    private var borderEndColor: Int = Color.TRANSPARENT

    private var borderGradientAngle: Float = 0f

    // --- 圆角属性 ---
    private var cornerRadius: Float = 0f
    private var cornerRadii: FloatArray? = null

    fun applyAttributes(context: Context, attrs: AttributeSet?) {
        if (attrs == null) return

        val style = context.obtainStyledAttributes(
            attrs,
            R.styleable.CommonGradientAttrs,
            android.R.attr.background,
            0
        )

        try {
            // 1. 获取系统背景（android:background）
            systemBackground = style.getDrawable(R.styleable.CommonGradientAttrs_android_background)

            // 2. 检查是否有自定义渐变属性
            val hasBgStartColor = style.hasValue(R.styleable.CommonGradientAttrs_start_color)
            val hasBgCenterColor = style.hasValue(R.styleable.CommonGradientAttrs_center_color)
            val hasBgEndColor = style.hasValue(R.styleable.CommonGradientAttrs_end_color)

            if (hasBgStartColor || hasBgCenterColor || hasBgEndColor) {
                // 设置了自定义渐变背景属性，覆盖系统背景
                backgroundStartColor = style.getColor(
                    R.styleable.CommonGradientAttrs_start_color,
                    Color.TRANSPARENT
                )
                backgroundCenterColor = style.getColor(
                    R.styleable.CommonGradientAttrs_center_color,
                    Color.TRANSPARENT
                )
                backgroundEndColor = style.getColor(
                    R.styleable.CommonGradientAttrs_end_color,
                    Color.TRANSPARENT
                )
                backgroundGradientAngle = style.getFloat(
                    R.styleable.CommonGradientAttrs_g_angle,
                    0f
                )
                backgroundColor = Color.TRANSPARENT
            } else if (systemBackground == null) {
                // 没有渐变属性且没有系统背景Drawable，检查系统背景色
                val systemBgColor = style.getColor(R.styleable.CommonGradientAttrs_android_background, Color.TRANSPARENT)
                if (systemBgColor != Color.TRANSPARENT) {
                    backgroundColor = systemBgColor
                    backgroundStartColor = systemBgColor
                    backgroundCenterColor = systemBgColor
                    backgroundEndColor = systemBgColor
                }
            }

            // --- 边框属性处理 ---
            borderWidth = style.getDimension(R.styleable.CommonGradientAttrs_border, 0f)

            val hasBorderColor = style.hasValue(R.styleable.CommonGradientAttrs_border_color)
            val hasBorderStartColor = style.hasValue(R.styleable.CommonGradientAttrs_border_start_color)
            val hasBorderCenterColor = style.hasValue(R.styleable.CommonGradientAttrs_border_center_color)
            val hasBorderEndColor = style.hasValue(R.styleable.CommonGradientAttrs_border_end_color)

            if (hasBorderColor) {
                borderColor = style.getColor(
                    R.styleable.CommonGradientAttrs_border_color,
                    Color.TRANSPARENT
                )
                borderStartColor = borderColor
                borderCenterColor = borderColor
                borderEndColor = borderColor
                borderGradientAngle = 0f
            } else if (hasBorderStartColor || hasBorderCenterColor || hasBorderEndColor) {
                borderStartColor = style.getColor(
                    R.styleable.CommonGradientAttrs_border_start_color,
                    Color.TRANSPARENT
                )
                borderCenterColor = style.getColor(
                    R.styleable.CommonGradientAttrs_border_center_color,
                    Color.TRANSPARENT
                )
                borderEndColor = style.getColor(
                    R.styleable.CommonGradientAttrs_border_end_color,
                    Color.TRANSPARENT
                )
                borderGradientAngle = style.getFloat(
                    R.styleable.CommonGradientAttrs_border_color_angle,
                    0f
                )
                borderColor = Color.TRANSPARENT
            } else {
                borderColor = Color.TRANSPARENT
            }

            // --- 圆角属性处理 ---
            cornerRadius = style.getDimension(R.styleable.CommonGradientAttrs_radius, 0f)
            val topLeftRadius = style.getDimension(
                R.styleable.CommonGradientAttrs_radius_top_left,
                cornerRadius
            )
            val topRightRadius = style.getDimension(
                R.styleable.CommonGradientAttrs_radius_top_right,
                cornerRadius
            )
            val bottomRightRadius = style.getDimension(
                R.styleable.CommonGradientAttrs_radius_bottom_right,
                cornerRadius
            )
            val bottomLeftRadius = style.getDimension(
                R.styleable.CommonGradientAttrs_radius_bottom_left,
                cornerRadius
            )


            cornerRadii = when {
                topLeftRadius != 0f || topRightRadius != 0f ||
                        bottomRightRadius != 0f || bottomLeftRadius != 0f -> {
                    floatArrayOf(
                        topLeftRadius, topLeftRadius,
                        topRightRadius, topRightRadius,
                        bottomRightRadius, bottomRightRadius,
                        bottomLeftRadius, bottomLeftRadius
                    )
                }
                cornerRadius != 0f -> {
                    FloatArray(8) { cornerRadius }
                }
                else -> null
            }
        } finally {
            style.recycle()
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        rectF.set(bounds)

        // 1. 绘制背景（考虑圆角）
        drawBackground(canvas)

        // 2. 绘制边框（考虑圆角）
        drawBorder(canvas)
    }


    private fun drawBackground(canvas: Canvas) {
        // 1. 自定义渐变背景（最高优先级）
        val hasCustomGradient = backgroundStartColor != Color.TRANSPARENT ||
                backgroundCenterColor != Color.TRANSPARENT ||
                backgroundEndColor != Color.TRANSPARENT

        // 2. 纯色背景（次优先级）
        val hasSolidBackground = backgroundColor != Color.TRANSPARENT

        // 3. 系统背景Drawable（最低优先级）
        val hasSystemDrawable = systemBackground != null

        // 创建背景绘制闭包
        val drawBackground: (Canvas) -> Unit = { c ->
            when {
                hasCustomGradient || hasSolidBackground -> {
                    val bgColors = if (hasCustomGradient) {
                        if (backgroundCenterColor != Color.TRANSPARENT) {
                            intArrayOf(backgroundStartColor, backgroundCenterColor, backgroundEndColor)
                        } else {
                            intArrayOf(backgroundStartColor, backgroundEndColor)
                        }
                    } else {
                        intArrayOf(backgroundColor, backgroundColor)
                    }

                    paint.shader = createLinearGradient(bgColors, backgroundGradientAngle, rectF)
                    c.drawRect(rectF, paint)
                }
                hasSystemDrawable -> {
                    systemBackground!!.bounds = bounds
                    systemBackground!!.draw(c)
                }
                else -> {
                    // 默认透明背景
                }
            }
        }

        // 应用圆角或直接绘制
        if (cornerRadii != null) {
            path.reset()
            path.addRoundRect(rectF, cornerRadii!!, Path.Direction.CW)
            canvas.save()
            // 确保所有背景类型（包括系统背景）都应用相同的圆角效果
            //强制所有背景内容（包括系统背景）都在圆角区域内绘制
            canvas.clipPath(path)
            drawBackground(canvas)
            // 如果有圆角，恢复画布
            canvas.restore()
        } else {
            drawBackground(canvas)
        }
    }

    private fun drawBorder(canvas: Canvas) {
        if (borderWidth <= 0) return

        borderPaint.strokeWidth = borderWidth

        val borderColors = if (borderStartColor != Color.TRANSPARENT ||
            borderCenterColor != Color.TRANSPARENT ||
            borderEndColor != Color.TRANSPARENT
        ) {
            if (borderCenterColor != Color.TRANSPARENT) {
                intArrayOf(borderStartColor, borderCenterColor, borderEndColor)
            } else {
                intArrayOf(borderStartColor, borderEndColor)
            }
        } else {
            intArrayOf(borderColor, borderColor)
        }

        borderPaint.shader = createLinearGradient(borderColors, borderGradientAngle, rectF)

        borderRectF.set(rectF)
        val halfBorder = borderWidth / 2f
        borderRectF.inset(halfBorder, halfBorder)

        if (cornerRadii != null) {
            val borderCornerRadii = FloatArray(8) { i ->
                0f.coerceAtLeast(cornerRadii!![i] - halfBorder)
            }
            path.reset()
            path.addRoundRect(borderRectF, borderCornerRadii, Path.Direction.CW)
            canvas.drawPath(path, borderPaint)
        } else {
            canvas.drawRect(borderRectF, borderPaint)
        }
    }



    private fun createLinearGradient(@ColorInt colors: IntArray, angle: Float, bounds: RectF): LinearGradient {
        val radians = Math.toRadians(angle.toDouble()).toFloat()
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val maxLen = hypot((bounds.width() / 2f).toDouble(), (bounds.height() / 2f).toDouble()).toFloat()

        val x0 = centerX - cos(radians.toDouble()).toFloat() * maxLen
        val y0 = centerY - sin(radians.toDouble()).toFloat() * maxLen
        val x1 = centerX + cos(radians.toDouble()).toFloat() * maxLen
        val y1 = centerY + sin(radians.toDouble()).toFloat() * maxLen

        val positions = if (colors.size == 3) floatArrayOf(0f, 0.5f, 1f) else null
        return LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        borderPaint.alpha = alpha
        systemBackground?.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity(): Int {
        return if (paint.alpha == 255 && systemBackground == null) {
            PixelFormat.OPAQUE
        } else {
            PixelFormat.TRANSLUCENT
        }
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        systemBackground?.colorFilter = colorFilter
        invalidateSelf()
    }

    // ===== 动态设置方法 =====
    // 背景设置方法（会覆盖所有之前的背景设置）

    fun setBackgroundColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int = Color.TRANSPARENT,
        @ColorInt endColor: Int
    ) {
        // 清除系统背景
        systemBackground = null

        // 设置渐变背景
        backgroundStartColor = startColor
        backgroundCenterColor = centerColor
        backgroundEndColor = endColor
        backgroundColor = Color.TRANSPARENT

        invalidateSelf()
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        // 清除系统背景
        systemBackground = null

        // 设置纯色背景
        backgroundColor = color
        backgroundStartColor = color
        backgroundCenterColor = color
        backgroundEndColor = color
        backgroundGradientAngle = 0f

        invalidateSelf()
    }

    fun setBackgroundDrawable(drawable: Drawable?) {
        // 清除所有颜色背景
        backgroundColor = Color.TRANSPARENT
        backgroundStartColor = Color.TRANSPARENT
        backgroundCenterColor = Color.TRANSPARENT
        backgroundEndColor = Color.TRANSPARENT
        backgroundGradientAngle = 0f

        // 设置Drawable背景
        systemBackground = drawable
        invalidateSelf()
    }

    fun setBackgroundGradientAngle(angle: Float) {
        if (backgroundGradientAngle != angle) {
            backgroundGradientAngle = angle
            invalidateSelf()
        }
    }

    // 边框和圆角设置方法保持不变...
    fun setBorderWidth(width: Float) {
        if (borderWidth != width) {
            borderWidth = width
            invalidateSelf()
        }
    }

    fun setBorderColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int = Color.TRANSPARENT,
        @ColorInt endColor: Int
    ) {
        if (borderStartColor != startColor ||
            borderCenterColor != centerColor ||
            borderEndColor != endColor ||
            borderColor != Color.TRANSPARENT
        ) {
            borderStartColor = startColor
            borderCenterColor = centerColor
            borderEndColor = endColor
            borderColor = Color.TRANSPARENT
            invalidateSelf()
        }
    }

    fun setBorderColor(@ColorInt color: Int) {
        if (borderColor != color || borderStartColor != color) {
            borderColor = color
            borderStartColor = color
            borderCenterColor = color
            borderEndColor = color
            borderGradientAngle = 0f
            invalidateSelf()
        }
    }

    fun setBorderGradientAngle(angle: Float) {
        if (borderGradientAngle != angle) {
            borderGradientAngle = angle
            invalidateSelf()
        }
    }

    fun setCornerRadius(radius: Float) {
        if (cornerRadius != radius || cornerRadii != null) {
            cornerRadius = radius
            cornerRadii = FloatArray(8) { radius }
            invalidateSelf()
        }
    }

    fun setCornerRadius(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        val changed = cornerRadii == null ||
                cornerRadii!![0] != topLeft || cornerRadii!![2] != topRight ||
                cornerRadii!![4] != bottomRight || cornerRadii!![6] != bottomLeft

        if (changed) {
            cornerRadius = 0f
            cornerRadii = floatArrayOf(
                topLeft, topLeft,
                topRight, topRight,
                bottomRight, bottomRight,
                bottomLeft, bottomLeft
            )
            invalidateSelf()
        }
    }
}