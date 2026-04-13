package com.android.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.android.common.R

class CircleLoadingView@JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attributeSet, defStyleAttr) {

    // 画笔
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 颜色
    private var progressColor: Int = Color.BLUE
    private var backgroundColor: Int = Color.LTGRAY

    // 尺寸
    private var strokeWidth: Float = 10f
    private var circleRadius: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    // 进度相关
    private var currentProgress: Float = 0f
    private var isInRetractPhase: Boolean = false
    private var retractProgress: Float = 0f

    // 动画
    private var currentAnimator: ValueAnimator? = null

    init {
        applyAttributes(attributeSet)
        setupPaints()
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable. CircleLoadingView)
        try {
            progressColor = typedArray.getColor(
                R.styleable.CircleLoadingView_circleColor,
                ContextCompat.getColor(context, android.R.color.holo_blue_light)
            )
            backgroundColor = typedArray.getColor(
                R.styleable.CircleLoadingView_circleBgColor,
                Color.LTGRAY
            )
            strokeWidth = typedArray.getDimension(
                R.styleable.CircleLoadingView_strokeWidth,
                10f
            )
        } finally {
            typedArray.recycle()
        }
    }

    private fun setupPaints() {
        // 背景圆画笔
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = strokeWidth
        backgroundPaint.color = backgroundColor
        backgroundPaint.strokeCap = Paint.Cap.ROUND

        // 进度圆画笔
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = progressColor
        progressPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val minSize = minOf(w, h)
        circleRadius = (minSize - strokeWidth) / 2f
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (circleRadius <= 0) return

        // 1. 绘制背景圆
        canvas.drawCircle(centerX, centerY, circleRadius, backgroundPaint)

        // 2. 根据阶段绘制进度
        if (isInRetractPhase) {
            // 收回阶段：绘制从0到currentProgress的进度条，但收回的部分用背景色覆盖
            // 这样就能实现收回效果
            if (currentProgress > 0) {
                // 先绘制完整的进度条
                drawProgressArc(canvas, 0f, currentProgress, progressPaint)

                // 然后从开头开始用背景色覆盖一部分，实现收回效果
                if (retractProgress > 0 && retractProgress <= currentProgress) {
                    drawProgressArc(canvas, 0f, retractProgress, backgroundPaint)
                }
            }
        } else {
            // 正常阶段：绘制从0到currentProgress的进度
            if (currentProgress > 0) {
                drawProgressArc(canvas, 0f, currentProgress, progressPaint)
            }
        }
    }

    /**
     * 绘制圆弧进度
     * @param startProgress 起始进度 (0-1)
     * @param endProgress 结束进度 (0-1)
     * @param paint 使用的画笔
     */
    private fun drawProgressArc(
        canvas: Canvas,
        startProgress: Float,
        endProgress: Float,
        paint: Paint
    ) {
        val startAngle = -90f // 从顶部开始
        val sweepAngle = (endProgress - startProgress) * 360f

        if (sweepAngle > 0) {
            val rect = RectF(
                centerX - circleRadius,
                centerY - circleRadius,
                centerX + circleRadius,
                centerY + circleRadius
            )
            canvas.drawArc(rect, startAngle + startProgress * 360f, sweepAngle, false, paint)
        }
    }

    /**
     * 设置进度
     * @param start 起始进度 (0-1)，在收回阶段表示收回的起始位置
     * @param end 结束进度 (0-1)，在收回阶段表示进度的结束位置
     * @param isRetract 是否处于收回阶段
     */
    fun setProgress(start: Float, end: Float, isRetract: Boolean = false) {
        isInRetractPhase = isRetract
        retractProgress = start.coerceIn(0f, 1f)
        currentProgress = end.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * 添加进度
     */
    fun addProgress(progress: Float) {
        val total = progress + currentProgress
        setProgress(0f, total.coerceIn(0f, 1f), false)
    }

    /**
     * 开始自定义动画
     */
    fun startWithAnimator(animator: () -> ValueAnimator) {
        currentAnimator?.cancel()
        currentAnimator = animator.invoke()
        currentAnimator?.start()
    }

    /**
     * 停止动画
     */
    fun stopAnimation() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    /**
     * 重置进度
     */
    fun resetProgress() {
        stopAnimation()
        currentProgress = 0f
        retractProgress = 0f
        isInRetractPhase = false
        invalidate()
    }
}