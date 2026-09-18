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
 * GTextView
 *
 * 一个支持通用背景能力和文本渐变能力的 TextView。
 *
 * 背景、边框、圆角、阴影等能力由 GStyler + BaseDrawable 提供。
 *
 * GTextView 自身额外支持：
 *
 * 1. 文本两色渐变
 * 2. 文本三色渐变
 * 3. 文本渐变角度
 * 4. Drawable / 图片填充文本
 * 5. selected 选中态纯色字体颜色
 * 6. selected 选中态渐变字体颜色
 *
 * selected 字体颜色优先级：
 *
 * selected 文本渐变 > selected 纯色字体 > text_drawable > 普通文本渐变 > android:textColor
 */
class GTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr), GStyleable {

    override val gStyler: GStyler = GStyler(this, context, attrs)

    /**
     * 表示字体颜色属性未设置。
     *
     * 不能使用 Color.TRANSPARENT 作为未设置值，
     * 因为透明色本身也是合法颜色。
     */
    private val TEXT_COLOR_UNSET = Int.MIN_VALUE

    // ------------------------------------------------------------------------
    // 普通文本渐变 / Drawable 填充属性
    // ------------------------------------------------------------------------

    /**
     * 文本渐变或 Drawable 填充使用的 Shader。
     */
    private var textGradientShader: Shader? = null

    /**
     * 普通文本渐变起始颜色。
     */
    @ColorInt
    private var textStartColor: Int = Color.TRANSPARENT

    /**
     * 普通文本渐变中心颜色。
     *
     * 如果设置该值，则可以形成三色渐变。
     */
    @ColorInt
    private var textCenterColor: Int = Color.TRANSPARENT

    /**
     * 普通文本渐变结束颜色。
     */
    @ColorInt
    private var textEndColor: Int = Color.TRANSPARENT

    /**
     * 普通文本渐变角度。
     *
     * 0 表示从左到右。
     */
    private var textGradientAngle: Float = 0f

    /**
     * 文本填充 Drawable。
     *
     * 如果设置了 textDrawable，则优先使用 Drawable 填充文本，
     * 普通文本颜色渐变会失效。
     */
    private var textDrawable: Drawable? = null

    // ------------------------------------------------------------------------
    // selected 选中态文本颜色 / 渐变属性
    // ------------------------------------------------------------------------

    /**
     * selected 选中态纯色字体颜色。
     *
     * 对应 XML：
     *
     * app:g_selected_text_color="#FF0000"
     */
    @ColorInt
    private var selectedTextColor: Int = TEXT_COLOR_UNSET

    /**
     * 是否设置了 selected 选中态文本渐变。
     *
     * 只要 XML 中设置了：
     *
     * 1. app:g_selected_text_start_color
     * 2. app:g_selected_text_center_color
     * 3. app:g_selected_text_end_color
     *
     * 任意一个，就认为有 selected 文本渐变。
     */
    private var hasSelectedTextGradient: Boolean = false

    /**
     * selected 选中态文本渐变起始颜色。
     */
    @ColorInt
    private var selectedTextStartColor: Int = Color.TRANSPARENT

    /**
     * selected 选中态文本渐变中心颜色。
     */
    @ColorInt
    private var selectedTextCenterColor: Int = Color.TRANSPARENT

    /**
     * selected 选中态文本渐变结束颜色。
     */
    @ColorInt
    private var selectedTextEndColor: Int = Color.TRANSPARENT

    /**
     * selected 选中态文本渐变角度。
     */
    private var selectedTextGradientAngle: Float = 0f

    // ------------------------------------------------------------------------
    // 尺寸缓存
    // ------------------------------------------------------------------------

    /**
     * 当前 View 宽度。
     *
     * 用于创建文本 Shader。
     */
    private var currentWidth: Int = 0

    /**
     * 当前 View 高度。
     *
     * 用于创建文本 Shader。
     */
    private var currentHeight: Int = 0

    /**
     * XML / 默认状态下的普通字体颜色。
     *
     * 用于 selected 状态结束后恢复。
     */
    @ColorInt
    private var normalTextColor: Int = Color.TRANSPARENT

    /**
     * 内部切换字体颜色标记。
     *
     * 避免 super.setTextColor() 触发 drawableStateChanged() 后造成重复递归。
     */
    private var changingTextColorInternally: Boolean = false

    init {
        parseTextAttributes(context, attrs)

        /**
         * AppCompatTextView 在 super 构造阶段已经解析了 android:textColor，
         * 所以这里可以拿到 XML 中设置的普通字体颜色。
         */
        normalTextColor = currentTextColor
    }

    /**
     * 解析 GTextView 独有属性。
     */
    private fun parseTextAttributes(
        context: Context,
        attrs: AttributeSet?
    ) {
        if (attrs == null) return

        val style = context.obtainStyledAttributes(
            attrs,
            R.styleable.GTextView,
            0,
            0
        )

        try {
            // ----------------------------------------------------------------
            // 1. 解析 selected 选中态纯色字体颜色
            // ----------------------------------------------------------------

            selectedTextColor = style.getColor(
                R.styleable.GTextView_g_selected_text_color,
                TEXT_COLOR_UNSET
            )

            // ----------------------------------------------------------------
            // 2. 解析 selected 选中态字体渐变
            // ----------------------------------------------------------------

            val hasSelectedTextStartColor = style.hasValue(
                R.styleable.GTextView_g_selected_text_start_color
            )
            val hasSelectedTextCenterColor = style.hasValue(
                R.styleable.GTextView_g_selected_text_center_color
            )
            val hasSelectedTextEndColor = style.hasValue(
                R.styleable.GTextView_g_selected_text_end_color
            )

            hasSelectedTextGradient =
                hasSelectedTextStartColor ||
                        hasSelectedTextCenterColor ||
                        hasSelectedTextEndColor

            if (hasSelectedTextGradient) {
                selectedTextStartColor = style.getColor(
                    R.styleable.GTextView_g_selected_text_start_color,
                    Color.TRANSPARENT
                )
                selectedTextCenterColor = style.getColor(
                    R.styleable.GTextView_g_selected_text_center_color,
                    Color.TRANSPARENT
                )
                selectedTextEndColor = style.getColor(
                    R.styleable.GTextView_g_selected_text_end_color,
                    Color.TRANSPARENT
                )
                selectedTextGradientAngle = style.getFloat(
                    R.styleable.GTextView_g_selected_text_color_angle,
                    0f
                )
            } else {
                selectedTextStartColor = Color.TRANSPARENT
                selectedTextCenterColor = Color.TRANSPARENT
                selectedTextEndColor = Color.TRANSPARENT
                selectedTextGradientAngle = 0f
            }

            // ----------------------------------------------------------------
            // 3. 解析文本 Drawable 填充
            // ----------------------------------------------------------------

            val textDrawableRef = style.getResourceId(
                R.styleable.GTextView_text_drawable,
                0
            )

            if (textDrawableRef != 0) {
                /**
                 * 如果设置了 Drawable 作为文本填充，
                 * 则 Drawable 填充优先于普通文本颜色渐变。
                 */
                textDrawable = ResourcesCompat.getDrawable(
                    context.resources,
                    textDrawableRef,
                    context.theme
                )

                /**
                 * 清除普通文本渐变属性，避免冲突。
                 */
                textStartColor = Color.TRANSPARENT
                textCenterColor = Color.TRANSPARENT
                textEndColor = Color.TRANSPARENT
                textGradientAngle = 0f
            } else {
                textDrawable = null

                // ------------------------------------------------------------
                // 4. 解析普通文本渐变颜色
                // ------------------------------------------------------------

                val hasTextStartColor = style.hasValue(
                    R.styleable.GTextView_text_start_color
                )
                val hasTextCenterColor = style.hasValue(
                    R.styleable.GTextView_text_center_color
                )
                val hasTextEndColor = style.hasValue(
                    R.styleable.GTextView_text_end_color
                )

                if (
                    hasTextStartColor ||
                    hasTextCenterColor ||
                    hasTextEndColor
                ) {
                    textStartColor = style.getColor(
                        R.styleable.GTextView_text_start_color,
                        Color.TRANSPARENT
                    )
                    textCenterColor = style.getColor(
                        R.styleable.GTextView_text_center_color,
                        Color.TRANSPARENT
                    )
                    textEndColor = style.getColor(
                        R.styleable.GTextView_text_end_color,
                        Color.TRANSPARENT
                    )
                    textGradientAngle = style.getFloat(
                        R.styleable.GTextView_text_color_angle,
                        0f
                    )
                } else {
                    textStartColor = Color.TRANSPARENT
                    textCenterColor = Color.TRANSPARENT
                    textEndColor = Color.TRANSPARENT
                    textGradientAngle = 0f
                }
            }
        } finally {
            style.recycle()
        }
    }

    /**
     * 当 View 尺寸变化时重新创建文本 Shader。
     */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w != currentWidth || h != currentHeight) {
            currentWidth = w
            currentHeight = h
            updateTextShader()
        }
    }

    /**
     * Drawable 状态变化回调。
     *
     * 当调用：
     *
     * view.isSelected = true
     *
     * TextView 会进入这里。
     *
     * 这里需要重新更新文本 Shader / selected 字体颜色。
     */
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (!changingTextColorInternally) {
            updateTextShader()
        }
    }

    /**
     * 当前是否处于 selected 状态。
     */
    private fun isSelectedTextState(): Boolean {
        return drawableState.contains(android.R.attr.state_selected)
    }


    override fun setTextColor(color: Int) {
        super.setTextColor(color)

        if (!changingTextColorInternally) {
            normalTextColor = color
            updateTextShader()
        }
    }

    /**
     * 内部设置 TextView 字体颜色。
     *
     * 不能只设置 paint.color，
     * 因为 TextView 自身会根据 textColor / ColorStateList 刷新 paint.color。
     */
    private fun setTextColorInternally(@ColorInt color: Int) {
        if (currentTextColor == color) return

        changingTextColorInternally = true
        try {
            super.setTextColor(color)
        } finally {
            changingTextColorInternally = false
        }
    }

    /**
     * 根据当前状态更新文本 Shader 或字体颜色。
     *
     * 优先级：
     *
     * 1. selected 文本渐变
     * 2. selected 纯色字体
     * 3. textDrawable
     * 4. 普通文本渐变
     * 5. android:textColor / 默认字体颜色
     */
    private fun updateTextShader() {
        val selected = isSelectedTextState()

        // --------------------------------------------------------------------
        // 1. selected 文本渐变需要宽高，如果宽高有效，则优先处理
        // --------------------------------------------------------------------

        if (
            selected &&
            hasSelectedTextGradient &&
            currentWidth > 0 &&
            currentHeight > 0
        ) {
            /**
             * selected 渐变字体依赖 shader，
             * 这里先恢复普通字体色，避免之前 selected 纯色残留。
             */
            setTextColorInternally(normalTextColor)

            val selectedColors = buildSelectedTextGradientColors()

            textGradientShader = createLinearGradientForText(
                colors = selectedColors,
                angle = selectedTextGradientAngle,
                width = currentWidth,
                height = currentHeight
            )

            paint.shader = textGradientShader
            invalidate()
            return
        }

        // --------------------------------------------------------------------
        // 2. selected 纯色字体不依赖宽高
        // --------------------------------------------------------------------

        if (
            selected &&
            selectedTextColor != TEXT_COLOR_UNSET
        ) {
            textGradientShader = null
            paint.shader = null

            /**
             * selected 纯色字体必须通过 TextView.setTextColor 设置，
             * 不能只改 paint.color。
             */
            setTextColorInternally(selectedTextColor)

            invalidate()
            return
        }

        /**
         * 非 selected 状态下恢复普通字体颜色。
         *
         * 否则从 selected 切回 normal 时，
         * super.setTextColor(selectedTextColor) 设置过的颜色会残留。
         */
        setTextColorInternally(normalTextColor)

        // --------------------------------------------------------------------
        // 3. 如果宽高为 0，无法创建 Shader，恢复默认文本颜色
        // --------------------------------------------------------------------

        if (currentWidth == 0 || currentHeight == 0) {
            textGradientShader = null
            paint.shader = null
            paint.color = normalTextColor
            invalidate()
            return
        }

        // --------------------------------------------------------------------
        // 4. Drawable 填充文本
        // --------------------------------------------------------------------

        if (textDrawable != null) {
            val bitmap = textDrawable!!.toBitmap(
                currentWidth,
                currentHeight,
                Bitmap.Config.ARGB_8888
            )

            textGradientShader = BitmapShader(
                bitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )

            paint.shader = textGradientShader
        } else {
            // ----------------------------------------------------------------
            // 5. 普通文本颜色渐变
            // ----------------------------------------------------------------

            val hasTextGradient =
                textStartColor != Color.TRANSPARENT ||
                        textCenterColor != Color.TRANSPARENT ||
                        textEndColor != Color.TRANSPARENT

            val textColors = if (hasTextGradient) {
                buildTextGradientColors()
            } else {
                null
            }

            if (textColors != null) {
                textGradientShader = createLinearGradientForText(
                    colors = textColors,
                    angle = textGradientAngle,
                    width = currentWidth,
                    height = currentHeight
                )
                paint.shader = textGradientShader
            } else {
                /**
                 * 没有文本渐变，也没有 Drawable 填充，
                 * 恢复 TextView 默认文字颜色。
                 */
                textGradientShader = null
                paint.shader = null
                paint.color = normalTextColor
            }
        }

        invalidate()
    }

    /**
     * 构建普通文本渐变颜色数组。
     *
     * 支持：
     *
     * 1. 两色渐变
     * 2. 三色渐变
     *
     * 注意：
     *
     * 这里沿用原来的逻辑：
     * Color.TRANSPARENT 表示没有设置该颜色。
     */
    private fun buildTextGradientColors(): IntArray {
        return if (
            textCenterColor != Color.TRANSPARENT &&
            textStartColor != textCenterColor &&
            textCenterColor != textEndColor
        ) {
            intArrayOf(
                textStartColor,
                textCenterColor,
                textEndColor
            )
        } else {
            intArrayOf(
                textStartColor,
                textEndColor
            )
        }
    }

    /**
     * 构建 selected 选中态文本渐变颜色数组。
     *
     * 支持：
     *
     * 1. 只设置 start
     * 2. 只设置 center
     * 3. 只设置 end
     * 4. start + end 两色渐变
     * 5. start + center + end 三色渐变
     *
     * 如果只设置了部分颜色，会自动补齐，
     * 避免 LinearGradient 因颜色数量不足产生异常。
     */
    private fun buildSelectedTextGradientColors(): IntArray {
        val realStartColor = when {
            selectedTextStartColor != Color.TRANSPARENT -> selectedTextStartColor
            selectedTextCenterColor != Color.TRANSPARENT -> selectedTextCenterColor
            selectedTextEndColor != Color.TRANSPARENT -> selectedTextEndColor
            else -> Color.TRANSPARENT
        }

        val realEndColor = when {
            selectedTextEndColor != Color.TRANSPARENT -> selectedTextEndColor
            selectedTextCenterColor != Color.TRANSPARENT -> selectedTextCenterColor
            selectedTextStartColor != Color.TRANSPARENT -> selectedTextStartColor
            else -> realStartColor
        }

        return if (selectedTextCenterColor != Color.TRANSPARENT) {
            intArrayOf(
                realStartColor,
                selectedTextCenterColor,
                realEndColor
            )
        } else {
            intArrayOf(
                realStartColor,
                realEndColor
            )
        }
    }

    /**
     * 根据颜色数组、角度、宽度和高度为文本创建 LinearGradient。
     *
     * 逻辑与 BaseDrawable 中的 createLinearGradient 类似。
     */
    private fun createLinearGradientForText(
        colors: IntArray,
        angle: Float,
        width: Int,
        height: Int
    ): LinearGradient {
        val radians = Math.toRadians(angle.toDouble()).toFloat()

        val centerX = width / 2f
        val centerY = height / 2f

        /**
         * 使用对角线长度，保证任意角度下渐变能覆盖整个文本区域。
         */
        val maxLen = hypot(
            (width / 2f).toDouble(),
            (height / 2f).toDouble()
        ).toFloat()

        val x0 = centerX - cos(radians.toDouble()).toFloat() * maxLen
        val y0 = centerY - sin(radians.toDouble()).toFloat() * maxLen
        val x1 = centerX + cos(radians.toDouble()).toFloat() * maxLen
        val y1 = centerY + sin(radians.toDouble()).toFloat() * maxLen

        val positions: FloatArray? = if (colors.size == 3) {
            floatArrayOf(0f, 0.5f, 1f)
        } else {
            null
        }

        return LinearGradient(
            x0,
            y0,
            x1,
            y1,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
    }

    // ------------------------------------------------------------------------
    // GTextView 独有的 setter 方法
    // ------------------------------------------------------------------------

    /**
     * 动态设置文本填充 Drawable。
     *
     * 设置后，普通文本颜色渐变将失效。
     *
     * 注意：
     *
     * selected 文本渐变 / selected 纯色字体仍然具有更高优先级。
     */
    fun setTextDrawable(drawable: Drawable?) {
        if (this.textDrawable != drawable) {
            this.textDrawable = drawable

            textStartColor = Color.TRANSPARENT
            textCenterColor = Color.TRANSPARENT
            textEndColor = Color.TRANSPARENT
            textGradientAngle = 0f

            updateTextShader()
        }
    }

    /**
     * 动态设置普通文本渐变颜色。
     *
     * 支持两色或三色渐变。
     *
     * @param startColor 渐变起始颜色
     * @param centerColor 渐变中心颜色，可选
     * @param endColor 渐变结束颜色
     */
    fun setTextColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int = Color.TRANSPARENT,
        @ColorInt endColor: Int
    ) {
        if (
            textDrawable == null &&
            (
                    this.textStartColor != startColor ||
                            this.textCenterColor != centerColor ||
                            this.textEndColor != endColor
                    )
        ) {
            this.textStartColor = startColor
            this.textCenterColor = centerColor
            this.textEndColor = endColor

            updateTextShader()
        }
    }

    /**
     * 动态设置普通文本两色渐变颜色。
     */
    fun setTextColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        setTextColors(
            startColor = startColor,
            centerColor = Color.TRANSPARENT,
            endColor = endColor
        )
    }

    /**
     * 动态设置普通文本渐变角度。
     */
    fun setTextGradientAngle(angle: Float) {
        if (textGradientAngle != angle) {
            this.textGradientAngle = angle
            updateTextShader()
        }
    }

    /**
     * 动态设置 selected 选中态纯色字体颜色。
     *
     * 设置后，当：
     *
     * view.isSelected = true
     *
     * 文本会使用该颜色。
     *
     * 注意：
     *
     * 该方法会清除 selected 文本渐变。
     */
    fun setSelectedTextColor(@ColorInt color: Int) {
        selectedTextColor = color

        hasSelectedTextGradient = false
        selectedTextStartColor = Color.TRANSPARENT
        selectedTextCenterColor = Color.TRANSPARENT
        selectedTextEndColor = Color.TRANSPARENT
        selectedTextGradientAngle = 0f

        updateTextShader()
    }

    /**
     * 动态设置 selected 选中态两色渐变字体。
     */
    fun setSelectedTextColors(
        @ColorInt startColor: Int,
        @ColorInt endColor: Int
    ) {
        hasSelectedTextGradient = true

        selectedTextStartColor = startColor
        selectedTextCenterColor = Color.TRANSPARENT
        selectedTextEndColor = endColor
        selectedTextGradientAngle = 0f

        /**
         * selected 渐变优先级高于 selected 纯色。
         */
        selectedTextColor = TEXT_COLOR_UNSET

        updateTextShader()
    }

    /**
     * 动态设置 selected 选中态三色渐变字体。
     */
    fun setSelectedTextColors(
        @ColorInt startColor: Int,
        @ColorInt centerColor: Int,
        @ColorInt endColor: Int
    ) {
        hasSelectedTextGradient = true

        selectedTextStartColor = startColor
        selectedTextCenterColor = centerColor
        selectedTextEndColor = endColor

        /**
         * selected 渐变优先级高于 selected 纯色。
         */
        selectedTextColor = TEXT_COLOR_UNSET

        updateTextShader()
    }

    /**
     * 动态设置 selected 选中态文本渐变角度。
     */
    fun setSelectedTextGradientAngle(angle: Float) {
        if (selectedTextGradientAngle != angle) {
            selectedTextGradientAngle = angle
            updateTextShader()
        }
    }

    /**
     * 清除 selected 选中态字体样式。
     *
     * 清除后 selected 状态下会继续使用：
     *
     * 1. textDrawable
     * 2. 普通文本渐变
     * 3. android:textColor
     */
    fun clearSelectedTextColor() {
        selectedTextColor = TEXT_COLOR_UNSET

        hasSelectedTextGradient = false
        selectedTextStartColor = Color.TRANSPARENT
        selectedTextCenterColor = Color.TRANSPARENT
        selectedTextEndColor = Color.TRANSPARENT
        selectedTextGradientAngle = 0f

        updateTextShader()
    }
}