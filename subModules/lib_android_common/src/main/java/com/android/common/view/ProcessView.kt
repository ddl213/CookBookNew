package com.android.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.android.common.R
import com.android.common.view.ProcessView.StartDirection.Companion.BOTTOM
import com.android.common.view.ProcessView.StartDirection.Companion.LEFT
import com.android.common.view.ProcessView.StartDirection.Companion.RIGHT
import com.android.common.view.ProcessView.StartDirection.Companion.TOP

class ProcessView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    def: Int = 0
) : View(context, attributeSet, def) {

    // 实际进度条的路径
    private val progressPath = Path()
    // 未绘制部分背景的完整路径
    private val backgroundPath = Path()
    // 最外层边框的路径
    private val outerBorderPath = Path()
    // 用于存储路径片段的目标路径
    private val dst = Path()

    // 实际进度条的画笔
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 未绘制部分背景的画笔
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 最外层边框的画笔
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pathMeasure = PathMeasure()


    // 总时长
    private var time: Long = 0L

    // 进度条的起始位置
    private var progressStart: Float = 0f
        set(value) {
            field = value.coerceIn(0f,progressEnd)
            invalidate() // 当进度改变时请求重绘
        }
    // 进度条的结束位置
    private var progressEnd: Float = 0f
        set(value) {
            field = value.coerceIn(progressStart, 1f)
            invalidate() // 当进度改变时请求重绘
        }

    // 当前保存的进度值 (0.0 到 1.0)
    private var savedProgress: Float = 0f

    // 可定制属性
    private var progressColor: Int = Color.BLUE
    private var progressWidth: Float = 10f

    // 背景颜色 (浅蓝色，20% 透明度)
    private var backgroundColor: Int = Color.TRANSPARENT
    // 背景宽度 (通常与进度条宽度相同)
    private var backgroundWidth: Float = 10f

    // 最外层边框颜色
    private var outerBorderColor: Int = Color.TRANSPARENT
    // 最外层边框宽度 (比内部线条细)
    private var outerBorderWidth: Float = 0f

    // 圆角半径 (默认为0，即无圆角)
    private var cornerRadius: Float = 0f

    // 用于管理当前动画实例
    private var currentAnimator: ValueAnimator? = null

    // 进度条的起始绘制方向
    private var startDirection: Int = TOP

    //是否指定起始时间
    private var isSpecifyStartTime: Boolean = false

    // 添加收回阶段标志
    private var isInRetractPhase: Boolean = false

    // 枚举定义进度条的起始绘制方向和路径方向
    sealed class StartDirection {
        companion object{
            // 左上角开始，顺时针绘制 (上 -> 右 -> 下 -> 左)
            const val TOP = 0
            // 右上角开始，向下绘制 (右 -> 下 -> 左 -> 上)
            const val RIGHT = 1
            // 右下角开始，向左绘制 (下 -> 左 -> 上 -> 右)
            const val BOTTOM = 2
            // 左下角开始，向上绘制 (左 -> 上 -> 右 -> 下)
            const val LEFT = 3
        }

    }


    init {
        // 初始化进度条画笔属性
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeCap = Paint.Cap.ROUND // 使线段末端为圆形
        progressPaint.strokeJoin = Paint.Join.ROUND // 使连接处为圆形

        // 初始化背景画笔属性
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeCap = Paint.Cap.ROUND
        backgroundPaint.strokeJoin = Paint.Join.ROUND

        // 初始化最外层边框画笔属性
        outerBorderPaint.style = Paint.Style.STROKE
        outerBorderPaint.strokeCap = Paint.Cap.ROUND
        outerBorderPaint.strokeJoin = Paint.Join.ROUND

        applyAttributes(attributeSet)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ProcessView)
        try {
            // 进度条属性
            progressColor = typedArray.getColor(R.styleable.ProcessView_progressColor, ContextCompat.getColor(context, android.R.color.holo_blue_light))
            progressWidth = typedArray.getDimension(R.styleable.ProcessView_progressWidth, progressWidth)

            // 背景条属性 (用于未绘制部分)
            backgroundColor = typedArray.getColor(R.styleable.ProcessView_backgroundColor, Color.TRANSPARENT)
            backgroundWidth = typedArray.getDimension(R.styleable.ProcessView_backgroundWidth, progressWidth) // 默认为进度条宽度

            // 最外层边框属性
            outerBorderColor = typedArray.getColor(R.styleable.ProcessView_outerBorderColor, Color.TRANSPARENT)
            outerBorderWidth = typedArray.getDimension(R.styleable.ProcessView_outerBorderWidth, 0f)

            // 圆角半径属性
            cornerRadius = typedArray.getDimension(R.styleable.ProcessView_cornerRadius, 30f)
            Log.d("myLogD", "applyAttributes: $cornerRadius")

            // 绘制方向属性
            startDirection = typedArray.getInt(R.styleable.ProcessView_startDirection, TOP)

            // 将属性应用到画笔
            progressPaint.color = progressColor
            progressPaint.strokeWidth = progressWidth

            backgroundPaint.color = backgroundColor
            backgroundPaint.strokeWidth = backgroundWidth

            outerBorderPaint.color = outerBorderColor
            outerBorderPaint.strokeWidth = outerBorderWidth

        } finally {
            typedArray.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldwh: Int) {
        super.onSizeChanged(w, h, oldw, oldwh)
        updatePaths()
    }

    private fun updatePaths() {
        // 重置所有路径
        progressPath.reset()
        backgroundPath.reset()
        outerBorderPath.reset()

        // 计算进度/背景路径的边界
        // 此路径位于进度线描边的“中间”
        // 我们需要确保矩形边界考虑描边宽度的一半，以便在视图内部绘制
        val progressHalfWidth = progressWidth / 2
        val innerRect = RectF(
            progressHalfWidth,
            progressHalfWidth,
            width - progressHalfWidth,
            height - progressHalfWidth
        )

        // 计算最外层边框路径的边界
        // 此路径应略微超出进度/背景路径
        // 我们需要考虑最外层边框宽度的一半来定位
        val outerBorderHalfWidth = outerBorderWidth / 2
        val outerRect = RectF(
            outerBorderHalfWidth,
            outerBorderHalfWidth,
            width - outerBorderHalfWidth,
            height - outerBorderHalfWidth
        )
        if (width >= height && cornerRadius > width / 2){
            cornerRadius = width / 2f
        }else if (height > width && cornerRadius > height / 2){
            cornerRadius = height / 2f
        }
        // 确保最外层边框的圆角在视觉上保持一致
        val adjustedOuterCornerRadius = cornerRadius + (outerBorderHalfWidth - progressHalfWidth)

        // 根据绘制方向构建进度路径
        buildRoundedRectPath(progressPath, innerRect, cornerRadius, startDirection)
        // 背景路径与进度路径相同
        backgroundPath.addPath(progressPath)

        // 根据绘制方向构建最外层边框路径
        buildRoundedRectPath(outerBorderPath, outerRect, adjustedOuterCornerRadius.coerceAtLeast(0f), startDirection)

        // PathMeasure 仅用于进度路径
        pathMeasure.setPath(progressPath, false)
    }

    /**
     * 构建一个圆角矩形路径，并支持自定义起始点和绘制方向。
     *
     * @param path 要构建的Path对象。
     * @param rectF 定义矩形边界的RectF对象。
     * @param radius 圆角半径。
     * @param direction 绘制方向枚举。
     */
    private fun buildRoundedRectPath(path: Path, rectF: RectF, radius: Float, direction: Int) {
        val left = rectF.left
        val top = rectF.top
        val right = rectF.right
        val bottom = rectF.bottom


        when (direction) {
            TOP -> {
                // 左上角开始，顺时针绘制 (上 -> 右 -> 下 -> 左)
                // 绘制起点：左上角圆角的右侧切点
                path.moveTo(left + radius, top)
            }
            RIGHT -> {
                // 右上角开始，向下绘制 (右 -> 下 -> 左 -> 上)
                // 绘制起点：右上角圆角的下侧切点
                path.moveTo(right, top - radius)
            }
            BOTTOM -> {
                // 右下角开始，向左绘制 (下 -> 左 -> 上 -> 右)
                // 绘制起点：右下角圆角的上侧切点
                path.moveTo(right - radius, bottom)
            }
            LEFT -> {
                // 左下角开始，向上绘制 (左 -> 上 -> 右 -> 下)
                // 绘制起点：左下角圆角的上侧切点
                path.moveTo(left, bottom - radius)
            }
        }
        initPath(path,radius,direction, left, top, right, bottom)

        path.close()
    }

    private fun initPath(path: Path, radius: Float,direction: Int,left : Float,top : Float,right : Float,bottom : Float){
        // 定义四个角的圆弧矩形边界
        val topLeftOval = RectF(left, top, left + 2 * radius, top + 2 * radius)
        val topRightOval = RectF(right - 2 * radius, top, right, top + 2 * radius)
        val bottomRightOval = RectF(right - 2 * radius, bottom - 2 * radius, right, bottom)
        val bottomLeftOval = RectF(left, bottom - 2 * radius, left + 2 * radius, bottom)
        var addPathCount = 0
        var currentDirection = direction

        while (addPathCount < 4){
            when(currentDirection){
                TOP ->{
                    // 从当前点水平绘制到右上角圆弧起点
                    path.lineTo(right - radius, top)
                    // 添加右上角顺时针90度圆弧 (起始角度270，扫过角度90)
                    path.arcTo(topRightOval, 270f, 90f, false)
                }
                RIGHT ->{
                    // 从当前点垂直绘制到右下角圆弧起点
                    path.lineTo(right, bottom - radius)
                    // 添加右下角顺时针90度圆弧 (起始角度0，扫过角度90)
                    path.arcTo(bottomRightOval, 0f, 90f, false)
                }
                BOTTOM ->{
                    // 从当前点水平绘制到左下角圆弧起点
                    path.lineTo(left + radius, bottom)
                    // 添加左下角顺时针90度圆弧 (起始角度90，扫过角度90)
                    path.arcTo(bottomLeftOval, 90f, 90f, false)
                }
                LEFT->{
                    // 从当前点垂直绘制到左上角圆弧起点
                    path.lineTo(left, top + radius)
                    // 添加左上角顺时针90度圆弧 (起始角度180，扫过角度90)
                    path.arcTo(topLeftOval, 180f, 90f, false)
                    currentDirection = -1
                }
            }
            currentDirection++
            addPathCount++
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // 确保 progressPath 已经构建
        if (progressPath.isEmpty) return

        // 设置 PathMeasure 并获取路径总长度
        pathMeasure.setPath(progressPath, false)
        val pathLength = pathMeasure.length

        // 1. 始终先绘制完整的背景
        canvas.drawPath(progressPath, backgroundPaint)

        // 2. 绘制进度条（根据不同的阶段采用不同的绘制策略）
        dst.reset()

        if (isInRetractPhase) {
            // 收回阶段：绘制从0到progressStart的部分（用背景色覆盖）
            // 这样就能实现从开头逐渐收回的效果
            if (progressStart > 0) {
                pathMeasure.getSegment(0f, pathLength * progressStart, dst, true)
                canvas.drawPath(dst, backgroundPaint)
            }

            // 绘制从progressStart到progressEnd的部分（用进度条颜色）
            if (progressStart < progressEnd) {
                dst.reset()
                pathMeasure.getSegment(pathLength * progressStart, pathLength * progressEnd, dst, true)
                canvas.drawPath(dst, progressPaint)
            }
        } else {
            // 正常阶段：只绘制从progressStart到progressEnd的进度条
            if (progressStart < progressEnd) {
                pathMeasure.getSegment(pathLength * progressStart, pathLength * progressEnd, dst, true)
                canvas.drawPath(dst, progressPaint)
            }
        }

        // 5. 绘制最外层边框 (如果有)
        if (outerBorderWidth > 0f) {
            canvas.drawPath(outerBorderPath, outerBorderPaint)
        }
    }

    /**
     * 设置进度条的进度。
     * 此方法现在主要供动画内部使用。
     * @param progress 一个介于 0.0 和 1.0 之间的浮点值，其中 1.0 表示完全完成。
     */
    private fun setProgressInternal(progress: Float) {
        this.progressStart = progress
        this.savedProgress = progress // 同时更新保存的进度
    }

    /**
     * 添加进度
     * @param progress 在当前进度的基础上添加指定的进度
     */
    fun addProgress(progress: Float) {
        val total = progress + this.progressStart
        setProgressInternal(total.coerceIn(0f,1f))
    }

    /**
     * 启动边框的进度动画。
     *
     * @param totalDuration 动画完成整个边框所需的总时长 (毫秒)。
     */
    private fun startProgressAnimation(totalDuration: Long, fromProgress: Float = savedProgress) {
        // 先停止任何正在进行的动画
        currentAnimator?.cancel()

        // 确保进度在有效范围内
        val startProgress = fromProgress.coerceIn(0f, 1f)

        // 计算剩余需要动画的进度
        val remainingProgress = 1f - startProgress

        // 如果已经完成则直接返回
        if (remainingProgress <= 0f) {
            setProgressInternal(1f)
            return
        }

        time = totalDuration

        val animator = ValueAnimator.ofFloat(startProgress, 1f).apply {
            duration = (totalDuration * remainingProgress).toLong()
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val segmentProgress = animation.animatedValue as Float
                setProgressInternal(segmentProgress)
            }
        }

        currentAnimator = animator
        animator.start()
    }

    /**
     * 停止当前进度动画。
     */
    fun stopProgressAnimation() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    /**
     * 将进度条重置到初始状态 (进度为 0)。
     */
    fun resetProgress() {
        stopProgressAnimation()
        setProgressInternal(0f)
    }

    /**
     * 从上次停止的位置继续进度动画。
     *
     * @param totalDuration 完成整个进度条所需的总时长 (毫秒)
     */
    fun start(totalDuration: Long = time) {
        startProgressAnimation(totalDuration, savedProgress)
    }


    /**
     * 自定义动画开始
     *
     * @param animator 自定义动画
     */
    fun startWithAnimator(animator : () -> ValueAnimator) {
        currentAnimator = animator.invoke()
        currentAnimator?.start()
    }

    fun setProgress(start : Float, end : Float){
        if (start != progressStart) {
            if (isInRetractPhase) isInRetractPhase = false
            setProgressInternal(start)
        }

        if (end != progressEnd) {
            if (isInRetractPhase.not()) isInRetractPhase = true
            progressEnd = end
        }

    }

    fun setProgressColor(userBackgroundColor : Boolean){
        progressPaint.color = if (userBackgroundColor) backgroundColor else progressColor
        backgroundPaint.color = if (!userBackgroundColor) backgroundColor else progressColor
    }
}
