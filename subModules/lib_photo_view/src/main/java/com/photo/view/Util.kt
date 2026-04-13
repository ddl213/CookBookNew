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

import android.view.MotionEvent
import android.widget.ImageView
import android.widget.ImageView.ScaleType

internal object Util {

  @JvmStatic
  fun checkZoomLevels(
    minZoom: Float,
    midZoom: Float,
    maxZoom: Float,
  ) {
    require(!(minZoom >= midZoom)) {
      "Minimum zoom has to be less than Medium zoom. " +
        "Call setMinimumZoom() with a more appropriate value"
    }
    require(!(midZoom >= maxZoom)) {
      "Medium zoom has to be less than Maximum zoom. " +
        "Call setMaximumZoom() with a more appropriate value"
    }
  }

  @JvmStatic
  fun hasDrawable(imageView: ImageView): Boolean {
    return imageView.drawable != null
  }

  @JvmStatic
  fun isSupportedScaleType(scaleType: ScaleType?): Boolean {
    if (scaleType == null) {
      return false
    }

    if (scaleType == ScaleType.MATRIX) {
      throw IllegalStateException("Matrix scale type is not supported")
    }
    return true
  }

  @JvmStatic
  fun getPointerIndex(action: Int): Int {
    return action and MotionEvent.ACTION_POINTER_INDEX_MASK shr
      MotionEvent.ACTION_POINTER_INDEX_SHIFT
  }
}
