package com.android.common.view.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 一个通过 Builder 模式构建的、能够感知生命周期的多功能 Dialog。
 *
 * @author Gemini
 *
 * 使用方法:
 * SimpleDialog.with(this) // 'this' 是 Fragment 或 Activity
 * .setLayout(R.layout.my_dialog_layout)
 * .setInitView { rootView ->
 * // 初始化视图
 * }
 * .setClickViews(R.id.btn_confirm, R.id.btn_cancel)
 * .setInitClick { view ->
 * // 处理点击事件
 * }
 * .show()
 */
class SimpleDialog private constructor(
    private val builder: Builder
) : Dialog(builder.context, builder.themeResId), DefaultLifecycleObserver {

    init {
        // 注册生命周期观察者
        builder.lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<Dialog>.onCreate(savedInstanceState)

        // 设置布局
        if (builder.layoutId != 0) {
            setContentView(builder.layoutId)
        } else {
            throw IllegalArgumentException("Layout ID must be set using setLayout().")
        }

        // 设置窗口属性
        window?.apply {
            setGravity(builder.gravity)
            setLayout(builder.width, builder.height)
        }

        // 设置可取消属性
        setCancelable(builder.isCancelable)
        setCanceledOnTouchOutside(builder.isCancelableOnTouchOutside)

        // 初始化视图回调
        builder.onViewInit?.invoke(window!!.decorView)

        // 设置点击事件
        val clickListener = View.OnClickListener { view ->
            // 执行自定义点击回调
            builder.onViewClick?.invoke(view)
            // 如果设置为点击后自动关闭，则关闭对话框
            if (builder.dismissOnClick) {
                dismiss()
            }
        }
        builder.clickViewIds.forEach { id ->
            findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    /**
     * 当 LifecycleOwner (Activity/Fragment) 销毁时自动关闭 Dialog
     */
    override fun onDestroy(owner: LifecycleOwner) {
        if (isShowing) {
            dismiss()
        }
        owner.lifecycle.removeObserver(this)
    }

    // --- Builder 建造者类 ---
    class Builder(val lifecycleOwner: LifecycleOwner) {

        val context: Context = when (lifecycleOwner) {
            is ComponentActivity -> lifecycleOwner
            is Fragment -> lifecycleOwner.requireContext()
            else -> throw IllegalArgumentException("LifecycleOwner must be a ComponentActivity or Fragment.")
        }

        @LayoutRes
        internal var layoutId: Int = 0
        internal val clickViewIds = mutableSetOf<Int>()
        internal var onViewInit: ((rootView: View) -> Unit)? = null
        internal var onViewClick: ((clickedView: View) -> Unit)? = null

        @StyleRes
        internal var themeResId: Int = android.R.style.Theme_Dialog // 默认主题
        internal var width: Int = ViewGroup.LayoutParams.WRAP_CONTENT
        internal var height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
        internal var gravity: Int = Gravity.CENTER
        internal var isCancelable: Boolean = true
        internal var isCancelableOnTouchOutside: Boolean = true
        internal var dismissOnClick: Boolean = false

        /**
         * 设置 Dialog 的布局资源 ID (必须)
         */
        fun setLayout(@LayoutRes layoutId: Int) = apply { this.layoutId = layoutId }

        /**
         * 添加一个需要监听点击事件的 View ID。
         * 点击后默认会关闭 Dialog。
         */
        fun setClickView(@IdRes viewId: Int) = apply { this.clickViewIds.add(viewId) }

        /**
         * 添加多个需要监听点击事件的 View ID。
         * 使用 vararg，可以传入任意数量的 ID。
         */
        fun setClickViews(@IdRes vararg viewIds: Int) = apply { this.clickViewIds.addAll(viewIds.toList()) }

        /**
         * 设置自定义的视图初始化逻辑。
         * 你可以在这个 lambda 中 findViewById 并设置文本、图片等。
         * @param block 回调函数，参数为 Dialog 的根视图 (decorView)。
         */
        fun setInitView(block: (rootView: View) -> Unit) = apply { this.onViewInit = block }

        /**
         * 设置统一的点击事件处理逻辑。
         * 所有通过 setClickView/setClickViews 添加的 View 都会触发这个回调。
         * @param block 回调函数，参数为被点击的 View。
         */
        fun setInitClick(block: (clickedView: View) -> Unit) = apply { this.onViewClick = block }

        /**
         * 设置点击 View 后是否自动关闭 Dialog。默认为 true。
         */
        fun setDismissOnClick(dismiss: Boolean) = apply { this.dismissOnClick = dismiss }

        /**
         * 设置 Dialog 的宽度和高度。
         * 例如: setSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
         */
        fun setSize(width: Int, height: Int) = apply {
            this.width = width
            this.height = height
        }

        /**
         * 设置 Dialog 的位置 (例如 Gravity.BOTTOM)。
         */
        fun setGravity(gravity: Int) = apply { this.gravity = gravity }

        /**
         * 设置 Dialog 的主题样式。
         */
        fun setTheme(@StyleRes themeResId: Int) = apply { this.themeResId = themeResId }

        /**
         * 设置是否可以通过返回键关闭 Dialog。默认为 true。
         */
        fun setCancelable(cancelable: Boolean) = apply { this.isCancelable = cancelable }

        /**
         * 设置是否可以点击外部区域关闭 Dialog。默认为 true。
         */
        fun setCanceledOnTouchOutside(cancelable: Boolean) = apply { this.isCancelableOnTouchOutside = cancelable }


        /**
         * 创建并显示 Dialog。
         * @return 返回创建的 SimpleDialog 实例。
         */
        fun show(): SimpleDialog {
            val dialog = SimpleDialog(this)
            dialog.show()
            return dialog
        }
    }

    companion object {
        /**
         * 静态工厂方法，作为 Builder 的入口。
         * @param lifecycleOwner 传入 `this` (通常是 Fragment 或 Activity)。
         */
        fun with(lifecycleOwner: LifecycleOwner): Builder {
            return Builder(lifecycleOwner)
        }
    }
}