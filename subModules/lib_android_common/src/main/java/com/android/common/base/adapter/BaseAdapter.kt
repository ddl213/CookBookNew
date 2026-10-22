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
import java.util.WeakHashMap

abstract class BaseAdapter<T>(diffCallback: DiffUtil.ItemCallback<T>) :
    ListAdapter<T,BaseViewHolder<ViewBinding>>(diffCallback) {

    // item的点击事件
    private var mOnItemClickListener: OnItemClick<T>? = null
    private var mOnItemLongClickListener: OnItemLongClick<T>? = null
    private var mOnItemChildClickListener: OnItemChildClick<T>? = null

    private val createdHolders = WeakHashMap<BaseViewHolder<ViewBinding>, Unit>()
    private var submittedList: List<T> = emptyList()

    /**
     * 替换全部数据，并保存列表结构的快照；提交后修改原列表不会影响已提交的数据。
     * 元素本身不做深拷贝，参与 Diff 比较的元素应保持不可变，更新时创建新元素。
     */
    override fun submitList(list: List<T>?) {
        submitList(list, null)
    }

    /** 后续提交覆盖本次提交时，沿用 ListAdapter 规则，commitCallback 可能不执行。 */
    override fun submitList(list: List<T>?, commitCallback: Runnable?) {
        val snapshot = list?.toList()
        submittedList = snapshot.orEmpty()
        super.submitList(snapshot, commitCallback)
    }

    /**
     * 在最近一次提交的数据之后追加，包含尚未完成 Diff 的提交，避免连续追加丢失数据。
     * 与 submitList 一样在主线程调用，元素本身应保持不可变。
     */
    fun addItems(items: List<T>, commitCallback: Runnable? = null) {
        submitList(submittedList + items, commitCallback)
    }


    // 设置ViewHolder初始化的回调
    protected var initViewHolder: ((BaseViewHolder<ViewBinding>) -> Unit)? = null

    fun initViewHolder(block: (BaseViewHolder<ViewBinding>) -> Unit) {
        initViewHolder = block
    }


    /**
     * 绑定控件点击事件
     */
    protected fun bindViewClickListener(holder: BaseViewHolder<ViewBinding>) {
        createdHolders[holder] = Unit
        installViewClickListeners(holder)
    }

    private fun installViewClickListeners(holder: BaseViewHolder<ViewBinding>) {
        // item点击事件
        if (mOnItemClickListener != null) {
            holder.itemView.setOnClickListener { v ->
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    mOnItemClickListener?.invoke(this, v, position)
                }
            }
        }
        // item的长按事件
        if (mOnItemLongClickListener != null) {
            holder.itemView.setOnLongClickListener { v ->
                val position = holder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                mOnItemLongClickListener?.invoke(this, v, position) ?: false
            }
        }
        // item子控件的点击事件
        if (mOnItemChildClickListener != null) {
            for (id in childClickViewIds) {
                holder.itemView.findViewById<View>(id)?.setOnClickListener { v ->
                    val position = holder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        mOnItemChildClickListener?.invoke(this, v, position)
                    }
                }
            }
        }

    }

    fun setOnItemClickListener(listener: OnItemClick<T>) {
        mOnItemClickListener = listener
        updateCreatedHolders()
    }

    fun setOnItemLongClickListener(listener: OnItemLongClick<T>) {
        mOnItemLongClickListener = listener
        updateCreatedHolders()
    }

    fun setOnItemChildClickListener(listener: OnItemChildClick<T>) {
        mOnItemChildClickListener = listener
        updateCreatedHolders()
    }

    private fun updateCreatedHolders() {
        createdHolders.keys.toList().forEach(::installViewClickListeners)
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

    /** 添加需要点击的View */
    fun addChildClickViewIds(@IdRes vararg ids: Int) {
        if (childClickViewIds.addAll(ids.toList())) {
            updateCreatedHolders()
        }
    }
}

class BaseViewHolder<VB : ViewBinding>(
    val binding: VB,
    init: ((BaseViewHolder<VB>) -> Unit)? = null,
    /** 业务 Adapter 的原始类型，不受 ConcatAdapter 的类型隔离映射影响。 */
    val localViewType: Int = 0
) : RecyclerView.ViewHolder(binding.root) {
    // 保留 BaseViewHolder(binding) { ... } 的尾随初始化回调写法。
    constructor(binding: VB, init: (BaseViewHolder<VB>) -> Unit) : this(binding, init, 0)

    init {
        init?.invoke(this)
    }

    inline fun <reified Binding : ViewBinding> withBinding(block: Binding.() -> Unit) {
        val typedBinding = binding
        require(typedBinding is Binding) {
            "Expected ${Binding::class.java.name}, found ${typedBinding.javaClass.name}"
        }
        block(typedBinding)
    }
}

