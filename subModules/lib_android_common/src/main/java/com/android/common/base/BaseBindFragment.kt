package com.android.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.android.common.R
import com.gyf.immersionbar.ImmersionBar

abstract class BaseBindFragment<V : ViewBinding>(private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> V) :
    Fragment() {

    private var _binding: V? = null//私有的binding用于获取传进来的binding

    //只读的binding，用于暴露出去
    protected val binding
        get() = _binding
            ?: throw IllegalStateException("Binding is null. Is the dialog shown or already destroyed?")


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //接收传递的binding
        _binding = inflate(inflater, container, false)
        //由于_binding是可变的，所以使用binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initImmersionBar()
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
    abstract fun initData(binding: V)

    /**
     * 初始化状态栏
     */
    open fun initImmersionBar(){
        val titleView = initTitleBar()
        if (titleView != null) {
            ImmersionBar.with(requireActivity())
                .transparentStatusBar()
                .statusBarColor(statusBarColor())
                .titleBar(initTitleBar())
                .init()
        }else {
            ImmersionBar.with(requireActivity())
                .transparentStatusBar()
                .statusBarColor(statusBarColor())
                .init()
        }
    }
    open fun initTitleBar() : View? = null
    open fun statusBarColor() : Int = R.color.white
    open fun immersionBarDarkFont() : Boolean = true

    /**
     * 初始化监听器
     */
    open fun initListener(binding: V)  {}

    open fun onViewDestroy() {}


    //将binding置为空,防止内存消耗
    override fun onDestroyView() {
        onViewDestroy()
        //置空
        _binding = null
        super.onDestroyView()
    }
}