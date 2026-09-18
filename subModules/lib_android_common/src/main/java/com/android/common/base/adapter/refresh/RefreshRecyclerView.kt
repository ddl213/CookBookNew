package com.android.common.base.adapter.refresh

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.header.listener.IRefreshHeader
import com.android.common.utils.LogUtils
import kotlin.math.abs

class RefreshRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var refreshHeader: IRefreshHeader? = null
    private var onRefreshListener: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var totalDrag = 0f
    private var isDragging = false
    private var isRefreshing = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * 绑定下拉刷新头
     */
    fun setRefreshHeader(header: IRefreshHeader) {
        refreshHeader = header
    }

    /**
     * 设置刷新回调
     */
    fun setOnRefreshListener(listener: () -> Unit) {
        onRefreshListener = listener
    }

    /**
     * 通知刷新完成
     */
    fun refreshComplete() {
        isRefreshing = false
        refreshHeader?.refreshComplete()
        // 刷新完成后，确保 RecyclerView 滚动到顶部，以便下次可以正常下拉
        postDelayed({
            scrollToPosition(0)
        }, 1500) // 等待 header 收起动画完成（1s 停留 + 300ms 动画 + 余量）
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 刷新中 或 已进入下拉拖拽：拦截整个触摸序列，避免 RecyclerView 的滚动状态机/子 View 抢走事件
        if (isRefreshing || isDragging) {
            // 同时禁止父 View（如 ViewPager）拦截，保证下拉/刷新事件不被页面切换抢占
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        // 顶部 + 纵向为主 + 向下：尽早禁止父 View 拦截，抢在 ViewPager 判定为页面切换之前锁定事件流
        if (e.actionMasked == MotionEvent.ACTION_MOVE && !canScrollVertically(-1)) {
            val dy = e.rawY - lastY
            val dx = e.rawX - lastX
            if (dy > 0 && abs(dy) > abs(dx)) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val header = refreshHeader ?: return super.onTouchEvent(e)

        // 刷新中：消费所有触摸事件，既不滚动列表也不再下拉 header
        if (isRefreshing) {
            return true
        }

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX
                lastY = e.rawY
                isDragging = false
                totalDrag = 0f
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - lastX
                val dy = e.rawY - lastY
                lastX = e.rawX
                lastY = e.rawY

                if (isDragging) {
                    // 已进入下拉拖拽：双向平滑跟随手指（向上收起、向下展开），忽略横向分量
                    // 持续禁止父 View 拦截，保证整段拖拽事件连续
                    parent?.requestDisallowInterceptTouchEvent(true)
                    totalDrag = (totalDrag + dy / 2f).coerceAtLeast(0f)
                    header.onMove(dy, totalDrag)
                    return true
                }

                // 未进入拖拽：仅在顶部、且纵向为主、且向下时才开始；横向滑动不拦截交给系统
                if (!canScrollVertically(-1) && abs(dy) > abs(dx) && dy > touchSlop) {
                    // 进入拖拽的瞬间锁定父 View，避免 ViewPager 抢占
                    parent?.requestDisallowInterceptTouchEvent(true)
                    totalDrag = (totalDrag + dy / 2f).coerceAtLeast(0f)
                    isDragging = true
                    header.onMove(dy, totalDrag)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 释放对父 View 的拦截锁定，恢复 ViewPager 等的正常手势
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging) {
                    isDragging = false
                    LogUtils.d("RefreshRecyclerView: 释放, totalDrag=$totalDrag")
                    val shouldRefresh = header.onRelease()
                    if (shouldRefresh) {
                        isRefreshing = true
                        onRefreshListener?.invoke()
                    }
                    totalDrag = 0f
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(e)
    }

    /**
     * 正确实现 performClick 以消除 Lint 警告 & 兼容辅助功能
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}