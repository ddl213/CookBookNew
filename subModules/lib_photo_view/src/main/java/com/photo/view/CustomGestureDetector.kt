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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.photo.view.Util.getPointerIndex
import com.photo.view.listener.OnGestureListener
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Does a whole lot of gesture detecting.
 */
internal class CustomGestureDetector(
  context: Context,
  private val listener: OnGestureListener,
) {
  private var mActivePointerId = INVALID_POINTER_ID
  private var mActivePointerIndex = 0
  private val mDetector: ScaleGestureDetector
  private var mVelocityTracker: VelocityTracker? = null

  var isDragging = false
    private set

  private val configuration = ViewConfiguration.get(context)
  private val touchSlop: Float = configuration.scaledTouchSlop.toFloat()
  private val minimumVelocity: Float = configuration.scaledMinimumFlingVelocity.toFloat()

  private var mLastTouchX = 0f
  private var mLastTouchY = 0f

  private val scaleListener: OnScaleGestureListener = object : OnScaleGestureListener {
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    override fun onScale(detector: ScaleGestureDetector): Boolean {
      val scaleFactor = detector.scaleFactor
      if (java.lang.Float.isNaN(scaleFactor) ||
        java.lang.Float.isInfinite(scaleFactor)
      ) {
        return false
      }
      if (scaleFactor >= 0) {
        listener.onScale(
          scaleFactor = scaleFactor,
          focusX = detector.focusX,
          focusY = detector.focusY,
          dx = detector.focusX - lastFocusX,
          dy = detector.focusY - lastFocusY,
        )
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
      }
      return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
      lastFocusX = detector.focusX
      lastFocusY = detector.focusY
      return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
      // NO-OP
    }
  }

  init {
    mDetector = ScaleGestureDetector(context, scaleListener)
  }

  private fun getActiveX(ev: MotionEvent): Float {
    return try {
      ev.getX(mActivePointerIndex)
    } catch (e: Exception) {
      ev.x
    }
  }

  private fun getActiveY(ev: MotionEvent): Float {
    return try {
      ev.getY(mActivePointerIndex)
    } catch (e: Exception) {
      ev.y
    }
  }

  val isScaling: Boolean
    get() = mDetector.isInProgress

  fun onTouchEvent(ev: MotionEvent): Boolean {
    return try {
      mDetector.onTouchEvent(ev)
      processTouchEvent(ev)
    } catch (e: IllegalArgumentException) {
      // Fix for support lib bug, happening when onDestroy is called
      true
    }
  }

  private fun processTouchEvent(ev: MotionEvent): Boolean {
    val action = ev.action
    when (action and MotionEvent.ACTION_MASK) {
      MotionEvent.ACTION_DOWN -> {
        mActivePointerId = ev.getPointerId(0)
        mVelocityTracker = VelocityTracker.obtain()
        if (null != mVelocityTracker) {
          mVelocityTracker!!.addMovement(ev)
        }
        mLastTouchX = getActiveX(ev)
        mLastTouchY = getActiveY(ev)
        isDragging = false
      }

      MotionEvent.ACTION_MOVE -> {
        val x = getActiveX(ev)
        val y = getActiveY(ev)
        val dx = x - mLastTouchX
        val dy = y - mLastTouchY
        if (!isDragging) {
          // Use Pythagoras to see if drag length is larger than
          // touch slop
          isDragging = sqrt((dx * dx + dy * dy).toDouble()) >= touchSlop
        }
        if (isDragging) {
          listener.onDrag(dx, dy)
          mLastTouchX = x
          mLastTouchY = y
          if (null != mVelocityTracker) {
            mVelocityTracker!!.addMovement(ev)
          }
        }
      }

      MotionEvent.ACTION_CANCEL -> {
        mActivePointerId = INVALID_POINTER_ID
        // Recycle Velocity Tracker
        if (null != mVelocityTracker) {
          mVelocityTracker!!.recycle()
          mVelocityTracker = null
        }
      }

      MotionEvent.ACTION_UP -> {
        mActivePointerId = INVALID_POINTER_ID
        if (isDragging) {
          if (null != mVelocityTracker) {
            mLastTouchX = getActiveX(ev)
            mLastTouchY = getActiveY(ev)

            // Compute velocity within the last 1000ms
            mVelocityTracker!!.addMovement(ev)
            mVelocityTracker!!.computeCurrentVelocity(1000)
            val vX = mVelocityTracker!!.xVelocity
            val vY = mVelocityTracker!!
              .yVelocity

            // If the velocity is greater than minVelocity, call
            // listener
            if (abs(vX).coerceAtLeast(abs(vY)) >= minimumVelocity) {
              listener.onFling(
                startX = mLastTouchX,
                startY = mLastTouchY,
                velocityX = -vX,
                velocityY = -vY,
              )
            }
          }
        }

        // Recycle Velocity Tracker
        if (null != mVelocityTracker) {
          mVelocityTracker!!.recycle()
          mVelocityTracker = null
        }
      }

      MotionEvent.ACTION_POINTER_UP -> {
        val pointerIndex = getPointerIndex(ev.action)
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
          // This was our active pointer going up. Choose a new
          // active pointer and adjust accordingly.
          val newPointerIndex = if (pointerIndex == 0) 1 else 0
          mActivePointerId = ev.getPointerId(newPointerIndex)
          mLastTouchX = ev.getX(newPointerIndex)
          mLastTouchY = ev.getY(newPointerIndex)
        }
      }
    }
    mActivePointerIndex = ev
      .findPointerIndex(if (mActivePointerId != INVALID_POINTER_ID) mActivePointerId else 0)
    return true
  }

  companion object {
    private const val INVALID_POINTER_ID = -1
  }
}
