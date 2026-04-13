package com.android.common.base.adapter.listener

import android.view.View
import com.android.common.base.adapter.BaseAdapter

interface OnItemScaleListener<T> {
    fun onItemScale(adapter: BaseAdapter<T>, view: View, position: Int, scaleFactor: Float?)

}