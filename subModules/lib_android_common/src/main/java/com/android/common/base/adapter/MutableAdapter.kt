package com.android.common.base.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import java.lang.reflect.Method

abstract class MutableAdapter<T>(
    private val viewTypeDelegate: (position: Int, item: T) -> Int,
    diffCallback: DiffUtil.ItemCallback<T>
) : BaseAdapter<T>(diffCallback) {

    // 首次使用时保存快照，避免基类构造期间调用子类方法以及后续外部修改布局映射。
    private val viewBindingClasses by lazy { setLayouts().toMap() }

    // 缓存inflate方法以提高性能
    private val inflateMethods = mutableMapOf<Class<out ViewBinding>, Method>()

    //设置布局类型
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return viewTypeDelegate.invoke(position, item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<ViewBinding> {

        val viewBindingClass = viewBindingClasses[viewType]
            ?: throw IllegalArgumentException("No viewBindingClass found for viewType: $viewType")

        val inflateMethod = getInflateMethod(viewBindingClass)

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

    private fun getInflateMethod(viewBindingClass: Class<out ViewBinding>): Method {
        return inflateMethods.getOrPut(viewBindingClass) {
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
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position)
        setData(holder, position, item,payloads)
    }

    // null 表示全量绑定，非空 payloads 表示局部更新；离屏后仍可能需要全量绑定。
    abstract fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>? = null)

    /** 提供业务 viewType 到布局的映射，在首次创建 ViewHolder 时读取并保存快照。 */
    abstract fun setLayouts(): MutableMap<Int, Class<out ViewBinding>>
}
