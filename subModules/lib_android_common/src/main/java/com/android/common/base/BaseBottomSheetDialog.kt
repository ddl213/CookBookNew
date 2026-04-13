package com.android.common.base

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

abstract class BaseBottomSheetDialog<V : ViewBinding>(
    context: Context,
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> V
) : BottomSheetDialog(context) {

    private var _binding: V? = null//私有的binding用于获取传进来的binding
    //只读的binding，用于暴露出去
    protected val binding get() = _binding ?: throw IllegalStateException("Binding is null. Is the dialog shown or already destroyed?")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 请求无标题窗口
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = inflate.invoke(LayoutInflater.from(context), null, false)
        // 设置Dialog的布局
        setContentView(binding.root)

        // 设置背景透明，以便自定义布局的圆角等效果能显示出来
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // 默认点击外部可取消
        setCanceledOnTouchOutside(true)

        // 初始化视图，由子类实现具体逻辑
        initView(binding)
        initListener(binding)
        initData(binding)
    }

    /**
     * 初始化view
     */
    abstract fun initView(binding: V)

    /**
     * 初始化数据
     */
    open fun initData(binding: V){}

    /**
     * 初始化监听器
     */
    open fun initListener(binding: V){}



    /**
     * 设置Dialog的宽度和高度。
     *
     * @param width 宽度，可以是具体像素值或ViewGroup.LayoutParams.MATCH_PARENT/WRAP_CONTENT
     * @param height 高度，可以是具体像素值或ViewGroup.LayoutParams.MATCH_PARENT/WRAP_CONTENT
     */
    fun setWindowSize(width: Int, height: Int) {
        window?.setLayout(width, height)
    }

    /**
     * 设置Dialog是否可取消（点击返回键或外部）。
     *
     * @param cancelable true为可取消，false为不可取消
     */
    fun setDialogCancelable(cancelable: Boolean) {
        setCancelable(cancelable)
    }

    /**
     * 设置点击Dialog外部是否可取消。
     *
     * @param cancel true为可取消，false为不可取消
     */
    fun setDialogCanceledOnTouchOutside(cancel: Boolean) {
        setCanceledOnTouchOutside(cancel)
    }

    /**
     * 关闭并销毁Dialog。
     * 调用此方法后，Dialog将从屏幕上移除，并且其内部资源会被释放。
     * 如果希望再次显示Dialog，需要重新创建实例。
     */
    fun dismissDialog() {
        if (isSafe() && isShowing){
            dismiss()
        }
    }

    /**
     * 隐藏Dialog，但保留其状态。
     * 调用此方法后，Dialog将从屏幕上移除，但其内部状态和资源不会被释放。
     * 可以通过调用 show() 方法再次显示。
     */
    fun hideDialog() {
        if (isSafe() && isShowing){
            hide()
        }
    }

    /**
     * 判断当前是否为安全的，即当前Activity是否存活
     */
    private fun isSafe() : Boolean{
        // 捕获context为局部val，以便进行安全的智能转换
        val activityContext = context
        if (activityContext is Activity && (activityContext.isFinishing || activityContext.isDestroyed)) {
            return false // 如果Activity已不再有效，则不执行dismiss操作
        }

        return true
    }

    /**
     * 在Dialog停止时清空ViewBinding的引用，防止内存泄漏。
     */
    override fun onStop() {
        super.onStop()
        _binding = null
    }
}