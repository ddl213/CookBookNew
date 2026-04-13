package com.android.common.base.adapter.listener

import android.view.View
import com.android.common.base.adapter.BaseAdapter

interface OnItemDoubleClickListener<T> {
    fun onItemDoubleClick(adapter: BaseAdapter<T>, view: View, position: Int)

}