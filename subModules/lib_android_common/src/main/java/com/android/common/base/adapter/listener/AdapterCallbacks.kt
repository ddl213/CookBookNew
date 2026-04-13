package com.android.common.base.adapter.listener

import android.view.View
import com.android.common.base.adapter.BaseAdapter


typealias OnItemClick<T> =
            (adapter: BaseAdapter<T>, view: View, position: Int) -> Unit

typealias OnItemLongClick<T> =
            (adapter: BaseAdapter<T>, view: View, position: Int) -> Boolean

typealias OnItemChildClick<T> =
            (adapter: BaseAdapter<T>, view: View, position: Int) -> Unit