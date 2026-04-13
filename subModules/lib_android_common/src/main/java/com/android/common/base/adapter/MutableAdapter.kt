package com.android.common.base.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.lang.reflect.Method

abstract class MutableAdapter<T>(
    private val viewTypeDelegate: (position: Int, item: T) -> Int,
    diffCallback: DiffUtil.ItemCallback<T>
) : BaseAdapter<T>(diffCallback) {

    // 存储布局类型到ViewBinding类的映射，只能通过setLayouts方法设置
    private lateinit var viewBindingClasses : MutableMap<Int, Class<out ViewBinding>>

    // 缓存inflate方法以提高性能
    private val inflateMethods = mutableMapOf<Int, Method>()

    //设置布局类型
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return viewTypeDelegate.invoke(position, item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewBinding> {

        val viewBindingClass = setLayouts()[viewType]
            ?: throw IllegalArgumentException("No viewBindingClass found for viewType: $viewType")

        val inflateMethod = getInflateMethod(viewBindingClass)

        val binding = inflateMethod.invoke(
            null,
            LayoutInflater.from(parent.context),
            parent,
            false
        ) as ViewBinding

        val holder = BaseViewHolder(binding, initViewHolder)
        bindViewClickListener(holder)
        return holder
    }

    private fun getInflateMethod(viewBindingClass: Class<out ViewBinding>): Method {
        return inflateMethods.getOrPut(viewBindingClass.hashCode()) {
            viewBindingClass.getMethod(
                "inflate",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Boolean::class.java
            )
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int) {
        val item = getItem(position)
        setData(holder, position, item)
    }
    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        @Suppress("UNCHECKED_CAST")
        setData(holder, position, item,payloads)
    }

    // 绑定数据的方法，由子类实现
    abstract fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>? = null)

    /**
     * 允许通过一个lambda表达式来设置多布局映射。
     *
     * @param block 一个 lambda，用于在 `MutableMap` 的上下文中构建布局映射。
     * 示例用法:
     * setLayouts {
     * 1 to MyFirstViewBinding::class.java
     * 2 to MySecondViewBinding::class.java
     * }
     */
    abstract fun setLayouts(): MutableMap<Int, Class<out ViewBinding>>
}
