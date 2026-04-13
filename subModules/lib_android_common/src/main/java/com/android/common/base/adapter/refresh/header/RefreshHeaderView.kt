package com.android.common.base.adapter.refresh.header

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
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

    //当前状态
    private var currentState = RefreshHeaderEnum.NORMAL
    //下拉刷新的高度
    private var headerHeight = 0
    //内容区域的实际高度
    private var contentHeight = 0
    //当前下拉的进度
    private var currentProgress = 0f


    private lateinit var process : CircleLoadingView
    private lateinit var tvDesc : TextView

    init {
        LayoutInflater.from(context).inflate(layoutId, this, true)
        // 在布局完成后获取内容高度
        post {
            contentHeight = measuredHeight
            LogUtils.d("$contentHeight")
            headerHeight = contentHeight.coerceAtLeast(150.dp().toInt())
        }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0)
        initView()
    }

    /**
     * 初始化控件
     */
    fun initView() {
        process = findViewById(R.id.process)
        tvDesc = findViewById(R.id.tv_desc)
    }

    override fun getHeaderView(): View = this

    override fun getVisibleHeight(): Int = layoutParams.height

    override fun onReset() {
        LogUtils.d("onReset")
        reset {
            requestLayout()
        }
        currentState = RefreshHeaderEnum.NORMAL
    }

    override fun onPrepare() {
        LogUtils.d("onPrepare")
        if (currentState != RefreshHeaderEnum.RELEASE_TO_REFRESH) {
            currentState = RefreshHeaderEnum.RELEASE_TO_REFRESH
            prepare()
        }
    }

    override fun onRefreshing() {
        LogUtils.d("onRefreshing")
        refreshing()
        currentState = RefreshHeaderEnum.REFRESHING
        // 刷新时保持内容区域完整显示
        layoutParams.height = contentHeight
        requestLayout()

    }

    override fun onMove(offSet: Float, sumOffSet: Float) {
        if (sumOffSet > 0) {
            layoutParams.height = sumOffSet.toInt()
            requestLayout()
            // 只有在非刷新状态时才更新状态
            if (currentState != RefreshHeaderEnum.REFRESHING) {
                if (sumOffSet > headerHeight) {
                    onPrepare()
                } else {
                    if (currentState != RefreshHeaderEnum.NORMAL) {
                        currentState = RefreshHeaderEnum.NORMAL
                    }
                }
            }
        }
        move(offSet,sumOffSet)
    }

    override fun onRelease(): Boolean {
        LogUtils.d("reset :${layoutParams.height > headerHeight}")
        return if (currentState == RefreshHeaderEnum.RELEASE_TO_REFRESH) {
            onRefreshing()
            true
        } else {
            onReset()
            false
        }
    }

    override fun refreshComplete() {
        LogUtils.d("refreshComplete")
        currentState = RefreshHeaderEnum.DONE
        refreshComplete{
            postDelayed({ onReset() }, 500)
        }
    }




    /**
     * 移动动画
     * @param offSet 移动距离
     * @param sumOffSet 总距离
     */
    fun move(offSet : Float,sumOffSet: Float){
        currentProgress = (sumOffSet / headerHeight).coerceIn(0f,1f)
        process.apply {
            addProgress(currentProgress)
            LogUtils.setLimit(LogUtils.COUNT_LIMIT)
            LogUtils.d("move: $currentProgress")
            scaleX = currentProgress.coerceAtLeast(0.3f)
            scaleY = currentProgress.coerceAtLeast(0.3f)
        }
    }

    /**
     * 刷新准备
     */
    fun prepare(){
        tvDesc.text = "松开后刷新"
    }

    /**
     * 刷新动画
     */
    fun refreshing(){
        tvDesc.text = "正在刷新..."
        process.startWithAnimator {
            // 创建一个从0到2的动画值，前半段表示增长，后半段表示收回
            ValueAnimator.ofFloat(0f, 2f).apply {
                this.duration = 1000 * 2  // 总时长为两倍
                interpolator = LinearInterpolator()
                repeatCount = 3
                repeatMode = ValueAnimator.RESTART

                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float

                    when {
                        // 前半段：0.0 -> 1.0，进度条从0开始增长
                        value <= 1.0f -> {
                            process.setProgress(0f, value, false)
                        }
                        // 后半段：1.0 -> 2.0，进度条从开头逐渐收回
                        else -> {
                            // 收回阶段，进度条保持满的(1.0)，但收回的部分逐渐增加
                            process.setProgress(value - 1.0f, 1.0f, true)
                        }
                    }
                }
            }

        }
    }


    /**
     * 刷新完成动画
     * @param end 刷新完成回调
     */
    fun refreshComplete(end: () -> Unit) {
        tvDesc.text = "刷新完成"

    }


    /**
     * 重置动画
     * @param end 动画结束回调
     */
    fun reset(end : () -> Unit){

        ValueAnimator.ofFloat(currentProgress, 0f).apply {
            duration = 300
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                Log.d("myLogD", "reset: $currentProgress")
                val progress = animation.animatedValue as Float

                // 同时处理高度和缩放
                val currentHeight = layoutParams.height
                layoutParams.height = (currentHeight * progress).toInt()
                requestLayout()

                process.scaleX = progress
                process.scaleY = progress
            }

            doOnEnd {
                end.invoke()
            }

            start()
        }
    }
}