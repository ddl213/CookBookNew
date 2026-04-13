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

import android.widget.ImageView
import com.photo.view.PhotoView

/**
 * 图片拖拽退出动画监听
 * 如果需要自定义回弹动画，或者拖拽缩放等动画，最好都使用自定义动画，否则搭配默认动画会出现异常
 */
interface OnViewExitListener {
  fun onDrag(totalTranslateX: Float,totalTranslateY: Float,progress: Float,img : ImageView) : Boolean // 拖拽进度 0~1
  fun onExit() // 拖拽超过阈值，退出

  /**
   * 拖拽距离不够，回弹
   * @return true: 自定义动画 false: 默认动画
   */
  fun onRestore(progress: Float) : Boolean
}
