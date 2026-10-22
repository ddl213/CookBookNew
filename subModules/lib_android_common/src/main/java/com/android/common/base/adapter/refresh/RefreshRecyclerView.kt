package com.android.common.base.adapter.refresh

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.header.RefreshHeaderAdapter
import com.android.common.base.adapter.refresh.header.RefreshHeaderEnum
import com.android.common.base.adapter.refresh.header.listener.IRefreshHeader
import com.android.common.utils.LogUtils
import kotlin.math.abs

class RefreshRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var refreshHeader: IRefreshHeader? = null
    private var observedAdapterHeader: IRefreshHeader? = null
    private var onRefreshListener: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var activePointerId = INVALID_POINTER_ID
    private var totalDrag = 0f
    private var isDragging = false
    // 刷新可能在手势中途完成，仍须消费这个没有交给父类 DOWN 的事件序列。
    private var consumeGesture = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // RecyclerView 构造时可能调用 override；这些字段在初始化前也可安全地取 null。
    private var managedGrid: GridLayoutManager? = null
    private var managedSpanLookup: HeaderSpanSizeLookup? = null

    /** 更换或移除刷新头时，取消旧头的刷新、延时任务和拖拽。 */
    fun setRefreshHeader(header: IRefreshHeader?) {
        if (refreshHeader === header) return
        refreshHeader?.onReset()
        cancelRecyclerTouch()
        clearGesture()
        refreshHeader = header
        updateGridSpanLookup()
    }

    fun setOnRefreshListener(listener: (() -> Unit)?) {
        onRefreshListener = listener
        if (listener == null) {
            refreshHeader?.onReset()
            cancelRecyclerTouch()
            clearGesture()
        }
    }

    /** 刷新结束只更新刷新头，不改变用户当前的滚动位置。 */
    fun refreshComplete() {
        refreshHeader?.refreshComplete()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) beginGesture(e)
        if (consumeGesture || isDragging) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_MOVE && tryStartPull(e)) return true
        if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) switchPointer(e)
        val intercepted = super.onInterceptTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            clearGesture()
        }
        return intercepted
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) beginGesture(e)
        if (consumeGesture) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                clearGesture()
            }
            return true
        }
        val header = refreshHeader ?: return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && tryStartPull(e)) return true
                if (isDragging) {
                    val index = e.findPointerIndex(activePointerId)
                    if (index < 0) {
                        header.onReset()
                        cancelRecyclerTouch(e)
                        clearGesture()
                        return true
                    }
                    val y = e.getY(index)
                    val dy = y - lastY
                    lastY = y
                    totalDrag = (totalDrag + dy / 2f).coerceAtLeast(0f)
                    header.onMove(dy, totalDrag)
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> switchPointer(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    LogUtils.d("RefreshRecyclerView: 释放, totalDrag=$totalDrag")
                    clearGesture()
                    val listener = onRefreshListener
                    if (e.actionMasked == MotionEvent.ACTION_CANCEL || listener == null) {
                        header.onReset()
                    } else if (header.onRelease()) {
                        try {
                            listener.invoke()
                        } catch (error: Throwable) {
                            header.onReset()
                            throw error
                        }
                    }
                    return true
                }
                clearGesture()
            }
        }
        if (isDragging) return true
        return super.onTouchEvent(e)
    }

    private fun beginGesture(e: MotionEvent) {
        syncAdapterHeader()
        if (isDragging) refreshHeader?.onReset()
        clearGesture()
        activePointerId = e.getPointerId(0)
        downX = e.x
        downY = e.y
        lastY = e.y
        consumeGesture = refreshHeader?.state == RefreshHeaderEnum.REFRESHING
        if (consumeGesture) {
            cancelRecyclerTouch(e)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun tryStartPull(e: MotionEvent): Boolean {
        val header = refreshHeader ?: return false
        if (onRefreshListener == null || header.state == RefreshHeaderEnum.REFRESHING ||
            header.state == RefreshHeaderEnum.DONE || layoutManager?.canScrollVertically() != true) return false
        val index = e.findPointerIndex(activePointerId)
        if (index < 0) return false
        val x = e.getX(index)
        val y = e.getY(index)
        if (!isAtTop()) {
            // 从列表中部滑到顶部时，只累计到达顶部之后的下拉距离。
            downX = x
            downY = y
            return false
        }
        val dx = x - downX
        val dy = y - downY
        if (dy <= 0f || abs(dy) <= abs(dx)) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        if (dy <= touchSlop) return false

        // 接管时结束 RecyclerView 的滚动/速度跟踪/嵌套滚动，不能只吞掉它的 UP。
        cancelRecyclerTouch(e)
        // 原地前插零高 header 时 LayoutManager 可能仍以首个内容项为锚点。
        if (layoutManager?.findViewByPosition(0) == null) scrollToPosition(0)
        isDragging = true
        lastY = y
        totalDrag = (dy - touchSlop) / 2f
        header.onMove(dy, totalDrag)
        return true
    }

    private fun isAtTop(): Boolean {
        val layout = layoutManager
        if (layout is LinearLayoutManager && !layout.reverseLayout) {
            val first = layout.findViewByPosition(0) ?: run {
                val position = layout.findFirstVisibleItemPosition()
                if (position == NO_POSITION || contentPosition(adapter, position) != 0 ||
                    refreshHeader?.getVisibleHeight() != 0) return false
                layout.findViewByPosition(position) ?: return false
            }
            val margin = (first.layoutParams as? LayoutParams)?.topMargin ?: 0
            // 零高度 header 不被滚动条视为可见项，估算的 scrollOffset 可能仍大于零。
            return layout.getDecoratedTop(first) - margin >= paddingTop
        }
        return !canScrollVertically(-1)
    }

    private fun switchPointer(e: MotionEvent) {
        if (e.getPointerId(e.actionIndex) != activePointerId) return
        val nextIndex = if (e.actionIndex == 0) 1 else 0
        if (nextIndex >= e.pointerCount) return
        val oldX = e.getX(e.actionIndex)
        val oldY = e.getY(e.actionIndex)
        val nextX = e.getX(nextIndex)
        val nextY = e.getY(nextIndex)
        downX += nextX - oldX
        downY += nextY - oldY
        lastY = nextY
        activePointerId = e.getPointerId(nextIndex)
    }

    private fun cancelRecyclerTouch(source: MotionEvent? = null) {
        val now = SystemClock.uptimeMillis()
        val cancel = source?.let(MotionEvent::obtain)
            ?: MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        cancel.action = MotionEvent.ACTION_CANCEL
        try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
        stopScroll()
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
    }

    private fun clearGesture() {
        isDragging = false
        consumeGesture = false
        activePointerId = INVALID_POINTER_ID
        totalDrag = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        if (isDragging) refreshHeader?.onReset()
        clearGesture()
        super.onDetachedFromWindow()
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        observedAdapterHeader = findRefreshHeader(adapter)
        setRefreshHeader(observedAdapterHeader)
        super.setAdapter(adapter)
        updateGridSpanLookup()
    }

    override fun swapAdapter(adapter: Adapter<*>?, removeAndRecycleExistingViews: Boolean) {
        observedAdapterHeader = findRefreshHeader(adapter)
        setRefreshHeader(observedAdapterHeader)
        super.swapAdapter(adapter, removeAndRecycleExistingViews)
        updateGridSpanLookup()
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        restoreGridSpanLookup()
        super.setLayoutManager(layout)
        updateGridSpanLookup()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // ConcatAdapter 可原地增删刷新头，无需重新调用 setAdapter。
        syncAdapterHeader()
        // 调用方可在设置 adapter 之后再提供自己的 SpanSizeLookup。
        updateGridSpanLookup()
        super.onMeasure(widthSpec, heightSpec)
    }

    private fun findRefreshHeader(adapter: Adapter<*>?): IRefreshHeader? = when (adapter) {
        is RefreshHeaderAdapter -> adapter.header
        is ConcatAdapter -> adapter.adapters.firstNotNullOfOrNull { findRefreshHeader(it) }
        else -> null
    }

    private fun syncAdapterHeader() {
        val discoveredHeader = findRefreshHeader(adapter)
        // 显式 setRefreshHeader(null) 仍然有效，只有 Adapter 结构变化才重新自动绑定。
        if (discoveredHeader !== observedAdapterHeader) {
            observedAdapterHeader = discoveredHeader
            setRefreshHeader(discoveredHeader)
        }
    }

    private fun restoreGridSpanLookup() {
        val grid = managedGrid
        val lookup = managedSpanLookup
        if (grid != null && lookup != null && grid.spanSizeLookup === lookup) {
            grid.spanSizeLookup = lookup.delegate
        }
        managedGrid = null
        managedSpanLookup = null
    }

    private fun updateGridSpanLookup() {
        val grid = layoutManager as? GridLayoutManager
        if (grid == null || findRefreshHeader(adapter) == null) {
            restoreGridSpanLookup()
            return
        }
        if (managedGrid === grid && grid.spanSizeLookup === managedSpanLookup) return
        restoreGridSpanLookup()
        var delegate = grid.spanSizeLookup
        while (delegate is HeaderSpanSizeLookup) delegate = delegate.delegate
        val lookup = HeaderSpanSizeLookup(grid, delegate)
        managedGrid = grid
        managedSpanLookup = lookup
        grid.spanSizeLookup = lookup
    }

    private inner class HeaderSpanSizeLookup(
        private val grid: GridLayoutManager,
        val delegate: GridLayoutManager.SpanSizeLookup
    ) : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            val contentPosition = contentPosition(adapter, position)
            return if (contentPosition < 0) grid.spanCount else delegate.getSpanSize(contentPosition)
        }
    }

    /** 将 ConcatAdapter 全局位置换算为排除刷新头的内容位置；-1 表示刷新头。 */
    private fun contentPosition(adapter: Adapter<*>?, position: Int): Int {
        if (adapter is RefreshHeaderAdapter) return -1
        if (adapter !is ConcatAdapter) return position
        var remaining = position
        var contentBefore = 0
        for (child in adapter.adapters) {
            if (remaining < child.itemCount) {
                val local = contentPosition(child, remaining)
                return if (local < 0) -1 else contentBefore + local
            }
            remaining -= child.itemCount
            contentBefore += contentItemCount(child)
        }
        return contentBefore + remaining
    }

    private fun contentItemCount(adapter: Adapter<*>): Int = when (adapter) {
        is RefreshHeaderAdapter -> 0
        is ConcatAdapter -> adapter.adapters.sumOf { contentItemCount(it) }
        else -> adapter.itemCount
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val INVALID_POINTER_ID = -1
    }
}
