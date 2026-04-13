/*
 * Copyright 2024 Stream.IO, Inc.
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.photo.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import com.photo.view.listener.OnMatrixChangedListener
import com.photo.view.listener.OnOutsidePhotoTapListener
import com.photo.view.listener.OnPhotoTapListener
import com.photo.view.listener.OnScaleChangedListener
import com.photo.view.listener.OnSingleFlingListener
import com.photo.view.listener.OnViewDragListener
import com.photo.view.listener.OnViewExitListener
import com.photo.view.listener.OnViewTapListener

/**
 * A zoomable ImageView. See [PhotoViewAttacher] for most of the details on how the zooming
 * is accomplished
 */

public class PhotoView @JvmOverloads constructor(
  context: Context,
  attr: AttributeSet? = null,
  @StyleRes defStyle: Int = 0,
) : AppCompatImageView(context, attr, defStyle) {
  /**
   * Get the current [PhotoViewAttacher] for this view. Be wary of holding on to references
   * to this attacher, as it has a reference to this view, which, if a reference is held in the
   * wrong place, can cause memory leaks.
   *
   * This is a tricky way to check if the [attacher] has been initialized or not before
   * accessing to its methods.
   *
   * @return the attacher.
   */
  private lateinit var attacher: PhotoViewAttacher // Don't remove lateinit
  private var pendingScaleType: ScaleType? = null

  init {
    attacher = PhotoViewAttacher(this)
    super.setScaleType(ScaleType.MATRIX)
    // apply the previously applied scale type
    if (pendingScaleType != null) {
      scaleType = pendingScaleType!!
      pendingScaleType = null
    }
  }

  override fun getScaleType(): ScaleType {
    return attacher.scaleType
  }

  override fun getImageMatrix(): Matrix {
    return attacher.imageMatrix
  }

  override fun setOnLongClickListener(l: OnLongClickListener?) {
    attacher.setOnLongClickListener(l)
  }

  override fun setOnClickListener(l: OnClickListener?) {
    attacher.setOnClickListener(l)
  }

  override fun setScaleType(scaleType: ScaleType) {
    if (drawable == null || !::attacher.isInitialized) {
      pendingScaleType = scaleType
    } else {
      attacher.scaleType = scaleType
    }
  }

  override fun setImageDrawable(drawable: Drawable?) {
    super.setImageDrawable(drawable)
    // setImageBitmap calls through to this method

    if (::attacher.isInitialized) {
      attacher.update()
    }
  }

  override fun setImageResource(resId: Int) {
    super.setImageResource(resId)
    if (::attacher.isInitialized) {
      attacher.update()
    }
  }

  override fun setImageURI(uri: Uri?) {
    super.setImageURI(uri)
    if (::attacher.isInitialized) {
      attacher.update()
    }
  }

  override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
    val changed = super.setFrame(l, t, r, b)
    if (changed && ::attacher.isInitialized) {
      attacher.update()
    }
    return changed
  }

  public fun setRotationTo(rotationDegree: Float) {
    attacher.setRotationTo(rotationDegree)
  }

  public fun setRotationBy(rotationDegree: Float) {
    attacher.setRotationBy(rotationDegree)
  }

  public var isZoomable: Boolean
    get() = attacher.isZoomable
    set(zoomable) {
      attacher.isZoomable = zoomable
    }
  public val displayRect: RectF?
    get() = attacher.displayRect

  public fun getDisplayMatrix(matrix: Matrix?) {
    attacher.getDisplayMatrix(matrix)
  }

  public fun setDisplayMatrix(finalRectangle: Matrix?): Boolean {
    return attacher.setDisplayMatrix(finalRectangle)
  }

  public fun getSuppMatrix(matrix: Matrix?) {
    attacher.getSuppMatrix(matrix)
  }

  public fun setSuppMatrix(matrix: Matrix?): Boolean {
    return attacher.setDisplayMatrix(matrix)
  }

  public var minimumScale: Float
    get() = attacher.minimumScale
    set(minimumScale) {
      attacher.minimumScale = minimumScale
    }
  public var mediumScale: Float
    get() = attacher.mediumScale
    set(mediumScale) {
      attacher.mediumScale = mediumScale
    }
  public var maximumScale: Float
    get() = attacher.maximumScale
    set(maximumScale) {
      attacher.maximumScale = maximumScale
    }
  public var scale: Float
    get() = attacher.scale
    set(scale) {
      attacher.scale = scale
    }

  /**
   * 拖拽退出时的最小缩放比
   */
  var dragMinScale: Float
    get() = attacher.minScaleOnExit
    set(scale) {
      attacher.minScaleOnExit = scale
    }

  /**
   * 下拉图片的阻尼量
   * 越大阻尼越轻、越能拖动；越小阻尼越紧
   */
  var factor: Int
    get() = attacher.factor
    set(factor) {
      attacher.factor = factor
    }

  public fun setAllowParentInterceptOnEdge(allow: Boolean) {
    attacher.setAllowParentInterceptOnEdge(allow)
  }

  public fun setScaleLevels(minimumScale: Float, mediumScale: Float, maximumScale: Float) {
    attacher.setScaleLevels(minimumScale, mediumScale, maximumScale)
  }

  public fun setOnMatrixChangeListener(listener: OnMatrixChangedListener?) {
    attacher.setOnMatrixChangeListener(listener)
  }

  public fun setOnPhotoTapListener(listener: OnPhotoTapListener?) {
    attacher.setOnPhotoTapListener(listener)
  }

  public fun setOnOutsidePhotoTapListener(listener: OnOutsidePhotoTapListener?) {
    attacher.setOnOutsidePhotoTapListener(listener)
  }

  public fun setOnViewTapListener(listener: OnViewTapListener?) {
    attacher.setOnViewTapListener(listener)
  }

  public fun setOnViewDragListener(listener: OnViewDragListener?) {
    attacher.setOnViewDragListener(listener)
  }

  public fun setOnViewExitListener(listener: OnViewExitListener?) {
    attacher.setOnViewExitListener(listener)
  }

  public fun setScale(scale: Float, animate: Boolean) {
    attacher.setScale(scale, animate)
  }

  public fun setScale(scale: Float, focalX: Float, focalY: Float, animate: Boolean) {
    attacher.setScale(scale, focalX, focalY, animate)
  }

  public fun setZoomTransitionDuration(milliseconds: Int) {
    attacher.setZoomTransitionDuration(milliseconds)
  }

  public fun setOnDoubleTapListener(onDoubleTapListener: GestureDetector.OnDoubleTapListener?) {
    attacher.setOnDoubleTapListener(onDoubleTapListener)
  }

  public fun setOnScaleChangeListener(onScaleChangedListener: OnScaleChangedListener?) {
    attacher.setOnScaleChangeListener(onScaleChangedListener)
  }

  public fun setOnSingleFlingListener(onSingleFlingListener: OnSingleFlingListener?) {
    attacher.setOnSingleFlingListener(onSingleFlingListener)
  }

  fun bindExitParent (parent: View) {
    attacher.bindExitParent(parent)
  }
}
