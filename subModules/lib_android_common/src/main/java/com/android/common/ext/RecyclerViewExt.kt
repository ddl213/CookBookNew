package com.android.common.ext

import androidx.annotation.MainThread
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.AdapterBuilder
import com.android.common.base.adapter.BaseAdapter
import com.android.common.base.adapter.refresh.RefreshRecyclerView

fun RecyclerView.liner(orientation: Int = RecyclerView.VERTICAL, reverse: Boolean = false) = apply {
    layoutManager = LinearLayoutManager(context, orientation, reverse)
}

fun RecyclerView.grid(spanCount: Int = 2, orientation: Int = RecyclerView.VERTICAL, reverse: Boolean = false) = apply {
    layoutManager = GridLayoutManager(context, spanCount, orientation, reverse)
}

fun RefreshRecyclerView.liner(orientation: Int = RecyclerView.VERTICAL, reverse: Boolean = false) = apply {
    layoutManager = LinearLayoutManager(context, orientation, reverse)
}

fun RefreshRecyclerView.grid(spanCount: Int = 2, orientation: Int = RecyclerView.VERTICAL, reverse: Boolean = false) = apply {
    layoutManager = GridLayoutManager(context, spanCount, orientation, reverse)
}

/** 构建 Adapter；建议显式指定数据类型，以便差量比较和绑定回调获得准确类型。 */
inline fun <reified T : Any> RecyclerView.buildAdapter(block: AdapterBuilder<T>.() -> Unit): BaseAdapter<T> {
    val dataAdapter = AdapterBuilder<T>().apply(block).build()
    adapter = dataAdapter
    return dataAdapter
}

/** 构建带可选下拉刷新头的 Adapter；footer 暂不支持，传 true 会抛出异常。 */
inline fun <reified T : Any> RefreshRecyclerView.buildRefreshAdapter(
    header: Boolean = false,
    footer: Boolean = false,
    block: AdapterBuilder<T>.() -> Unit
): ConcatAdapter {
    val refreshAdapter = AdapterBuilder<T>().apply(block).buildRefresh(this, header, footer)
    adapter = refreshAdapter
    return refreshAdapter
}

/**
 * 替换完整数据列表，传入 null 或空列表可清空数据；适用于 RecyclerView 和 RefreshRecyclerView。
 * 自动查找 ConcatAdapter 中唯一的 BaseAdapter，刷新头等其他 Adapter 不受影响。
 *
 * 数据类型须与构建 Adapter 时一致。列表会复制浅快照，提交后不要修改参与差量比较的数据对象。
 * [commitCallback] 在列表提交完成后执行；若本次提交被后续提交取代，可能不会执行。
 * 未设置 Adapter、未找到 BaseAdapter 或存在多个 BaseAdapter 时抛出 IllegalStateException。
 */
@MainThread
fun RecyclerView.submitList(list: List<Any?>?, commitCallback: Runnable? = null) {
    requireDataAdapter("submitList").submitList(list, commitCallback)
}

/**
 * 向最新请求的数据列表追加 [items]，不需要持有 Adapter 变量。
 * 基于最近一次提交的快照追加，因此紧接尚未完成的 submitList 或 addItems 调用也不会丢失数据。
 * 数据类型须与构建 Adapter 时一致；查找规则与 [submitList] 相同。
 * [commitCallback] 沿用 ListAdapter 的提交语义，提交被后续请求取代时可能不会执行。
 */
@MainThread
fun RecyclerView.addItems(items: List<Any?>, commitCallback: Runnable? = null) {
    requireDataAdapter("addItems").addItems(items, commitCallback)
}

private fun RecyclerView.requireDataAdapter(operation: String): BaseAdapter<Any?> {
    val currentAdapter = checkNotNull(adapter) {
        "请先通过 buildAdapter、buildRefreshAdapter 或 setAdapter 设置 Adapter，再调用 $operation。"
    }
    val dataAdapters = currentAdapter.findDataAdapters()
    check(dataAdapters.isNotEmpty()) {
        "$operation 需要 BaseAdapter，当前 Adapter 中未找到可提交数据的 BaseAdapter。"
    }
    check(dataAdapters.size == 1) {
        "当前 ConcatAdapter 中存在多个 BaseAdapter，无法确定 $operation 的目标，请直接向目标 Adapter 提交数据。"
    }
    // RecyclerView 不携带数据泛型，调用方须保证列表元素类型与目标 Adapter 一致。
    @Suppress("UNCHECKED_CAST")
    return dataAdapters.single() as BaseAdapter<Any?>
}

private fun RecyclerView.Adapter<*>.findDataAdapters(): List<BaseAdapter<*>> = when (this) {
    is BaseAdapter<*> -> listOf(this)
    is ConcatAdapter -> adapters.flatMap { it.findDataAdapters() }
    else -> emptyList()
}
