package com.android.common.ext

import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import com.android.common.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target


/**
 * --------------------------------- ImageView ---------------------------------
 */
fun ImageView.load(url : String){
    Glide.with(this)
        .load( url)
        .into(this)
}

fun ImageView.load(url : Uri){
    Glide.with(this)
        .load( url)
        .into(this)
}

fun ImageView.load(url : Int){
    Glide.with(this)
        .load( url)
        .error(R.color.color_0a80ed)
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Log.d("myLogD", "onLoadFailed: 加载失败")
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                return false
            }

        })
        .into(this)
}
