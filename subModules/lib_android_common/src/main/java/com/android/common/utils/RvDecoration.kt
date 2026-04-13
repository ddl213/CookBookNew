package com.android.common.utils

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.annotation.Keep
import androidx.recyclerview.widget.RecyclerView
import com.android.common.ext.dp

class RvDecoration private constructor(private val builder: Builder) : RecyclerView.ItemDecoration(){

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
                val isVertical = type.orientation ==RecyclerView.VERTICAL

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



    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)

    }


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
        fun applyAll(applyAll : Int) = also{
            this.applyAll = applyAll
        }

        fun setTop(top: Int) = also{
            this.top = top.dp().toInt()
        }
        fun getTop() = top

        fun setBottom(bottom: Int)  = also{
            this.bottom = bottom.dp().toInt()
        }
        fun getBottom() = bottom

        fun setLeft(left: Int)  = also{
            this.left = left.dp().toInt()
        }
        fun getLeft() = left

        fun setRight(right: Int)  = also{
            this.right = right.dp().toInt()
        }
        fun getRight() = right

        fun setVertical(vertical: Int)  = also{
            this.top = vertical.dp().toInt()
            this.bottom = vertical.dp().toInt()
        }

        fun setHorizontal(horizontal: Int)  = also{
            this.left = horizontal.dp().toInt()
            this.right = horizontal.dp().toInt()
        }

        internal fun getSpacingConfig() = spacingConfig

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
