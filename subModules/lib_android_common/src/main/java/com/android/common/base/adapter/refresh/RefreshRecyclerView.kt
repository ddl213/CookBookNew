package com.android.common.base.adapter.refresh

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.header.listener.IRefreshHeader
import kotlin.math.abs

class RefreshRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var refreshHeader: IRefreshHeader? = null
    private var onRefreshListener: (() -> Unit)? = null

    private var lastY = 0f
    private var totalDrag = 0f
    private var isDragging = false
    private var isRefreshing = false

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
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val header = refreshHeader ?: return super.onTouchEvent(e)

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.rawY
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = e.rawY - lastY
                lastY = e.rawY

                // 向下拉并且当前滑到顶部时拦截
                if (abs(dy) > 5 && !canScrollVertically(-1)) {
                    totalDrag += dy / 2f
                    isDragging = true
                    if (!isRefreshing) header.onMove(dy, totalDrag)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    LogUtils.d("释放")
                    val shouldRefresh = header.onRelease()
                    if (shouldRefresh && !isRefreshing) {
                        isRefreshing = true
                        onRefreshListener?.invoke()
                    }
                    totalDrag = 0f
                    // 必须调用 performClick() 保证无障碍兼容
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
        // 这里通常不需要额外逻辑，只要调用 super 即可
        return true
    }
}