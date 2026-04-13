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
import android.graphics.Matrix.ScaleToFit
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.OverScroller
import com.photo.view.Compat.postOnAnimation
import com.photo.view.Util.checkZoomLevels
import com.photo.view.Util.hasDrawable
import com.photo.view.Util.isSupportedScaleType
import com.photo.view.listener.OnGestureListener
import com.photo.view.listener.OnMatrixChangedListener
import com.photo.view.listener.OnOutsidePhotoTapListener
import com.photo.view.listener.OnPhotoTapListener
import com.photo.view.listener.OnScaleChangedListener
import com.photo.view.listener.OnSingleFlingListener
import com.photo.view.listener.OnViewDragListener
import com.photo.view.listener.OnViewExitListener
import com.photo.view.listener.OnViewTapListener
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The component of [PhotoView] which does the work allowing for zooming, scaling, panning, etc.
 * It is made public in case you need to subclass something other than AppCompatImageView and still
 * gain the functionality that [PhotoView] offers
 */
internal class PhotoViewAttacher(private val mImageView: ImageView) :
  OnTouchListener,
  OnLayoutChangeListener {
  private var mInterpolator: Interpolator = AccelerateDecelerateInterpolator()
  private var mZoomDuration = DEFAULT_ZOOM_DURATION
  private var mMinScale = DEFAULT_MIN_SCALE
  private var mMidScale = DEFAULT_MID_SCALE
  private var mMaxScale = DEFAULT_MAX_SCALE
  private var mAllowParentInterceptOnEdge = true
  private var mBlockParentIntercept = false

  // Gesture Detectors
  private var mGestureDetector: GestureDetector =
    GestureDetector(
      mImageView.context,
      object : SimpleOnGestureListener() {
        // forward long click listener
        override fun onLongPress(e: MotionEvent) {
          if (mLongClickListener != null) {
            mLongClickListener!!.onLongClick(mImageView)
          }
        }

        override fun onFling(
          e1: MotionEvent?,
          e2: MotionEvent,
          velocityX: Float,
          velocityY: Float,
        ): Boolean {
          if (mSingleFlingListener != null) {
            if (scale > DEFAULT_MIN_SCALE) {
              return false
            }
            return if (e1!!.pointerCount > SINGLE_TOUCH ||
              e2.pointerCount > SINGLE_TOUCH
            ) {
              false
            } else {
              mSingleFlingListener!!.onFling(e1, e2, velocityX, velocityY)
            }
          }
          return false
        }
      },
    )
  private var mScaleDragDetector: CustomGestureDetector? = null

  // These are set so we don't keep allocating them on the heap
  private val mBaseMatrix = Matrix()
  internal val imageMatrix: Matrix = Matrix()
  private val mSuppMatrix = Matrix()
  private val mDisplayRect = RectF()
  private val mMatrixValues = FloatArray(9)

  // Listeners
  private var mMatrixChangeListener: OnMatrixChangedListener? = null
  private var mPhotoTapListener: OnPhotoTapListener? = null
  private var mOutsidePhotoTapListener: OnOutsidePhotoTapListener? = null
  private var mViewTapListener: OnViewTapListener? = null
  private var mOnClickListener: View.OnClickListener? = null
  private var mLongClickListener: OnLongClickListener? = null
  private var mScaleChangeListener: OnScaleChangedListener? = null
  private var mSingleFlingListener: OnSingleFlingListener? = null
  private var mOnViewDragListener: OnViewDragListener? = null
  private var mOnViewExitListener: OnViewExitListener? = null
  private var mCurrentFlingRunnable: FlingRunnable? = null
  private var mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
  private var mVerticalScrollEdge = VERTICAL_EDGE_BOTH
  private var mBaseRotation: Float = 0.0f

  @get:Deprecated("")
  var isZoomEnabled = true
    private set

  private var mScaleType = ScaleType.FIT_CENTER

  //拖拽退出需要的变量
  private var isDraggingToExit = false
  private var hasBegunNormalDrag = false
  private var hasCheckedDragType = false // 每次按下重置
  private var lastMotionY = 0f
  private var lastMotionX = 0f
  private var totalTranslateY = 0f
  private var totalTranslateX = 0f
  private val touchSlop = ViewConfiguration.get(mImageView.context).scaledTouchSlop
  private val exitThreshold = mImageView.context.resources.displayMetrics.heightPixels * 0.25f
  var minScaleOnExit : Float = 0.5f
  var factor: Int = FACTOR

  private val onGestureListener: OnGestureListener = object : OnGestureListener {
    override fun onDrag(dx: Float, dy: Float) {
      if (mScaleDragDetector?.isScaling == true) {
        return // Do not drag if we are already scaling
      }
      /**新*/
      if (isDraggingToExit) {
        return // Drag-to-exit is handled in onTouch
      }

      // === 如果允许退出，则不修改矩阵，只处理退出拖拽 ===
      val onVerticalEdge =
        mVerticalScrollEdge == VERTICAL_EDGE_TOP && dy >= 1f ||
                mVerticalScrollEdge == VERTICAL_EDGE_BOTTOM && dy <= -1f
      if (mOnViewDragListener != null) {
        mOnViewDragListener!!.onDrag(dx, dy)
      }
      // 1. 尝试执行 PhotoView 的标准平移 (Pan)
      mSuppMatrix.postTranslate(dx, dy)
      checkAndDisplayMatrix()

      /*
       * Here we decide whether to let the ImageView's parent to start taking
       * over the touch event.
       *
       * First we check whether this function is enabled. We never want the
       * parent to take over if we're scaling. We then check the edge we're
       * on, and the direction of the scroll (i.e. if we're pulling against
       * the edge, aka 'overscrolling', let the parent take over).
       */
      val parent = mImageView.parent

      if (mAllowParentInterceptOnEdge && mScaleDragDetector?.isScaling != true &&
        !mBlockParentIntercept
      ) {
        if (mHorizontalScrollEdge == HORIZONTAL_EDGE_BOTH ||
          mHorizontalScrollEdge == HORIZONTAL_EDGE_LEFT && dx >= 1f ||
          mHorizontalScrollEdge == HORIZONTAL_EDGE_RIGHT && dx <= -1f || onVerticalEdge) {

          parent?.requestDisallowInterceptTouchEvent(false)
        }
      } else {
        parent?.requestDisallowInterceptTouchEvent(true)
      }
    }

    override fun onFling(startX: Float, startY: Float, velocityX: Float, velocityY: Float) {
      if (isDraggingToExit) return
      mCurrentFlingRunnable = FlingRunnable(mImageView.context)
      mCurrentFlingRunnable!!.fling(
        getImageViewWidth(mImageView),
        getImageViewHeight(mImageView),
        velocityX.toInt(),
        velocityY.toInt(),
      )
      mImageView.post(mCurrentFlingRunnable)
    }

    override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
      onScale(scaleFactor, focusX, focusY, 0f, 0f)
    }

    override fun onScale(
      scaleFactor: Float,
      focusX: Float,
      focusY: Float,
      dx: Float,
      dy: Float,
    ) {
      if (isDraggingToExit) return
      if (scale < mMaxScale || scaleFactor < 1f) {
        if (mScaleChangeListener != null) {
          mScaleChangeListener!!.onScaleChange(scaleFactor, focusX, focusY)
        }
        mSuppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
        mSuppMatrix.postTranslate(dx, dy)
        checkAndDisplayMatrix()
      }
    }
  }

  init {
    mImageView.setOnTouchListener(this)
    mImageView.addOnLayoutChangeListener(this)
    if (!mImageView.isInEditMode) {

      // Create Gesture Detectors...
      mScaleDragDetector = CustomGestureDetector(mImageView.context, onGestureListener)
      mGestureDetector.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
          if (mOnClickListener != null) {
            mOnClickListener!!.onClick(mImageView)
          }
          val displayRect: RectF? = displayRect
          val x = e.x
          val y = e.y
          if (mViewTapListener != null) {
            mViewTapListener!!.onViewTap(mImageView, x, y)
          }
          if (displayRect != null) {
            // Check to see if the user tapped on the photo
            if (displayRect.contains(x, y)) {
              val xResult = (
                (x - displayRect.left) /
                  displayRect.width()
                )
              val yResult = (
                (y - displayRect.top) /
                  displayRect.height()
                )
              if (mPhotoTapListener != null) {
                mPhotoTapListener!!.onPhotoTap(mImageView, xResult, yResult)
              }
              return true
            } else {
              if (mOutsidePhotoTapListener != null) {
                mOutsidePhotoTapListener!!.onOutsidePhotoTap(mImageView)
              }
            }
          }
          return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
          if (isDraggingToExit) return false
          try {
            val scale: Float = scale
            val x = ev.x
            val y = ev.y
            if (scale < mediumScale) {
              setScale(mediumScale, x, y, true)
            } else if (scale >= mediumScale && scale < maximumScale) {
              setScale(maximumScale, x, y, true)
            } else {
              setScale(minimumScale, x, y, true)
            }
          } catch (e: ArrayIndexOutOfBoundsException) {
            // Can sometimes happen when getX() and getY() is called
          }
          return true
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
          // Wait for the confirmed onDoubleTap() instead
          return false
        }
      })
    }
  }

  fun setOnDoubleTapListener(newOnDoubleTapListener: GestureDetector.OnDoubleTapListener?) {
    mGestureDetector.setOnDoubleTapListener(newOnDoubleTapListener)
  }

  fun setOnScaleChangeListener(onScaleChangeListener: OnScaleChangedListener?) {
    mScaleChangeListener = onScaleChangeListener
  }

  fun setOnSingleFlingListener(onSingleFlingListener: OnSingleFlingListener?) {
    mSingleFlingListener = onSingleFlingListener
  }

  val displayRect: RectF?
    get() {
      checkMatrixBounds()
      return getDisplayRect(drawMatrix)
    }

  fun setDisplayMatrix(finalMatrix: Matrix?): Boolean {
    requireNotNull(finalMatrix) { "Matrix cannot be null" }
    if (mImageView.drawable == null) {
      return false
    }
    mSuppMatrix.set(finalMatrix)
    checkAndDisplayMatrix()
    return true
  }

  fun setBaseRotation(degrees: Float) {
    mBaseRotation = degrees % 360
    update()
    setRotationBy(mBaseRotation)
    checkAndDisplayMatrix()
  }

  fun setRotationTo(degrees: Float) {
    mSuppMatrix.setRotate(degrees % 360)
    checkAndDisplayMatrix()
  }

  fun setRotationBy(degrees: Float) {
    mSuppMatrix.postRotate(degrees % 360)
    checkAndDisplayMatrix()
  }

  var minimumScale: Float
    get() = mMinScale
    set(minimumScale) {
      checkZoomLevels(minimumScale, mMidScale, mMaxScale)
      mMinScale = minimumScale
    }
  var mediumScale: Float
    get() = mMidScale
    set(mediumScale) {
      checkZoomLevels(mMinScale, mediumScale, mMaxScale)
      mMidScale = mediumScale
    }
  var maximumScale: Float
    get() = mMaxScale
    set(maximumScale) {
      checkZoomLevels(mMinScale, mMidScale, maximumScale)
      mMaxScale = maximumScale
    }

  var scale: Float
    get() = sqrt(
      (
        getValue(mSuppMatrix, Matrix.MSCALE_X).toDouble().pow(2.0).toFloat() + getValue(
          mSuppMatrix,
          Matrix.MSKEW_Y,
        ).toDouble().pow(2.0).toFloat()
        ).toDouble(),
    ).toFloat()
    set(scale) {
      setScale(scale, false)
    }

  var scaleType: ScaleType
    get() = mScaleType
    set(scaleType) {
      if (isSupportedScaleType(scaleType) && scaleType != mScaleType) {
        mScaleType = scaleType
        update()
      }
    }

  override fun onLayoutChange(
    v: View,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    oldLeft: Int,
    oldTop: Int,
    oldRight: Int,
    oldBottom: Int,
  ) {
    // Update our base matrix, as the bounds have changed
    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
      updateBaseMatrix(mImageView.drawable)
    }
  }

  override fun onTouch(v: View, ev: MotionEvent): Boolean {
    var handled = false
    val imageView = v as? ImageView ?: return false
    if (!isZoomEnabled || !hasDrawable(imageView)) return false

    val dragDetector = mScaleDragDetector
    val wasScaling = dragDetector?.isScaling == true
    val shouldScale = scale < mMinScale
    val canDragExit = ev.pointerCount == 1 && !wasScaling && !shouldScale

    Log.d("myLogD", "onTouch:, $canDragExit")

    // ---- 拖拽退出逻辑 ----
    if (canDragExit) {
      handled = onDragExit(v,ev)
    }

    // ---- 常规缩放 / 拖拽逻辑 ----
    if (!handled) {
      handled = onNormalEvent(v,ev,dragDetector)
    }
    return handled
  }

  /**
   * 拖拽退出事件
   */
  private fun onDragExit(v: View, ev: MotionEvent): Boolean{
    var handled = false
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        Log.d("myLogD", "ACTION_DOWN: ${System.currentTimeMillis()}")
        lastMotionX = ev.rawX
        lastMotionY = ev.rawY
        totalTranslateX = 0f
        totalTranslateY = 0f
        isDraggingToExit = false
        hasBegunNormalDrag = false
        hasCheckedDragType = false
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = ev.rawX - lastMotionX
        val dy = ev.rawY - lastMotionY
        //计算是否拖拽到边界
        val rect = displayRect ?: return handled
        val isScrollDown = dy > 0
        val canScrollVertically = rect.top == 0f && isScrollDown ||
                rect.bottom == getImageViewHeight(mImageView).toFloat() && !isScrollDown
        // 检查是否开始拖拽退出
        //只有未开始拖拽且不是拖拽关闭状态，并且拖拽距离大于防抖距离时才认为开始拖拽
        if (!hasCheckedDragType) {
          hasCheckedDragType = true
          if (!hasBegunNormalDrag || !isDraggingToExit && abs(dy) > touchSlop * 2) {
            if (canScrollVertically || rect.height() <= getImageViewHeight(mImageView)) {
              isDraggingToExit = true
              v.parent?.requestDisallowInterceptTouchEvent(true)
            } else {
              hasBegunNormalDrag = true
            }
          }
        }

        if (isDraggingToExit) {
          totalTranslateX += dx
          totalTranslateY += dy
          val progress = abs(totalTranslateY) / exitThreshold
          val normalDrag = mOnViewExitListener?.onDrag(totalTranslateX,totalTranslateY,progress.coerceIn(0f, 1f),mImageView)

          if (normalDrag == true) {
            val totalRate = abs(totalTranslateY) / mImageView.height
            val scale = 1 - totalRate.coerceIn(0f, 1f - minScaleOnExit)
            val alpha = (1 - totalRate * 2.5f).coerceIn(0.2f, 1f)
            mImageView.apply {
              scaleX = scale
              scaleY = scale
              translationX = totalTranslateX
              translationY = totalTranslateY
            }
            exitParentView?.alpha = alpha
          }


          lastMotionX = ev.rawX
          lastMotionY = ev.rawY
          handled = true
        }else if (canScrollVertically || totalTranslateY != 0f) {
          // 计算下一步累计距离
          val nextTotalY = totalTranslateY + dy

          // 基于累计距离计算阻尼
          val resistance = getDragResistance(abs(nextTotalY))

          // 3. 应用当前增量 * 阻尼
          totalTranslateY = dy * resistance
          totalTranslateX = dx * resistance


          mImageView.translationY = totalTranslateY
          mImageView.translationX = totalTranslateX
        }else{
          lastMotionX = ev.rawX
          lastMotionY = ev.rawY
        }
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        Log.d("myLogD", "ACTION_UP: ${System.currentTimeMillis()} : $totalTranslateY , $exitThreshold : $touchSlop")
        if (isDraggingToExit) {
          isDraggingToExit = false
          val exceedThreshold = abs(totalTranslateY) / exitThreshold
          if (exceedThreshold >= 1) {
            mOnViewExitListener?.onExit()
          } else {
            val restore = mOnViewExitListener?.onRestore(exceedThreshold) ?: true
            if (restore) animateViewBack()
          }
          handled = true
        }else if (totalTranslateY != 0f){
          animateViewBack()
        }
      }
    }

    return handled
  }

  /**
   * 距离越大返回越小（指数衰减，更“紧绷”）
   * 例如：0 → 1.0， 200px → 0.73， 600px → 0.43， 1000px → 0.27
   */
  private fun getDragResistance(distanceY: Float): Float {
    // 控制衰减速率：越大阻尼越轻、越能拖动；越小阻尼越紧
    // 公式：e^{-√(distance/factor)}
    val resistance = exp(-sqrt(distanceY / factor))

    return resistance.coerceIn(0f, 1f)
  }

  private fun onNormalEvent(v: View,ev: MotionEvent,dragDetector : CustomGestureDetector?): Boolean {
    var handled = false
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        v.parent?.requestDisallowInterceptTouchEvent(true)
        cancelFling()
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val rect = displayRect ?: return handled
        if (scale < mMinScale) {
          v.post(AnimatedZoomRunnable(scale, mMinScale, rect.centerX(), rect.centerY()))
          handled = true
        } else if (scale > mMaxScale) {
          v.post(AnimatedZoomRunnable(scale, mMaxScale, rect.centerX(), rect.centerY()))
          handled = true
        }
      }
    }

    // 让拖拽/缩放检测器继续处理
    dragDetector?.let {
      val prevScaling = it.isScaling
      val prevDragging = it.isDragging
      handled = it.onTouchEvent(ev)
      mBlockParentIntercept = !(prevScaling || prevDragging || it.isScaling || it.isDragging)
    }

    // 让点击 / 双击检测器处理
    if (mGestureDetector.onTouchEvent(ev)) {
      handled = true
    }

    return  handled
  }

  /**
   * 重置动画
   */
  private fun animateViewBack() {
    mImageView.animate()
      .translationX(0f)
      .translationY(0f)
      .scaleX(1f)
      .scaleY(1f)
      .setDuration(200)
      .start()
    exitParentView?.animate()?.alpha(1f)?.setDuration(200)?.start()
  }

  fun setAllowParentInterceptOnEdge(allow: Boolean) {
    mAllowParentInterceptOnEdge = allow
  }

  fun setScaleLevels(minimumScale: Float, mediumScale: Float, maximumScale: Float) {
    checkZoomLevels(minimumScale, mediumScale, maximumScale)
    mMinScale = minimumScale
    mMidScale = mediumScale
    mMaxScale = maximumScale
  }

  fun setOnLongClickListener(listener: OnLongClickListener?) {
    mLongClickListener = listener
  }

  fun setOnClickListener(listener: View.OnClickListener?) {
    mOnClickListener = listener
  }

  fun setOnMatrixChangeListener(listener: OnMatrixChangedListener?) {
    mMatrixChangeListener = listener
  }

  fun setOnPhotoTapListener(listener: OnPhotoTapListener?) {
    mPhotoTapListener = listener
  }

  fun setOnOutsidePhotoTapListener(mOutsidePhotoTapListener: OnOutsidePhotoTapListener?) {
    this.mOutsidePhotoTapListener = mOutsidePhotoTapListener
  }

  fun setOnViewTapListener(listener: OnViewTapListener?) {
    mViewTapListener = listener
  }

  fun setOnViewDragListener(listener: OnViewDragListener?) {
    mOnViewDragListener = listener
  }
  fun setOnViewExitListener(listener: OnViewExitListener?) {
    mOnViewExitListener = listener
  }

  fun setScale(scale: Float, animate: Boolean) {
    setScale(
      scale,
      (
        mImageView.right / 2
        ).toFloat(),
      (
        mImageView.bottom / 2
        ).toFloat(),
      animate,
    )
  }

  fun setScale(
    scale: Float,
    focalX: Float,
    focalY: Float,
    animate: Boolean,
  ) {
    // Check to see if the scale is within bounds
    require(!(scale < mMinScale || scale > mMaxScale)) {
      "Scale must be within the range of minScale and maxScale"
    }
    if (animate) {
      mImageView.post(
        AnimatedZoomRunnable(
          this.scale,
          scale,
          focalX,
          focalY,
        ),
      )
    } else {
      mSuppMatrix.setScale(scale, scale, focalX, focalY)
      checkAndDisplayMatrix()
    }
  }

  /**
   * Set the zoom interpolator
   *
   * @param interpolator the zoom interpolator
   */
  fun setZoomInterpolator(interpolator: Interpolator) {
    mInterpolator = interpolator
  }

  var isZoomable: Boolean
    get() = isZoomEnabled
    set(zoomable) {
      isZoomEnabled = zoomable
      update()
    }

  fun update() {
    if (isZoomEnabled) {
      // Update the base matrix using the current drawable
      updateBaseMatrix(mImageView.drawable)
    } else {
      // Reset the Matrix...
      resetMatrix()
    }
  }

  /**
   * Get the display matrix
   *
   * @param matrix target matrix to copy to
   */
  fun getDisplayMatrix(matrix: Matrix?) {
    matrix?.set(drawMatrix)
  }

  /**
   * Get the current support matrix
   */
  fun getSuppMatrix(matrix: Matrix?) {
    matrix?.set(mSuppMatrix)
  }

  private val drawMatrix: Matrix
    get() {
      imageMatrix.set(mBaseMatrix)
      imageMatrix.postConcat(mSuppMatrix)
      return imageMatrix
    }

  fun setZoomTransitionDuration(milliseconds: Int) {
    mZoomDuration = milliseconds
  }

  /**
   * Helper method that 'unpacks' a Matrix and returns the required value
   *
   * @param matrix     Matrix to unpack
   * @param whichValue Which value from Matrix.M* to return
   * @return returned value
   */
  private fun getValue(matrix: Matrix, whichValue: Int): Float {
    matrix.getValues(mMatrixValues)
    return mMatrixValues[whichValue]
  }

  /**
   * Resets the Matrix back to FIT_CENTER, and then displays its contents
   */
  private fun resetMatrix() {
    mSuppMatrix.reset()
    setRotationBy(mBaseRotation)
    setImageViewMatrix(drawMatrix)
    checkMatrixBounds()
  }

  private fun setImageViewMatrix(matrix: Matrix) {
    mImageView.imageMatrix = matrix
    // Call MatrixChangedListener if needed
    val displayRect = getDisplayRect(matrix)
    if (mMatrixChangeListener != null) {
      val displayRect = getDisplayRect(matrix)
      if (displayRect != null) {
        mMatrixChangeListener!!.onMatrixChanged(displayRect)
      }
    }
  }

  /**
   * Helper method that simply checks the Matrix, and then displays the result
   */
  private fun checkAndDisplayMatrix() {
    if (checkMatrixBounds()) {
      setImageViewMatrix(drawMatrix)
    }
  }

  /**
   * Helper method that maps the supplied Matrix to the current Drawable
   *
   * @param matrix - Matrix to map Drawable against
   * @return RectF - Displayed Rectangle
   */
  private fun getDisplayRect(matrix: Matrix): RectF? {
    val d = mImageView.drawable
    if (d != null) {
      mDisplayRect[0f, 0f, d.intrinsicWidth.toFloat()] = d.intrinsicHeight.toFloat()
      matrix.mapRect(mDisplayRect)
      return mDisplayRect
    }
    return null
  }

  /**
   * Calculate Matrix for FIT_CENTER
   *
   * @param drawable - Drawable being displayed
   */
  private fun updateBaseMatrix(drawable: Drawable?) {
    if (drawable == null) {
      return
    }
    val viewWidth = getImageViewWidth(mImageView).toFloat()
    val viewHeight = getImageViewHeight(mImageView).toFloat()
    val drawableWidth = drawable.intrinsicWidth
    val drawableHeight = drawable.intrinsicHeight
    mBaseMatrix.reset()
    val widthScale = viewWidth / drawableWidth
    val heightScale = viewHeight / drawableHeight

    when (mScaleType) {
      ScaleType.CENTER -> {
        mBaseMatrix.postTranslate(
          (viewWidth - drawableWidth) / 2f,
          (viewHeight - drawableHeight) / 2f,
        )
      }

      ScaleType.CENTER_CROP -> {
        val scale = widthScale.coerceAtLeast(heightScale)
        mBaseMatrix.postScale(scale, scale)
        mBaseMatrix.postTranslate(
          (viewWidth - drawableWidth * scale) / 2f,
          (viewHeight - drawableHeight * scale) / 2f,
        )
      }

      ScaleType.CENTER_INSIDE -> {
        val scale = 1.0f.coerceAtMost(widthScale.coerceAtMost(heightScale))
        mBaseMatrix.postScale(scale, scale)
        mBaseMatrix.postTranslate(
          (viewWidth - drawableWidth * scale) / 2f,
          (viewHeight - drawableHeight * scale) / 2f,
        )
      }

      else -> {
        var mTempSrc = RectF(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat())
        val mTempDst = RectF(0f, 0f, viewWidth, viewHeight)
        if (mBaseRotation.toInt() % 180 != 0) {
          mTempSrc = RectF(0f, 0f, drawableHeight.toFloat(), drawableWidth.toFloat())
        }
        when (mScaleType) {
          ScaleType.FIT_CENTER -> mBaseMatrix.setRectToRect(
            mTempSrc,
            mTempDst,
            ScaleToFit.CENTER,
          )

          ScaleType.FIT_START -> mBaseMatrix.setRectToRect(
            mTempSrc,
            mTempDst,
            ScaleToFit.START,
          )

          ScaleType.FIT_END -> mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.END)
          ScaleType.FIT_XY -> mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.FILL)
          else -> {}
        }
      }
    }
    resetMatrix()
  }

  private fun checkMatrixBounds(): Boolean {
    val rect = getDisplayRect(drawMatrix) ?: return false
    val height = rect.height()
    val width = rect.width()
    var deltaX = 0f
    var deltaY = 0f
    val viewHeight = getImageViewHeight(mImageView)
    if (height <= viewHeight) {
      deltaY = when (mScaleType) {
        ScaleType.FIT_START -> -rect.top
        ScaleType.FIT_END -> viewHeight - height - rect.top
        else -> (viewHeight - height) / 2 - rect.top
      }
      mVerticalScrollEdge = VERTICAL_EDGE_BOTH
    } else if (rect.top > 0) {
      mVerticalScrollEdge = VERTICAL_EDGE_TOP
      deltaY = -rect.top
    } else if (rect.bottom < viewHeight) {
      mVerticalScrollEdge = VERTICAL_EDGE_BOTTOM
      deltaY = viewHeight - rect.bottom
    } else {
      mVerticalScrollEdge = VERTICAL_EDGE_NONE
    }
    val viewWidth = getImageViewWidth(mImageView)
    if (width <= viewWidth) {
      deltaX = when (mScaleType) {
        ScaleType.FIT_START -> -rect.left
        ScaleType.FIT_END -> viewWidth - width - rect.left
        else -> (viewWidth - width) / 2 - rect.left
      }
      mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
    } else if (rect.left > 0) {
      mHorizontalScrollEdge = HORIZONTAL_EDGE_LEFT
      deltaX = -rect.left
    } else if (rect.right < viewWidth) {
      deltaX = viewWidth - rect.right
      mHorizontalScrollEdge = HORIZONTAL_EDGE_RIGHT
    } else {
      mHorizontalScrollEdge = HORIZONTAL_EDGE_NONE
    }
    // Finally actually translate the matrix
    mSuppMatrix.postTranslate(deltaX, deltaY)
    return true
  }

  private fun getImageViewWidth(imageView: ImageView): Int {
    return imageView.width - imageView.paddingLeft - imageView.paddingRight
  }

  private fun getImageViewHeight(imageView: ImageView): Int {
    return imageView.height - imageView.paddingTop - imageView.paddingBottom
  }

  private fun cancelFling() {
    if (mCurrentFlingRunnable != null) {
      mCurrentFlingRunnable!!.cancelFling()
      mCurrentFlingRunnable = null
    }
  }

  internal inner class AnimatedZoomRunnable(
    private val mZoomStart: Float,
    private val mZoomEnd: Float,
    private val mFocalX: Float,
    private val mFocalY: Float,
  ) : Runnable {
    private val mStartTime: Long = System.currentTimeMillis()

    override fun run() {
      val t = interpolate()
      val scale = mZoomStart + t * (mZoomEnd - mZoomStart)
      val deltaScale: Float = scale / this@PhotoViewAttacher.scale
      onGestureListener.onScale(deltaScale, mFocalX, mFocalY)
      // We haven't hit our target scale yet, so post ourselves again
      if (t < 1f) {
        postOnAnimation(mImageView, this)
      }
    }

    private fun interpolate(): Float {
      var t = 1f * (System.currentTimeMillis() - mStartTime) / mZoomDuration
      t = Math.min(1f, t)
      t = mInterpolator.getInterpolation(t)
      return t
    }
  }

  private inner class FlingRunnable(context: Context?) : Runnable {
    private val mScroller: OverScroller
    private var mCurrentX = 0
    private var mCurrentY = 0

    init {
      mScroller = OverScroller(context)
    }

    fun cancelFling() {
      mScroller.forceFinished(true)
    }

    fun fling(
      viewWidth: Int,
      viewHeight: Int,
      velocityX: Int,
      velocityY: Int,
    ) {
      val rect: RectF = displayRect ?: return
      val startX = Math.round(-rect.left)
      val minX: Int
      val maxX: Int
      val minY: Int
      val maxY: Int
      if (viewWidth < rect.width()) {
        minX = 0
        maxX = Math.round(rect.width() - viewWidth)
      } else {
        maxX = startX
        minX = maxX
      }
      val startY = Math.round(-rect.top)
      if (viewHeight < rect.height()) {
        minY = 0
        maxY = Math.round(rect.height() - viewHeight)
      } else {
        maxY = startY
        minY = maxY
      }
      mCurrentX = startX
      mCurrentY = startY
      // If we actually can move, fling the scroller
      if (startX != maxX || startY != maxY) {
        mScroller.fling(
          startX, startY, velocityX, velocityY, minX,
          maxX, minY, maxY, 0, 0,
        )
      }
    }

    override fun run() {
      if (mScroller.isFinished) {
        return // remaining post that should not be handled
      }
      if (mScroller.computeScrollOffset()) {
        val newX = mScroller.currX
        val newY = mScroller.currY
        mSuppMatrix.postTranslate(
          (mCurrentX - newX).toFloat(),
          (mCurrentY - newY).toFloat(),
        )
        checkAndDisplayMatrix()
        mCurrentX = newX
        mCurrentY = newY
        // Post On animation
        postOnAnimation(mImageView, this)
      }
    }
  }

  // --- Drag-to-Exit Properties and Methods ---
  private var exitParentView: View? = null

  fun bindExitParent(parent: View) {
    exitParentView = parent
  }

  companion object {
    private const val DEFAULT_MAX_SCALE = 3.0f
    private const val DEFAULT_MID_SCALE = 1.75f
    private const val DEFAULT_MIN_SCALE = 1.0f
    private const val DEFAULT_ZOOM_DURATION = 200
    private const val HORIZONTAL_EDGE_NONE = -1
    private const val HORIZONTAL_EDGE_LEFT = 0
    private const val HORIZONTAL_EDGE_RIGHT = 1
    private const val HORIZONTAL_EDGE_BOTH = 2
    private const val VERTICAL_EDGE_NONE = -1
    private const val VERTICAL_EDGE_TOP = 0
    private const val VERTICAL_EDGE_BOTTOM = 1
    private const val VERTICAL_EDGE_BOTH = 2
    private const val SINGLE_TOUCH = 1
    private const val FACTOR = 1200
  }
}
