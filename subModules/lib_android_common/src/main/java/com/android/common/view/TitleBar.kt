package com.android.common.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.android.common.R
import com.android.common.databinding.CommonLayoutTitleBarBinding
import com.android.common.ext.visible

class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var mClickCallback: (() -> Unit)? = null
    private var mRightClickCallback: (() -> Unit)? = null
    private val mBinding = CommonLayoutTitleBarBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        mBinding.apply {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TitleBar)
            val leftSrc = typedArray.getResourceId(R.styleable.TitleBar_left_src, -1)
            val text = typedArray.getString(R.styleable.TitleBar_title_text)
            val textColor = typedArray.getColor(R.styleable.TitleBar_title_text_color, Color.parseColor("#0F141A"))
            val textSize = typedArray.getDimension(R.styleable.TitleBar_title_text_size, 18f)

            val showRightSrc = typedArray.hasValue(R.styleable.TitleBar_right_src)
            val showRightText = typedArray.hasValue(R.styleable.TitleBar_right_text)

            if (leftSrc != -1) {
                ivLeft.setImageResource(leftSrc)
            }

            if (text.isNullOrEmpty().not()) {
                tvTitle.text = text
                tvTitle.setTextColor(textColor)
                tvTitle.textSize = textSize
            }
            if (showRightSrc){
                ivRight.setImageResource(typedArray.getResourceId(R.styleable.TitleBar_right_src, -1))
                ivRight.visibility = VISIBLE
                ivRight.setOnClickListener {
                    mRightClickCallback?.invoke()
                }
            }
            else if (showRightText) {
                tvRight.text = typedArray.getString(R.styleable.TitleBar_right_text)
                tvRight.visibility = VISIBLE
                tvRight.setTextColor(textColor)
            }

            ivLeft.setOnClickListener {
                mClickCallback?.invoke()
            }

            typedArray.recycle()
        }
    }

    fun setLeftSrc(@DrawableRes resId: Int?) {
        if (resId != null) {
            mBinding.ivLeft.setImageResource(resId)
        } else {
            mBinding.ivLeft.setImageDrawable(null)
        }
    }

    fun setRightText(text: String) {
        mBinding.tvRight.text = text
        mBinding.tvRight.visible()
    }

    fun setTitle(text: String){
        mBinding.tvTitle.text = text
    }

    fun setLeftClick(block: () -> Unit) {
        mClickCallback = block
    }

    fun setRightClick(block: () -> Unit) {
        mRightClickCallback =  block
    }

}