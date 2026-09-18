package com.android.common.view.base

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import com.android.common.R

internal const val BD_UNSET: Int = Int.MIN_VALUE

internal data class GradientSpec(
    @ColorInt var startColor: Int = BD_UNSET,
    @ColorInt var centerColor: Int = BD_UNSET,
    @ColorInt var endColor: Int = BD_UNSET,

    /**
     * 渐变角度。
     *
     * 0   表示从左到右
     * 90  表示从上到下
     * 180 表示从右到左
     * 270 表示从下到上
     */
    var angle: Float = 0f,

    /**
     * 渐变中间点位置。
     *
     * 默认是 0.5f，也就是中间位置。
     *
     * 取值建议：
     *
     * 0f ~ 1f
     *
     * 例如：
     *
     * 0.3f 表示中间色出现在 30% 的位置。
     *
     * 如果只设置了 startColor / endColor 两种颜色，
     * BaseDrawable 会自动把 startColor 当成 centerColor 使用：
     *
     * colors = [startColor, startColor, endColor]
     * positions = [0f, centerPosition, 1f]
     */
    var centerPosition: Float = 0.5f
) {
    fun hasColor(): Boolean {
        return startColor != BD_UNSET ||
                centerColor != BD_UNSET ||
                endColor != BD_UNSET
    }

    fun clear() {
        startColor = BD_UNSET
        centerColor = BD_UNSET
        endColor = BD_UNSET
        angle = 0f
        centerPosition = 0.5f
    }
}

internal data class BackgroundSpec(
    @ColorInt var color: Int = BD_UNSET,
    var drawable: Drawable? = null,
    var gradient: GradientSpec = GradientSpec()
) {
    fun hasContent(): Boolean {
        return drawable != null ||
                color != BD_UNSET ||
                gradient.hasColor()
    }

    fun clear() {
        color = BD_UNSET
        drawable = null
        gradient.clear()
    }
}

internal data class BorderSpec(
    var width: Float = 0f,
    @ColorInt var color: Int = BD_UNSET,
    var gradient: GradientSpec = GradientSpec()
) {
    fun hasColor(): Boolean {
        return color != BD_UNSET || gradient.hasColor()
    }

    fun clearColor() {
        color = BD_UNSET
        gradient.clear()
    }
}

internal data class CornerSpec(
    var radius: Float = 0f,
    var radii: FloatArray? = null
)

internal data class ShadowSpec(
    @ColorInt var color: Int = BD_UNSET,
    var radius: Float = 0f,
    var dx: Float = 0f,
    var dy: Float = 0f
) {
    fun hasShadow(): Boolean {
        return color != BD_UNSET && radius > 0f
    }

    fun clear() {
        color = BD_UNSET
        radius = 0f
        dx = 0f
        dy = 0f
    }
}

internal data class BaseDrawableConfig(
    var background: BackgroundSpec = BackgroundSpec(),
    var border: BorderSpec = BorderSpec(),
    var corner: CornerSpec = CornerSpec(),
    var shadow: ShadowSpec = ShadowSpec(),
    var selectedBackground: BackgroundSpec = BackgroundSpec(),
    var selectedBorder: BorderSpec = BorderSpec()
)

internal object BaseDrawableAttrsParser {

    fun parse(context: Context, attrs: AttributeSet?): BaseDrawableConfig {
        if (attrs == null) return BaseDrawableConfig()

        val ta = context.obtainStyledAttributes(
            attrs,
            R.styleable.CommonGradientAttrs,
            0,
            0
        )

        return try {
            BaseDrawableConfig(
                background = parseBackground(ta),
                border = parseBorder(ta),
                corner = parseCorner(ta),
                shadow = parseShadow(ta),
                selectedBackground = parseSelectedBackground(ta),
                selectedBorder = parseSelectedBorder(ta)
            )
        } finally {
            ta.recycle()
        }
    }

    private fun parseBackground(ta: TypedArray): BackgroundSpec {
        val systemBackground = ta.getDrawable(
            R.styleable.CommonGradientAttrs_android_background
        )?.mutate()

        val hasGradient = ta.hasAny(
            R.styleable.CommonGradientAttrs_start_color,
            R.styleable.CommonGradientAttrs_center_color,
            R.styleable.CommonGradientAttrs_end_color
        )

        if (hasGradient) {
            return BackgroundSpec(
                gradient = GradientSpec(
                    startColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_start_color,
                        BD_UNSET
                    ),
                    centerColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_center_color,
                        BD_UNSET
                    ),
                    endColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_end_color,
                        BD_UNSET
                    ),
                    angle = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_angle,
                        0f
                    ),
                    centerPosition = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_center_position,
                        0.5f
                    )
                )
            )
        }

        return BackgroundSpec(
            drawable = systemBackground
        )
    }

    private fun parseBorder(ta: TypedArray): BorderSpec {
        val width = ta.getDimension(
            R.styleable.CommonGradientAttrs_border,
            0f
        )

        val border = BorderSpec(width = width)

        val hasSolidColor = ta.hasValue(
            R.styleable.CommonGradientAttrs_border_color
        )

        val hasGradient = ta.hasAny(
            R.styleable.CommonGradientAttrs_border_start_color,
            R.styleable.CommonGradientAttrs_border_center_color,
            R.styleable.CommonGradientAttrs_border_end_color
        )

        when {
            hasSolidColor -> {
                border.color = ta.getColor(
                    R.styleable.CommonGradientAttrs_border_color,
                    BD_UNSET
                )
            }

            hasGradient -> {
                border.gradient = GradientSpec(
                    startColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_border_start_color,
                        BD_UNSET
                    ),
                    centerColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_border_center_color,
                        BD_UNSET
                    ),
                    endColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_border_end_color,
                        BD_UNSET
                    ),
                    angle = ta.getFloat(
                        R.styleable.CommonGradientAttrs_border_color_angle,
                        0f
                    ),
                    centerPosition = ta.getFloat(
                        R.styleable.CommonGradientAttrs_border_center_position,
                        0.5f
                    )
                )
            }
        }

        return border
    }

    private fun parseCorner(ta: TypedArray): CornerSpec {
        val radius = ta.getDimension(
            R.styleable.CommonGradientAttrs_radius,
            0f
        )

        val topLeft = ta.getDimension(
            R.styleable.CommonGradientAttrs_radius_top_left,
            radius
        )
        val topRight = ta.getDimension(
            R.styleable.CommonGradientAttrs_radius_top_right,
            radius
        )
        val bottomRight = ta.getDimension(
            R.styleable.CommonGradientAttrs_radius_bottom_right,
            radius
        )
        val bottomLeft = ta.getDimension(
            R.styleable.CommonGradientAttrs_radius_bottom_left,
            radius
        )

        val radii = if (
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

        return CornerSpec(
            radius = radius,
            radii = radii
        )
    }

    private fun parseShadow(ta: TypedArray): ShadowSpec {
        return ShadowSpec(
            color = ta.getColor(
                R.styleable.CommonGradientAttrs_g_shadow_color,
                BD_UNSET
            ),
            radius = ta.getDimension(
                R.styleable.CommonGradientAttrs_g_shadow_radius,
                0f
            ),
            dx = ta.getDimension(
                R.styleable.CommonGradientAttrs_g_shadow_dx,
                0f
            ),
            dy = ta.getDimension(
                R.styleable.CommonGradientAttrs_g_shadow_dy,
                0f
            )
        )
    }

    private fun parseSelectedBackground(ta: TypedArray): BackgroundSpec {
        val result = BackgroundSpec()

        val hasSelectedDrawable = ta.hasValue(
            R.styleable.CommonGradientAttrs_g_selected_background
        )

        val hasSelectedGradient = ta.hasAny(
            R.styleable.CommonGradientAttrs_g_selected_start_color,
            R.styleable.CommonGradientAttrs_g_selected_center_color,
            R.styleable.CommonGradientAttrs_g_selected_end_color
        )

        when {
            hasSelectedDrawable -> {
                result.drawable = ta.getDrawable(
                    R.styleable.CommonGradientAttrs_g_selected_background
                )?.mutate()

                if (result.drawable == null) {
                    val color = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_background,
                        BD_UNSET
                    )

                    if (color != BD_UNSET) {
                        result.drawable = ColorDrawable(color)
                    }
                }
            }

            hasSelectedGradient -> {
                result.gradient = GradientSpec(
                    startColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_start_color,
                        BD_UNSET
                    ),
                    centerColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_center_color,
                        BD_UNSET
                    ),
                    endColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_end_color,
                        BD_UNSET
                    ),
                    angle = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_selected_angle,
                        0f
                    ),
                    centerPosition = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_selected_center_position,
                        0.5f
                    )
                )
            }
        }

        return result
    }

    private fun parseSelectedBorder(ta: TypedArray): BorderSpec {
        val border = BorderSpec()

        val hasSolidColor = ta.hasValue(
            R.styleable.CommonGradientAttrs_g_selected_border_color
        )

        val hasGradient = ta.hasAny(
            R.styleable.CommonGradientAttrs_g_selected_border_start_color,
            R.styleable.CommonGradientAttrs_g_selected_border_center_color,
            R.styleable.CommonGradientAttrs_g_selected_border_end_color
        )

        when {
            hasSolidColor -> {
                border.color = ta.getColor(
                    R.styleable.CommonGradientAttrs_g_selected_border_color,
                    BD_UNSET
                )
            }

            hasGradient -> {
                border.gradient = GradientSpec(
                    startColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_border_start_color,
                        BD_UNSET
                    ),
                    centerColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_border_center_color,
                        BD_UNSET
                    ),
                    endColor = ta.getColor(
                        R.styleable.CommonGradientAttrs_g_selected_border_end_color,
                        BD_UNSET
                    ),
                    angle = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_selected_border_angle,
                        0f
                    ),
                    centerPosition = ta.getFloat(
                        R.styleable.CommonGradientAttrs_g_selected_border_center_position,
                        0.5f
                    )
                )
            }
        }

        return border
    }

    private fun TypedArray.hasAny(vararg indexes: Int): Boolean {
        return indexes.any { hasValue(it) }
    }
}