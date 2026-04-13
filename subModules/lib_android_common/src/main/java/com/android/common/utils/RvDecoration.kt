package com.android.common.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.annotation.Keep
import androidx.recyclerview.widget.RecyclerView
import com.android.common.ext.dp

/**
 * RecyclerView装饰器，用于设置item间距和绘制分割线
 */
class RvDecoration private constructor(private val builder: Builder) : RecyclerView.ItemDecoration(){

    /**
     * 计算每个item的偏移量（用于设置间距）
     */
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val type = parent.layoutManager

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val left = builder.getLeft()
        val right = builder.getRight()
        val bottom = builder.getBottom()
        val top = builder.getTop()

        when (type) {
            is androidx.recyclerview.widget.GridLayoutManager -> {
                val spanCount = type.spanCount // 列数
                val column = position % spanCount // 当前列
                outRect.left = column * left / spanCount // 列的左间距
                outRect.right = right - (column + 1) * right / spanCount // 列的右间距
                //只有第一排才显示顶部间距
                if (position < spanCount) {
                    outRect.top = top
                }
                outRect.bottom = bottom
            }

            is androidx.recyclerview.widget.LinearLayoutManager -> {
                val isVertical = type.orientation == RecyclerView.VERTICAL

                //判断是否是垂直排列
                if (isVertical){
                    //垂直排列
                    //只有当第一项时才显示顶部间距
                    if (position == 0) {
                        outRect.top = top
                    }
                    outRect.left = left
                }else{
                    //水平排列
                    //只有第一项时才显示左侧间距
                    if (position == 0) {
                        outRect.left = left
                    }
                    outRect.top = top
                }

                outRect.right = right
                outRect.bottom = bottom
            }
        }
    }

    /**
     * 在RecyclerView上绘制分割线
     */
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val dividerPaint = builder.getPaint()
        // 绘制分割线
        if (dividerPaint != null) {
            val layoutManager = parent.layoutManager ?: return

            // 最后一个item是否显示分割线
            val showLast = builder.getShowLastDivider()

            val childCount = parent.childCount.run { if (showLast) this else this - 1 }
            val dividerHeight = builder.getPaintHeight()
            val dividerSpacing = builder.getDividerSpacing()
            val dividerHorizontalSpacing = builder.getDividerHorizontalSpacing()

            // 用于水平布局的情况
            val isHorizontal = (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) &&
                              (layoutManager.orientation == RecyclerView.HORIZONTAL)

            for (i in 1 until childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) continue

                val params = child.layoutParams as RecyclerView.LayoutParams

                val left: Float
                val right: Float
                val top: Float
                val bottom: Float

                if (isHorizontal) {
                    // 水平布局
                    left = child.right.toFloat() + dividerSpacing
                    right = left + dividerHeight
                    top = child.top.toFloat() + dividerHorizontalSpacing
                    bottom = child.bottom.toFloat() - dividerHorizontalSpacing
                } else {
                    // 垂直布局
                    left = child.left.toFloat() + dividerHorizontalSpacing
                    right = child.right.toFloat()- dividerHorizontalSpacing
                    top = child.bottom.toFloat() + dividerSpacing
                    bottom = top + dividerHeight
                }

                c.drawRect(left, top, right, bottom, dividerPaint)
            }
        }
    }


    /**
     * Builder模式实现，用于构建RvDecoration实例
     */
    class Builder{
        //是否应用所有间距
        private var applyAll : Int? = null
        //间距
        private var top = 0
        private var bottom = 0
        private var left = 0
        private var right = 0
        //间距配置
        private var spacingConfig = SpacingConfig()

        // 分割线配置
        private var dividerPaint: Paint? = null
        private var dividerHeight = 1f

        /** 设置是否显示最后一个item的分割线 */
        private var showLastDivider = true
        private var dividerSpacing = 0f
        private var dividerHorizontalSpacing = 0f

        /**
         * 统一设置所有边距值
         */
        fun applyAll(applyAll : Int) = also{ this.applyAll = applyAll }

        /** 设置顶部间距 */
        fun setTop(top: Int) = also{ this.top = top.dp().toInt() }
        /** 获取顶部间距值 */
        fun getTop() = top

        /** 设置底部间距 */
        fun setBottom(bottom: Int)  = also{ this.bottom = bottom.dp().toInt() }
        /** 获取底部间距值 */
        fun getBottom() = bottom


        /** 设置左侧间距 */
        fun setLeft(left: Int)  = also{ this.left = left.dp().toInt() }
        /** 获取左侧间距值 */
        fun getLeft() = left


        /** 设置右侧间距 */
        fun setRight(right: Int)  = also{ this.right = right.dp().toInt() }
        /** 获取右侧间距值 */
        fun getRight() = right

        /** 同时设置垂直方向的上下间距 */
        fun setVertical(vertical: Int)  = also{
            this.top = vertical.dp().toInt()
            this.bottom = vertical.dp().toInt()
        }

        /** 同时设置水平方向的左右间距 */
        fun setHorizontal(horizontal: Int)  = also{
            this.left = horizontal.dp().toInt()
            this.right = horizontal.dp().toInt()
        }

        /** 设置分割线颜色和高度 */
        fun setDivider(color: Int, height: Float = 1f) = also {
            this.dividerPaint = Paint().apply {
                this.color = color
                strokeWidth = height.toFloat()
            }
            this.dividerHeight = height
        }

        /** 设置分割线画笔（允许更多自定义） */
        fun setDividerPaint(paint: Paint, height: Float = 1f) = also {
            this.dividerPaint = paint
            this.dividerHeight = height
        }

        /** 获取分割线画笔 */
        fun getPaint() = dividerPaint
        /** 获取分割线高度 */
        fun getPaintHeight() = dividerHeight

        /** 设置是否显示最后一个item的分割线 */
        fun setShowLastDivider(showLastDivider: Boolean) = also{ this.showLastDivider = showLastDivider }
        /** 获取是否显示最后一个item的分割线 */
        fun getShowLastDivider() = showLastDivider

        /** 设置分割线水平间距 */
        fun setDividerSpacing(dividerSpacing: Int) = also{ this.dividerSpacing = dividerSpacing.dp() }
        /** 获取分割线水平间距 */
        fun getDividerSpacing() = dividerSpacing

        /** 设置分割线水平方向的间距 */
        fun setDividerHorizontalSpacing(dividerHorizontalSpacing: Int) = also{ this.dividerHorizontalSpacing = dividerHorizontalSpacing.dp() }
        /**  获取分割线水平方向的间距 */
        fun getDividerHorizontalSpacing() = dividerHorizontalSpacing

        /** 获取间距配置 */
        internal fun getSpacingConfig() = spacingConfig

        /** 构建RvDecoration实例 */
        fun build() :RvDecoration{
            spacingConfig = if (applyAll == null){
                SpacingConfig(
                    topPx = top.dp().toInt(),
                    bottomPx = bottom.dp().toInt(),
                    leftPx = left.dp().toInt(),
                    rightPx = right.dp().toInt()
                )
            }else{
                val applyAll = applyAll!!.dp().toInt()
                SpacingConfig(
                    topPx = applyAll,
                    bottomPx = applyAll,
                    leftPx = applyAll,
                    rightPx = applyAll
                )
            }

            return RvDecoration(this)
        }
    }


    // 最终的不可变配置类，存储的是已经转换后的 Pixel 值
    @Keep
    internal data class SpacingConfig(
        val topPx: Int = 0,
        val bottomPx: Int = 0,
        val leftPx: Int = 0,
        val rightPx: Int = 0
    )
}
