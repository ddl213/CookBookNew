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
        val holder = BaseViewHolder(binding, initViewHolder, localViewType = viewType)
        bindViewClickListener(holder)
        return holder
    }

    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int) {
        val item = getItem(position)
        @Suppress("UNCHECKED_CAST")
        setData(holder as BaseViewHolder<VB>, position, item)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<ViewBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position)
        @Suppress("UNCHECKED_CAST")
        setData(holder as BaseViewHolder<VB>, position, item,payloads)
    }

    // null 表示全量绑定，非空 payloads 表示局部更新；离屏后仍可能需要全量绑定。
    abstract fun setData(holder: BaseViewHolder<VB>, position: Int, item: T, payloads: List<Any>? = null)
}

