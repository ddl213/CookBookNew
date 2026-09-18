package com.android.common.view.base

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * 支持背景、图片、foreground 统一圆角裁剪的 ImageView。
 *
 * app:radius / 四角圆角会同时作用于：
 *
 * 1. background
 * 2. android:src
 * 3. app:srcCompat
 * 4. foreground
 */
class GImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), GStyleable {

    override val gStyler: GStyler = GStyler(this, context, attrs)

    private val clipPath = Path()
    private val clipRect = RectF()

    private var originalBlurDrawable: Drawable? = null

    private var blurredBitmap: Bitmap? = null

    override fun draw(canvas: Canvas) {
        val radii = gStyler.getCornerRadii()

        // 没有设置圆角时，不增加额外裁剪开销
        if (radii == null) {
            super.draw(canvas)
            return
        }

        clipRect.set(
            0f,
            0f,
            width.toFloat(),
            height.toFloat()
        )

        clipPath.reset()
        clipPath.addRoundRect(
            clipRect,
            radii,
            Path.Direction.CW
        )

        val saveCount = canvas.save()

        canvas.clipPath(clipPath)

        /*
         * 直接裁剪整个 super.draw()。
         *
         * 因此以下内容全部会被裁剪：
         *
         * background
         *      ↓
         * src / srcCompat
         *      ↓
         * foreground
         */
        super.draw(canvas)

        canvas.restoreToCount(saveCount)
    }


    /**
     * 对当前 src / srcCompat 图片进行高斯模糊。
     *
     * @param radius 模糊半径，范围 0.1f ~ 25f
     */
    fun setGaussianBlur(radius: Float = 16f) {

        if (radius <= 0f) {
            clearGaussianBlur()
            return
        }

        post {

            /*
             * 第一次开启模糊时保存原始 Drawable。
             *
             * 后续再次修改模糊半径，
             * 始终基于原图进行处理，
             * 避免：
             *
             * 模糊 -> 再模糊 -> 再模糊
             *
             * 导致图片越来越糊。
             */
            if (originalBlurDrawable == null) {

                val current = drawable ?: return@post

                originalBlurDrawable =
                    current.constantState
                        ?.newDrawable(resources)
                        ?.mutate()
                        ?: current
            }

            val sourceDrawable =
                originalBlurDrawable
                    ?: return@post

            val sourceBitmap =
                drawableToBlurBitmap(sourceDrawable)

            val result = GaussianBlurHelper.blur(
                context = context,
                source = sourceBitmap,
                radius = radius
            )

            blurredBitmap = result

            setImageBitmap(result)
        }
    }


    /**
     * 清除高斯模糊，恢复原图。
     */
    fun clearGaussianBlur() {

        val original =
            originalBlurDrawable
                ?: return

        setImageDrawable(original)

        originalBlurDrawable = null
        blurredBitmap = null
    }


    /**
     * 将 Drawable 转换成适合当前 View 尺寸的 Bitmap。
     *
     * 不直接使用上传图片的原始尺寸，
     * 避免 4000x3000 等大图直接进行高斯模糊。
     *
     * 同时保持图片原始宽高比。
     */
    private fun drawableToBlurBitmap(
        drawable: Drawable
    ): Bitmap {

        val drawableWidth =
            drawable.intrinsicWidth
                .takeIf { it > 0 }
                ?: width

        val drawableHeight =
            drawable.intrinsicHeight
                .takeIf { it > 0 }
                ?: height

        val availableWidth =
            (width - paddingLeft - paddingRight)
                .coerceAtLeast(1)

        val availableHeight =
            (height - paddingTop - paddingBottom)
                .coerceAtLeast(1)

        val scale = minOf(
            availableWidth / drawableWidth.toFloat(),
            availableHeight / drawableHeight.toFloat(),
            1f
        )

        val targetWidth =
            (drawableWidth * scale)
                .roundToInt()
                .coerceAtLeast(1)

        val targetHeight =
            (drawableHeight * scale)
                .roundToInt()
                .coerceAtLeast(1)

        return drawable.toBitmap(
            width = targetWidth,
            height = targetHeight,
            config = Bitmap.Config.ARGB_8888
        )
    }
}