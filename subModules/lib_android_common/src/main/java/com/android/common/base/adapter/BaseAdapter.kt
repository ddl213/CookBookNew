package com.android.common.base.adapter

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.android.common.base.adapter.listener.OnItemChildClick
import com.android.common.base.adapter.listener.OnItemClick
import com.android.common.base.adapter.listener.OnItemLongClick

abstract class BaseAdapter<T>(diffCallback: DiffUtil.ItemCallback<T>) :
    ListAdapter<T,BaseViewHolder<ViewBinding>>(diffCallback) {

    // item的点击事件
    private var mOnItemClickListener: OnItemClick<T>? = null
    private var mOnItemLongClickListener: OnItemLongClick<T>? = null
    private var mOnItemChildClickListener: OnItemChildClick<T>? = null


    // 设置ViewHolder初始化的回调
    protected var initViewHolder: ((BaseViewHolder<ViewBinding>) -> Unit)? = null

    fun initViewHolder(block: (BaseViewHolder<ViewBinding>) -> Unit) {
        initViewHolder = block
    }


    /**
     * 绑定控件点击事件
     */
    protected fun bindViewClickListener(holder: BaseViewHolder<ViewBinding>) {
        val positionProvider = {
            holder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
        }
        // item点击事件
        mOnItemClickListener?.let {
            holder.itemView.setOnClickListener { v ->
                positionProvider()?.let { pos ->
                    it.invoke(this, v, pos)
                }
            }
        }
        // item的长按事件
        mOnItemLongClickListener?.let {
            holder.itemView.setOnLongClickListener { v ->
                val pos = holder.absoluteAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                it.invoke(this, v, pos)
            }
        }
        // item子控件的点击事件
        mOnItemChildClickListener?.let {callback ->
            for (id in childClickViewIds) {
                holder.itemView.findViewById<View>(id)?.setOnClickListener { v ->
                    val pos = holder.absoluteAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        callback(this, v, pos)
                    }
                }
            }
        }

    }

    fun setOnItemClickListener(listener: OnItemClick<T>) {
        mOnItemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClick<T>) {
        mOnItemLongClickListener = listener
    }

    fun setOnItemChildClickListener(listener: OnItemChildClick<T>) {
        mOnItemChildClickListener = listener
    }


    // 获取点击监听器
    protected fun getOnItemClick(): OnItemClick<T>? {
        return mOnItemClickListener
    }

    // 获取长按监听器
    protected fun getOnItemLongClick(): OnItemLongClick<T>? {
        return mOnItemLongClickListener
    }

    // 获取item子控件点击监听器
    protected fun getOnItemChildClick(): OnItemChildClick<T>? {
        return mOnItemChildClickListener
    }

    // 需要点击事件的View
    private val childClickViewIds = LinkedHashSet<Int>()

    // 获取需要点击事件的View
    private fun getChildClickViewIds(): LinkedHashSet<Int> {
        return childClickViewIds
    }

    /** 添加需要点击的View */
    fun addChildClickViewIds(@IdRes vararg ids: Int) {
        childClickViewIds.addAll(ids.toList())
    }
}

class BaseViewHolder<VB : ViewBinding>(
    val binding: VB,
    init: ((BaseViewHolder<VB>) -> Unit)? = null
) : RecyclerView.ViewHolder(binding.root) {
    //为了多布局能够更方便的获取到binding
    //一个布局会生成一个holder，所以创建一个接口，将holder的binding进行强转
    private val handler : MultiLayoutHandler
    init {
        init?.invoke(this)
        handler = object : MultiLayoutHandler {
            override fun <VB : ViewBinding> withBinding(
                block: VB.() -> Unit
            ) {
                block(binding as VB)
            }
        }
    }

    fun <VB : ViewBinding> withBinding(
        block: VB.() -> Unit
    ){
        handler.withBinding(block)
    }

    // 多布局处理接口
    interface MultiLayoutHandler {
        fun <VB : ViewBinding> withBinding(
            block: VB.() -> Unit
        )
    }
}

