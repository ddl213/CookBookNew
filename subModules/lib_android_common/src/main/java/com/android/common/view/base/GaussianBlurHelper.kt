package com.android.common.view.base

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/**
 * Bitmap 高斯模糊工具。
 *
 * 当前用于兼容 Android 12 以下。
 *
 * RenderScript 虽然已经废弃，
 * 但对于现有 Android 版本仍然可以工作。
 *
 * 后续如果替换实现，只需要修改这里，
 * GImageView API 不需要变化。
 */
internal object GaussianBlurHelper {

    @Suppress("DEPRECATION")
    fun blur(
        context: Context,
        source: Bitmap,
        radius: Float
    ): Bitmap {

        val safeRadius = radius.coerceIn(0.1f, 25f)

        val inputBitmap = source.copy(
            Bitmap.Config.ARGB_8888,
            true
        )

        val outputBitmap = Bitmap.createBitmap(
            inputBitmap.width,
            inputBitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val renderScript = RenderScript.create(
            context.applicationContext
        )

        var input: Allocation? = null
        var output: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null

        try {

            input = Allocation.createFromBitmap(
                renderScript,
                inputBitmap
            )

            output = Allocation.createTyped(
                renderScript,
                input.type
            )

            blur = ScriptIntrinsicBlur.create(
                renderScript,
                Element.U8_4(renderScript)
            )

            blur.setRadius(safeRadius)

            blur.setInput(input)

            blur.forEach(output)

            output.copyTo(outputBitmap)

        } finally {

            blur?.destroy()
            input?.destroy()
            output?.destroy()

            renderScript.destroy()

            if (
                inputBitmap !== source &&
                !inputBitmap.isRecycled
            ) {
                inputBitmap.recycle()
            }
        }

        return outputBitmap
    }
}