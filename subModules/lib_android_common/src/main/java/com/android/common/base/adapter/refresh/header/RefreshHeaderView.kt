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

    // 当前状态
    private var currentState = RefreshHeaderEnum.NORMAL
    // 下拉刷新的触发高度阈值
    private var headerHeight = 0
    // 内容区域的实际高度
    private var contentHeight = 0
    // 当前下拉的进度 (0 ~ 1)
    private var currentProgress = 0f

    // 刷新动画
    private var refreshAnimator: ValueAnimator? = null
    // 收起动画
    private var collapseAnimator: ValueAnimator? = null

    private lateinit var process: CircleLoadingView
    private lateinit var tvDesc: TextView

    init {
        LayoutInflater.from(context).inflate(layoutId, this, true)
        initView()
        post {
            // 第一次 post：view 已 attach，但可能尚未 layout
            // 再 post 一次确保 measuredHeight 已正确计算
            post {
                contentHeight = measuredHeight
                headerHeight = contentHeight.coerceAtLeast(150.dp().toInt())
                LogUtils.d("RefreshHeaderView: contentHeight=$contentHeight, headerHeight=$headerHeight")
                // 初始状态：高度为 0，不可见
                setVisibleHeight(0)
            }
        }
    }

    private fun initView() {
        process = findViewById(R.id.process)
        tvDesc = findViewById(R.id.tv_desc)
        // 初始缩放为最小值
        process.scaleX = 0.3f
        process.scaleY = 0.3f
    }

    /**
     * 设置可见高度，同时触发 requestLayout
     */
    private fun setVisibleHeight(height: Int) {
        val lp = layoutParams ?: return
        // 高度未变化时跳过，避免下拉/回弹过程中每帧重复 requestLayout 打断触摸流
        if (lp.height == height) return
        lp.height = height
        layoutParams = lp
    }

    // ==================== IRefreshHeader 接口实现 ====================

    override fun getHeaderView(): View = this

    override fun getVisibleHeight(): Int = layoutParams?.height ?: 0

    override fun onReset() {
        LogUtils.d("RefreshHeaderView.onReset")
        currentState = RefreshHeaderEnum.NORMAL
        // 从当前高度动画收起
        animateCollapse {
            // 收起完成后重置所有状态
            tvDesc.text = "下拉刷新"
            process.resetProgress()
            process.scaleX = 0.3f
            process.scaleY = 0.3f
            currentProgress = 0f
        }
    }

    override fun onPrepare() {
        if (currentState != RefreshHeaderEnum.RELEASE_TO_REFRESH) {
            LogUtils.d("RefreshHeaderView.onPrepare")
            currentState = RefreshHeaderEnum.RELEASE_TO_REFRESH
            tvDesc.text = "松手即可刷新"
        }
    }

    override fun onRefreshing() {
        LogUtils.d("RefreshHeaderView.onRefreshing")
        currentState = RefreshHeaderEnum.REFRESHING
        tvDesc.text = "加载中..."
        // 固定高度为内容高度，确保刷新头完整显示
        setVisibleHeight(contentHeight)
        // 开始刷新动画
        startRefreshAnimation()
    }

    override fun onMove(offSet: Float, sumOffSet: Float) {
        // 只有在非刷新、非完成状态下才响应移动
        if (currentState == RefreshHeaderEnum.REFRESHING || currentState == RefreshHeaderEnum.DONE) {
            return
        }

        // 始终同步可见高度，向上推到 0 时也能平滑收起
        setVisibleHeight(sumOffSet.toInt().coerceAtLeast(0))

        if (sumOffSet > 0) {
            // 更新状态
            if (sumOffSet > headerHeight) {
                onPrepare()
            } else {
                if (currentState != RefreshHeaderEnum.NORMAL) {
                    currentState = RefreshHeaderEnum.NORMAL
                    tvDesc.text = "下拉刷新"
                }
            }
        }
        // 更新下拉进度和 loading 效果
        updatePullProgress(sumOffSet)
    }

    override fun onRelease(): Boolean {
        LogUtils.d("RefreshHeaderView.onRelease, currentState=$currentState, visibleHeight=${getVisibleHeight()}, headerHeight=$headerHeight")
        // 刷新状态或完成状态时，不做任何操作
        if (currentState == RefreshHeaderEnum.REFRESHING || currentState == RefreshHeaderEnum.DONE) {
            LogUtils.d("RefreshHeaderView.onRelease: ignored, already refreshing or done")
            return true
        }

        // 释放时直接检查当前高度是否达到刷新阈值
        val shouldRefresh = getVisibleHeight() >= headerHeight
        LogUtils.d("RefreshHeaderView.onRelease: shouldRefresh=$shouldRefresh")
        return if (shouldRefresh) {
            // 达到刷新阈值，进入刷新状态
            onRefreshing()
            true
        } else {
            // 未达到阈值，收起
            onReset()
            false
        }
    }

    override fun refreshComplete() {
        LogUtils.d("RefreshHeaderView.refreshComplete")
        currentState = RefreshHeaderEnum.DONE
        // 停止刷新动画
        stopRefreshAnimation()
        // 停在 100% 进度
        process.setProgress(0f, 1f, false)
        tvDesc.text = "数据加载完成"
        // 停留 1 秒后向上折叠消失
        postDelayed({
            animateCollapseFromContent {
                // 重置所有状态
                currentState = RefreshHeaderEnum.NORMAL
                tvDesc.text = "下拉刷新"
                process.resetProgress()
                process.scaleX = 0.3f
                process.scaleY = 0.3f
                currentProgress = 0f
            }
        }, 1000)
    }

    // ==================== 下拉进度更新 ====================

    private fun updatePullProgress(sumOffSet: Float) {
        currentProgress = (sumOffSet / headerHeight).coerceIn(0f, 1f)
        process.apply {
            // 显示下拉进度（0 → 100%）
            setProgress(0f, currentProgress, false)
            // 缩放效果：最小 0.3，最大 1.0
            val scale = currentProgress.coerceAtLeast(0.3f)
            scaleX = scale
            scaleY = scale
        }
    }

    // ==================== 刷新动画 ====================

    private fun startRefreshAnimation() {
        stopRefreshAnimation()
        refreshAnimator = ValueAnimator.ofFloat(0f, 2f).apply {
            duration = 2000 // 一个完整周期：0→1 (1s) + 1→2 (1s)
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                when {
                    // 前半段 0.0 → 1.0：进度从 0% 增长到 100%
                    value <= 1.0f -> {
                        process.setProgress(0f, value, false)
                    }
                    // 后半段 1.0 → 2.0：清空后从 0% 重新增长到 100%
                    else -> {
                        process.setProgress(0f, value - 1.0f, false)
                    }
                }
            }
            start()
        }
    }

    private fun stopRefreshAnimation() {
        refreshAnimator?.cancel()
        refreshAnimator = null
    }

    // ==================== 收起动画 ====================

    /**
     * 从当前高度动画收起至 0
     */
    private fun animateCollapse(end: () -> Unit) {
        // 刷新状态时禁止折叠
        if (currentState == RefreshHeaderEnum.REFRESHING) {
            LogUtils.d("RefreshHeaderView.animateCollapse: blocked, current state is REFRESHING")
            return
        }
        collapseAnimator?.cancel()
        val startHeight = layoutParams?.height ?: 0
        if (startHeight <= 0) {
            end.invoke()
            return
        }

        collapseAnimator = ValueAnimator.ofInt(startHeight, 0).apply {
            duration = 300
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val height = animation.animatedValue as Int
                setVisibleHeight(height)
            }

            doOnEnd {
                setVisibleHeight(0)
                end.invoke()
            }
            start()
        }
    }

    /**
     * 从 contentHeight 动画收起至 0（刷新完成专用）
     */
    private fun animateCollapseFromContent(end: () -> Unit) {
        // 刷新状态时禁止折叠
        if (currentState == RefreshHeaderEnum.REFRESHING) {
            LogUtils.d("RefreshHeaderView.animateCollapseFromContent: blocked, current state is REFRESHING")
            return
        }
        collapseAnimator?.cancel()
        val startHeight = contentHeight

        collapseAnimator = ValueAnimator.ofInt(startHeight, 0).apply {
            duration = 300
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val height = animation.animatedValue as Int
                setVisibleHeight(height)
            }

            doOnEnd {
                setVisibleHeight(0)
                end.invoke()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRefreshAnimation()
        collapseAnimator?.cancel()
        collapseAnimator = null
    }
}