package com.android.common.view.base

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.android.common.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * `GTextView` (Gradient TextView) 是一个扩展 `AppCompatTextView` 的自定义 `TextView`。
 * 它通过内部持有 `GStyler` 实例来获得与 `CView` 相同的背景、边框和圆角功能。
 * 此外，`GTextView` 专门处理**文本的渐变或 Drawable 填充**。
 *
 * 自身类中公共样式方法极少，主要集中于文本渐变逻辑。
 */
class GTextView : AppCompatTextView {

    // --- 字体渐变相关属性（GTextView独有） ---
    private var textGradientShader: Shader? = null // 文本渐变或填充的 Shader

    @ColorInt
    private var textStartColor: Int = Color.TRANSPARENT // 文本渐变的起始颜色

    @ColorInt
    private var textCenterColor: Int = Color.TRANSPARENT // 文本渐变的中心颜色（用于三色渐变）

    @ColorInt
    private var textEndColor: Int = Color.TRANSPARENT // 文本渐变的结束颜色

    private var textGradientAngle: Float = 0f // 文本渐变的角度（0-360度）

    private var textDrawable: Drawable? = null // 可选的文本填充 Drawable，如果设置，则覆盖颜色渐变

    // 持有 `GStyler` 实例，负责处理来自 `CommonGradientAttrs` 的背景、边框和圆角属性
    private val styler: GStyler

    private var currentWidth: Int = 0 // 当前 `View` 的宽度，用于 Shader 计算
    private var currentHeight: Int = 0 // 当前 `View` 的高度，用于 Shader 计算

    // 构造函数链，最终调用三参数构造函数
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    /**
     * `GTextView` 的主构造函数，用于从 XML 解析属性。
     *
     * @param context `Context` 实例。
     * @param attrs `AttributeSet` 实例，包含从 XML 读取的属性。
     * @param defStyleAttr 默认样式属性。
     */
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        // 初始化 GStyler，让它处理 CommonGradientAttrs 定义的背景、边框和圆角样式
        styler = GStyler(this, context, attrs)

        // 处理 GTextView 独有的字体渐变属性
        if (attrs != null) {
            // 获取 `GTextView` 定义的属性 TypedArray
            val style = context.obtainStyledAttributes(attrs, R.styleable.GTextView, 0, 0)
            try {
                // 文本填充的优先级：`gtv_textDrawable` (Drawable填充) > 颜色渐变 (gtv_textStartColor/gtv_textCenterColor/gtv_textEndColor)
                val textDrawableRef = style.getResourceId(R.styleable.GTextView_text_drawable, 0)
                if (textDrawableRef != 0) {
                    // 如果设置了 Drawable 作为文本填充，则加载该 Drawable
                    textDrawable = ResourcesCompat.getDrawable(context.resources,textDrawableRef, context.theme)
                    // 同时清除所有文本颜色/渐变属性，避免冲突
                    textStartColor = Color.TRANSPARENT
                    textCenterColor = Color.TRANSPARENT
                    textEndColor = Color.TRANSPARENT
                    textGradientAngle = 0f
                } else {
                    // 如果没有设置 Drawable 文本填充，则处理颜色渐变属性
                    textDrawable = null // 确保 Drawable 文本填充为 null

                    // 检查是否存在文本渐变颜色属性
                    val hasTextStartColor = style.hasValue(R.styleable.GTextView_text_start_color)
                    val hasTextCenterColor = style.hasValue(R.styleable.GTextView_text_center_color)
                    val hasTextEndColor = style.hasValue(R.styleable.GTextView_text_end_color)

                    if (hasTextStartColor || hasTextCenterColor || hasTextEndColor) {
                        // 如果存在任何渐变颜色属性，则读取它们
                        textStartColor = style.getColor(R.styleable.GTextView_text_start_color, Color.TRANSPARENT)
                        textCenterColor = style.getColor(R.styleable.GTextView_text_center_color, Color.TRANSPARENT)
                        textEndColor = style.getColor(R.styleable.GTextView_text_end_color, Color.TRANSPARENT)
                        textGradientAngle = style.getFloat(R.styleable.GTextView_text_color_angle, 0f)
                    } else {
                        // 如果没有设置任何文本渐变相关属性，则默认是透明的
                        textStartColor = Color.TRANSPARENT
                        textCenterColor = Color.TRANSPARENT
                        textEndColor = Color.TRANSPARENT
                        textGradientAngle = 0f
                    }
                }
            } finally {
                // 确保 TypedArray 被回收，避免内存泄露
                style.recycle()
            }
        }
    }

    /**
     * 当 `View` 的尺寸改变时调用此方法。
     * 在这里更新文本渐变 Shader，以适应新的尺寸。
     *
     * @param w 新的宽度。
     * @param h 新的高度。
     * @param oldw 旧的宽度。
     * @param oldh 旧的高度。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 只有当尺寸确实改变时才更新 Shader
        if (w != currentWidth || h != currentHeight) {
            currentWidth = w
            currentHeight = h
            updateTextShader() // 更新文本 Shader
        }
    }


    /**
     * 根据当前设置的文本渐变属性或 Drawable 更新 `TextView` 的 `paint.shader`。
     * 这个方法会在尺寸变化或文本渐变属性改变时调用。
     */
    private fun updateTextShader() {
        // 如果宽度或高度为0，则不创建 Shader，直接使用默认文本颜色
        if (currentWidth == 0 || currentHeight == 0) {
            paint.shader = null
            paint.color = currentTextColor // 恢复默认文本颜色
            return
        }

        // 优先处理 Drawable 文本填充
        if (textDrawable != null) {
            // 将 Drawable 转换为 Bitmap，并创建 BitmapShader
            val bitmap = textDrawable!!.toBitmap(currentWidth, currentHeight, Bitmap.Config.ARGB_8888) // 使用 ARGB_8888 确保透明度
            textGradientShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            paint.shader = textGradientShader // 将 Shader 应用到 TextView 的文本画笔上
        } else {
            // 如果没有 Drawable 文本填充，则处理颜色渐变
            val textColors = if (textStartColor != Color.TRANSPARENT || textCenterColor != Color.TRANSPARENT || textEndColor != Color.TRANSPARENT) {
                // 根据是否设置了中心颜色来判断是两色渐变还是三色渐变
                if (textCenterColor != Color.TRANSPARENT && textStartColor != textCenterColor && textCenterColor != textEndColor) {
                    intArrayOf(textStartColor, textCenterColor, textEndColor) // 三色渐变
                } else {
                    intArrayOf(textStartColor, textEndColor) // 默认两色渐变
                }
            } else {
                null // 透明，表示没有渐变
            }

            if (textColors != null) {
                // 如果有渐变颜色，则创建线性渐变并设置给文本画笔的 Shader
                textGradientShader = createLinearGradientForText(textColors, textGradientAngle, currentWidth, currentHeight)
                paint.shader = textGradientShader
            } else {
                // 没有渐变颜色，清除 Shader，并恢复 `TextView` 默认的文本颜色
                paint.shader = null
                paint.color = currentTextColor // `currentTextColor` 会返回 `android:textColor` 设置的颜色，如果没有则返回默认颜色
            }
        }
        invalidate() // 请求 `View` 重绘，使 Shader 生效
    }

    /**
     * 根据颜色数组、角度、宽度和高度为文本创建 `LinearGradient`。
     * 逻辑与 `BaseDrawable` 中的 `createLinearGradient` 类似。
     *
     * @param colors 渐变颜色数组。
     * @param angle 渐变角度（0-360度）。
     * @param width `View` 的宽度。
     * @param height `View` 的高度。
     * @return 配置好的 `LinearGradient` 对象。
     */
    private fun createLinearGradientForText(colors: IntArray, angle: Float, width: Int, height: Int): LinearGradient {
        val x0: Float
        val y0: Float
        val x1: Float
        val y1: Float

        val radians = Math.toRadians(angle.toDouble()).toFloat() // 角度转换为弧度
        val centerX = width / 2f
        val centerY = height / 2f
        // 计算从中心到矩形对角线最远点的距离，作为渐变线的最大长度
        val maxLen = hypot((width / 2f).toDouble(), (height / 2f).toDouble()).toFloat()

        // 根据角度计算渐变线的起始和结束坐标
        x0 = centerX - cos(radians.toDouble()).toFloat() * maxLen
        y0 = centerY - sin(radians.toDouble()).toFloat() * maxLen
        x1 = centerX + cos(radians.toDouble()).toFloat() * maxLen
        y1 = centerY + sin(radians.toDouble()).toFloat() * maxLen

        // 如果是三色渐变，则指定颜色在渐变线上的相对位置
        val positions: FloatArray? = if (colors.size == 3) floatArrayOf(0f, 0.5f, 1f) else null

        // 创建并返回线性渐变对象
        return LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
    }

    // --- GTextView 独有的 setter 方法 ---

    /**
     * 动态设置文本填充的 Drawable。设置后，文本颜色渐变将失效。
     *
     * @param drawable 用于文本填充的 Drawable。
     */
    fun setTextDrawable(drawable: Drawable?) {
        // 只有当新的 Drawable 与当前的不同时才更新
        if (this.textDrawable != drawable) {
            this.textDrawable = drawable
            // 清除所有文本颜色渐变属性
            textStartColor = Color.TRANSPARENT
            textCenterColor = Color.TRANSPARENT
            textEndColor = Color.TRANSPARENT
            textGradientAngle = 0f
            updateTextShader() // 更新 Shader
        }
    }

    /**
     * 动态设置文本的渐变颜色。支持两色或三色渐变。
     *
     * @param startColor 渐变起始颜色。
     * @param centerColor 渐变中心颜色 (可选，默认为透明，表示两色渐变)。
     * @param endColor 渐变结束颜色。
     */
    fun setTextColors(@ColorInt startColor: Int, @ColorInt centerColor: Int = Color.TRANSPARENT, @ColorInt endColor: Int) {
        // 只有当没有 Drawable 文本填充且颜色组合发生变化时才更新
        if (textDrawable == null && (
                    this.textStartColor != startColor ||
                            this.textCenterColor != centerColor ||
                            this.textEndColor != endColor
                    )
        ) {
            this.textStartColor = startColor
            this.textCenterColor = centerColor
            this.textEndColor = endColor
            updateTextShader() // 更新 Shader
        }
    }

    /**
     * 动态设置文本渐变的角度。
     *
     * @param angle 渐变角度（0-360度）。
     */
    fun setTextGradientAngle(angle: Float) {
        // 只有当角度不同时才更新
        if (textGradientAngle != angle) {
            this.textGradientAngle = angle
            updateTextShader() // 更新 Shader
        }
    }

    /**
     * 获取此 `GTextView` 关联的 `GStyler` 实例。
     * 允许外部通过 `GStyler` 访问背景、边框和圆角设置。
     *
     * @return `GStyler` 实例。
     */
    fun getStyler(): GStyler {
        return styler
    }
}