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
        initData(binding)
        initListener(binding)
        initObserver(binding)

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
     * 初始化监听器
     */
    open fun initListener(binding: V)  {}
    /**
     * 初始化监听器
     */
    open fun initObserver(binding: V)  {}

    open fun onViewDestroy() {}


    /**
     * 初始化状态栏
     */
    protected open fun initImmersionBar(){
        ImmersionBar.with(requireActivity())
            .statusBarDarkFont(getStartBarDarkFont())
            .statusBarColor(getStatusBarColor())
            .titleBar(initTitleBar())
            .init()

    }


    //将binding置为空,防止内存消耗
    override fun onDestroyView() {
        onViewDestroy()
        //置空
        _binding = null
        super.onDestroyView()
    }

    /**
     * 设置状态栏顶部View
     */
    protected open fun initTitleBar() : View? = null
    /**
     * 设置状态栏颜色
     */
    protected open fun getStatusBarColor() : Int = R.color.transparent

    /**
     * 状态栏字体颜色
     */
    protected open fun getStartBarDarkFont() : Boolean = true
}