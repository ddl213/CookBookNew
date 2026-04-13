package com.android.common.base.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding

abstract class SingleAdapter<T, VB : ViewBinding>(
    private val viewBindingClass: Class<out VB>,
    diffCallback: DiffUtil.ItemCallback<T>
) : BaseAdapter<T>(diffCallback) {
    // 通过反射获取inflate方法，并缓存以提高性能
    private val inflateMethod by lazy {
        viewBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewBinding> {
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

    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int) {
        val item = getItem(position)
        @Suppress("UNCHECKED_CAST")
        setData(holder as BaseViewHolder<VB>, position, item)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        @Suppress("UNCHECKED_CAST")
        setData(holder as BaseViewHolder<VB>, position, item,payloads)
    }

    // 绑定数据的方法，由子类实现
    abstract fun setData(holder: BaseViewHolder<VB>, position: Int, item: T, payloads: List<Any>? = null)

    // 这里的setData方法是用于子类实现的
//    abstract fun setData(holder: BaseViewHolder<VB>, position: Int, item: T?)
//
//    // 重写父类的setData方法，强制转换
//    override fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T?) {
//        @Suppress("UNCHECKED_CAST")
//        setData(holder as BaseViewHolder<VB>, position, item)
//    }
}

