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
package com.photo.view.listener

/**
 * Interface definition for callback to be invoked when attached ImageView scale changes
 */
public fun interface OnScaleChangedListener {
  /**
   * Callback for when the scale changes
   *
   * @param scaleFactor the scale factor (less than 1 for zoom out, greater than 1 for zoom in)
   * @param focusX      focal point X position
   * @param focusY      focal point Y position
   */
  public fun onScaleChange(scaleFactor: Float, focusX: Float, focusY: Float)
}
