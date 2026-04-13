package com.android.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.gyf.immersionbar.ImmersionBar

abstract class BaseBindActivity<V : ViewBinding>(private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> V) :
    AppCompatActivity() {

    private var _binding: V? = null//私有的binding用于获取传进来的binding
    //只读的binding，用于暴露出去
    protected val binding get() = _binding ?: throw IllegalStateException("Binding is null. Is the dialog shown or already destroyed?")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflate.invoke(layoutInflater, null, false)

        val titleView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0
            )
        }

        val linearLayout = LinearLayout(this)
        linearLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.addView(titleView)
        linearLayout.addView(
            _binding?.root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        ImmersionBar.with(this).titleBar(titleView)
            .statusBarDarkFont(immersionBarDarkFont()).init()

        setContentView(linearLayout)
        initView(binding)
        initListener(binding)
        initData(binding)
    }


    /**
     * 初始化view
     */
    abstract fun initView(binding : V)

    /**
     * 初始化数据
     */
    abstract fun initData(binding : V)

    /**
     * 初始化监听器
     */
    abstract fun initListener(binding : V)


    open fun immersionBarDarkFont() = true

    //将binding置为空,防止内存消耗
    override fun onDestroy() {
        super.onDestroy()
        //置空
        _binding = null
    }
}