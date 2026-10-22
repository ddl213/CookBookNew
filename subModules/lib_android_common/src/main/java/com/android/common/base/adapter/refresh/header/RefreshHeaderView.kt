package com.android.common.base.adapter.refresh.header

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.android.common.R
import com.android.common.base.adapter.refresh.header.listener.IRefreshHeader
import com.android.common.ext.dp
import com.android.common.utils.LogUtils
import com.android.common.view.CircleLoadingView

class RefreshHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    layoutId: Int = R.layout.common_layout_refresh_header
) : FrameLayout(context, attrs, defStyleAttr), IRefreshHeader {

    override var state = RefreshHeaderEnum.NORMAL
        private set
    private var headerHeight = 150.dp().toInt().coerceAtLeast(1)
    private var contentHeight = 0
    private var visibleHeightPx = 0
    private var currentProgress = 0f
    private var refreshAnimator: ValueAnimator? = null
    private var collapseAnimator: ValueAnimator? = null
    private val process: CircleLoadingView
    private val tvDesc: TextView

    private val completeRunnable = Runnable {
        if (state == RefreshHeaderEnum.DONE) {
            animateCollapseFromContent {
                state = RefreshHeaderEnum.NORMAL
                resetAppearance()
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(layoutId, this, true)
        process = findViewById(R.id.process)
        tvDesc = findViewById(R.id.tv_desc)
        resetAppearance()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 可见高度为 0 时也测量内容的自然高度，避免靠 post 的执行顺序初始化刷新阈值。
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val naturalHeight = measuredHeight
        if (contentHeight != naturalHeight) {
            contentHeight = naturalHeight
            headerHeight = contentHeight.coerceAtLeast(150.dp().toInt()).coerceAtLeast(1)
            LogUtils.d("RefreshHeaderView: contentHeight=$contentHeight, headerHeight=$headerHeight")
        }
        if (state == RefreshHeaderEnum.REFRESHING) {
            // 动态插入的头可能在第一次 measure 前已收到 release；首次测量后恢复内容高度。
            visibleHeightPx = contentHeight.coerceAtLeast(1)
            layoutParams?.height = visibleHeightPx
            setMeasuredDimension(measuredWidth, visibleHeightPx)
        } else {
            setMeasuredDimension(measuredWidth, resolveSize(visibleHeightPx, heightMeasureSpec))
        }
    }

    private fun setVisibleHeight(height: Int) {
        visibleHeightPx = height.coerceAtLeast(0)
        val lp = layoutParams
        if (lp == null) {
            requestLayout()
        } else if (lp.height != visibleHeightPx) {
            lp.height = visibleHeightPx
            layoutParams = lp
        }
    }

    override fun getHeaderView(): View = this

    override fun getVisibleHeight(): Int = visibleHeightPx

    override fun onReset() {
        LogUtils.d("RefreshHeaderView.onReset")
        cancelCompletion()
        cancelCollapseAnimation()
        stopRefreshAnimation()
        state = RefreshHeaderEnum.NORMAL
        animateCollapse { resetAppearance() }
    }

    override fun onPrepare() {
        if (state == RefreshHeaderEnum.REFRESHING || state == RefreshHeaderEnum.DONE) return
        if (state != RefreshHeaderEnum.RELEASE_TO_REFRESH) {
            LogUtils.d("RefreshHeaderView.onPrepare")
            state = RefreshHeaderEnum.RELEASE_TO_REFRESH
            tvDesc.text = "松手即可刷新"
        }
    }

    override fun onRefreshing() {
        LogUtils.d("RefreshHeaderView.onRefreshing")
        cancelCompletion()
        cancelCollapseAnimation()
        state = RefreshHeaderEnum.REFRESHING
        tvDesc.text = "加载中..."
        setVisibleHeight(contentHeight.coerceAtLeast(1))
        if (isAttachedToWindow) startRefreshAnimation()
    }

    override fun onMove(offSet: Float, sumOffSet: Float) {
        if (state == RefreshHeaderEnum.REFRESHING || state == RefreshHeaderEnum.DONE) return
        cancelCollapseAnimation()
        setVisibleHeight(sumOffSet.toInt())
        if (sumOffSet >= headerHeight) {
            onPrepare()
        } else {
            state = RefreshHeaderEnum.NORMAL
            tvDesc.text = "下拉刷新"
        }
        updatePullProgress(sumOffSet)
    }

    override fun onRelease(): Boolean {
        LogUtils.d("RefreshHeaderView.onRelease, currentState=$state, visibleHeight=${getVisibleHeight()}, headerHeight=$headerHeight")
        if (state == RefreshHeaderEnum.REFRESHING || state == RefreshHeaderEnum.DONE) {
            LogUtils.d("RefreshHeaderView.onRelease: ignored, already refreshing or done")
            return false
        }
        val shouldRefresh = getVisibleHeight() >= headerHeight
        LogUtils.d("RefreshHeaderView.onRelease: shouldRefresh=$shouldRefresh")
        if (shouldRefresh) onRefreshing() else onReset()
        return shouldRefresh
    }

    override fun refreshComplete() {
        if (state != RefreshHeaderEnum.REFRESHING) return
        LogUtils.d("RefreshHeaderView.refreshComplete")
        state = RefreshHeaderEnum.DONE
        stopRefreshAnimation()
        process.setProgress(0f, 1f, false)
        tvDesc.text = "数据加载完成"
        cancelCompletion()
        if (isAttachedToWindow) {
            postDelayed(completeRunnable, 1000)
        } else {
            resetImmediately()
        }
    }

    private fun updatePullProgress(sumOffSet: Float) {
        currentProgress = (sumOffSet / headerHeight).coerceIn(0f, 1f)
        process.setProgress(0f, currentProgress, false)
        val scale = currentProgress.coerceAtLeast(0.3f)
        process.scaleX = scale
        process.scaleY = scale
    }

    private fun startRefreshAnimation() {
        stopRefreshAnimation()
        refreshAnimator = ValueAnimator.ofFloat(0f, 2f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                process.setProgress(0f, if (value <= 1f) value else value - 1f, false)
            }
            start()
        }
    }

    private fun stopRefreshAnimation() {
        refreshAnimator?.cancel()
        refreshAnimator = null
    }

    private fun cancelCompletion() {
        removeCallbacks(completeRunnable)
    }

    private fun cancelCollapseAnimation() {
        val animator = collapseAnimator
        collapseAnimator = null
        // ValueAnimator.cancel 也会回调 onAnimationEnd；先移除回调，旧状态才不会覆盖新拖拽。
        animator?.removeAllUpdateListeners()
        animator?.removeAllListeners()
        animator?.cancel()
    }

    private fun animateCollapse(end: () -> Unit) {
        if (state == RefreshHeaderEnum.REFRESHING) {
            LogUtils.d("RefreshHeaderView.animateCollapse: blocked, current state is REFRESHING")
            return
        }
        cancelCollapseAnimation()
        if (visibleHeightPx <= 0 || !isAttachedToWindow) {
            setVisibleHeight(0)
            end()
            return
        }
        collapseAnimator = ValueAnimator.ofInt(visibleHeightPx, 0).apply {
            duration = 300
            interpolator = LinearInterpolator()
            addUpdateListener { setVisibleHeight(it.animatedValue as Int) }
            doOnEnd {
                collapseAnimator = null
                setVisibleHeight(0)
                end()
            }
            start()
        }
    }

    private fun animateCollapseFromContent(end: () -> Unit) {
        if (state == RefreshHeaderEnum.REFRESHING) {
            LogUtils.d("RefreshHeaderView.animateCollapseFromContent: blocked, current state is REFRESHING")
            return
        }
        // 从实际可见高度收起，避免完成期间布局变化造成突然展开。
        animateCollapse(end)
    }

    private fun resetAppearance() {
        tvDesc.text = "下拉刷新"
        process.resetProgress()
        process.scaleX = 0.3f
        process.scaleY = 0.3f
        currentProgress = 0f
    }

    private fun resetImmediately() {
        cancelCompletion()
        cancelCollapseAnimation()
        stopRefreshAnimation()
        state = RefreshHeaderEnum.NORMAL
        setVisibleHeight(0)
        resetAppearance()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state == RefreshHeaderEnum.REFRESHING) startRefreshAnimation()
    }

    override fun onDetachedFromWindow() {
        cancelCompletion()
        cancelCollapseAnimation()
        stopRefreshAnimation()
        // 暂时移出窗口不等于请求已结束；保留刷新态，重挂载时恢复动画。
        if (state != RefreshHeaderEnum.REFRESHING) resetImmediately()
        super.onDetachedFromWindow()
    }
}
