package com.android.common.base.adapter

import androidx.annotation.IdRes
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.android.common.base.adapter.listener.OnItemChildClick
import com.android.common.base.adapter.listener.OnItemClick
import com.android.common.base.adapter.listener.OnItemLongClick
import com.android.common.base.adapter.refresh.RefreshRecyclerView
import com.android.common.base.adapter.refresh.header.RefreshHeaderAdapter
import com.android.common.base.adapter.refresh.header.RefreshHeaderView
import com.android.common.utils.LogUtils

class AdapterBuilder<T : Any> {
    @PublishedApi
    internal var refreshWrapperAdapter : ConcatAdapter? = null
    @PublishedApi
    internal var adapter: BaseAdapter<T>? = null
    @PublishedApi
    internal var list: MutableList<T>? = null
    @PublishedApi
    internal var singleLayout: Class<out ViewBinding>? = null
    @PublishedApi
    internal val multiLayouts = mutableMapOf<Int, Class<out ViewBinding>>()
    @PublishedApi
    internal var viewTypeDelegate: ((position: Int, item: T) -> Int)? = null

    @PublishedApi
    internal var diffCallback: DiffUtil.ItemCallback<T>? = null

    //adapter单次初始化view
    private var initViewHolderBlock: ((BaseViewHolder<ViewBinding>) -> Unit)? = null

    // item的点击事件
    private var clickChildIds : List<Int>? = null
    private var mOnItemClickListener: OnItemClick<T>? = null
    private var mOnItemLongClickListener: OnItemLongClick<T>? = null
    private var mOnItemChildClickListener: OnItemChildClick<T>? = null

    fun setDiff(areItemsSame: (oldItem: T, newItem: T) -> Boolean,
        areContentsSame: (oldItem: T, newItem: T) -> Boolean){
        diffCallback = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = areItemsSame.invoke(oldItem, newItem)
            override fun areContentsTheSame(oldItem: T, newItem: T) = areContentsSame.invoke(oldItem, newItem)
        }
    }

    fun setDiff(areItemsSame: (oldItem: T, newItem: T) -> Boolean,
                areContentsSame: (oldItem: T, newItem: T) -> Boolean,
                getChangePayload : ((oldItem: T, newItem: T) -> Any)?){
        diffCallback = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = areItemsSame.invoke(oldItem, newItem)
            override fun areContentsTheSame(oldItem: T , newItem: T) = areContentsSame.invoke(oldItem, newItem)
            override fun getChangePayload(oldItem: T, newItem: T): Any? {
                return getChangePayload?.invoke(oldItem, newItem)
            }
        }
    }

    /** 设置单布局 */
    fun setLayout(layout: Class<out ViewBinding>) = apply {
        singleLayout = layout
        multiLayouts.clear()
    }

    /** 设置多布局 */
    fun setLayout(viewType: Int, layout: Class<out ViewBinding>) = apply {
        multiLayouts[viewType] = layout
        singleLayout = null
    }

    /** 设置获取布局类型的委托（多布局必需） */
    fun setViewTypeDelegate(delegate: (position: Int, item: T) -> Int) = apply {
        viewTypeDelegate = delegate
    }

    /** 设置数据源 */
    fun setList(list: MutableList<T>) = apply {
        this.list = list
    }

    /** 设置item子控件的点击事件 */
    fun addChildClickViewIds(@IdRes vararg childIds : Int) = also {
        clickChildIds = childIds.toList()
    }

    /** 设置item的点击事件 */
    fun setOnItemClickListener(listener: OnItemClick<T>) = apply {
        mOnItemClickListener = listener
    }

    /** 监听item长按事件 */
    fun setOnItemLongClickListener(listener: OnItemLongClick<T>) = apply {
        mOnItemLongClickListener = listener
    }

    /** 监听item子控件的点击事件 */
    fun setOnItemChildClickListener(listener: OnItemChildClick<T>) = apply {
        mOnItemChildClickListener = listener
    }

    /** 监听item子控件的点击事件 */
    fun initViewHolderBlock(viewHolder: (BaseViewHolder<ViewBinding>) -> Unit) = apply {
        initViewHolderBlock = viewHolder
    }


    /** 类型安全的单布局绑定 */
    inline fun <reified VB : ViewBinding> bind(
        crossinline setData: (holder: BaseViewHolder<VB>, position: Int, item: T, payloads: List<Any>?) -> Unit
    ) = also {
        if (singleLayout == null) {
            throw IllegalStateException("Single layout must be set using setLayout()")
        }
        if (diffCallback == null){
            throw IllegalStateException("diffCallback must be set using setDiff()")
        }


        adapter = object : SingleAdapter<T, ViewBinding>(singleLayout!!, diffCallback = diffCallback!!) {
            override fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>?) {
                val typedHolder = holder as BaseViewHolder<VB>
                setData(typedHolder, position, item,payloads)
                LogUtils.d("$position,$item")
            }
        }
    }

    /** 类型安全的多布局绑定 */
    inline fun bindMulti(
        crossinline setData : (holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>?) -> Unit
    ) = also {
        if (multiLayouts.isEmpty()) {
            throw IllegalStateException("Multi-layouts must be added using addLayout()")
        }
        if (viewTypeDelegate == null) {
            throw IllegalStateException("viewTypeDelegate must be set for multi-layout adapter.")
        }
        if (diffCallback == null){
            throw IllegalStateException("diffCallback must be set using setDiff()")
        }

        adapter = object : MutableAdapter<T>(viewTypeDelegate!!,diffCallback = diffCallback!!) {
            override fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>?) {
                LogUtils.d("setData: $position")
                setData(holder, position, item,payloads)
            }

            override fun setLayouts(): MutableMap<Int, Class<out ViewBinding>> = multiLayouts
        }
    }


    /** 构建adapter 仅同模块可见*/
    @PublishedApi
    internal fun build() : BaseAdapter<T>{
        if (adapter == null){
            throw NullPointerException("adapter is null:请使用bind或bindMulti方法绑定adapter")
        }
        initAdapter()
        return adapter!!
    }

    /** 构建附带刷新头/脚adapter 仅同模块可见*/
    @PublishedApi
    internal fun buildRefresh(rv: RefreshRecyclerView, header: Boolean, footer: Boolean) : ConcatAdapter{
        if (adapter == null){
            throw NullPointerException("adapter is null:请使用bind或bindMulti方法绑定adapter")
        }

        initAdapter()

        //创建一个适配器列表
        val adapters = mutableListOf<RecyclerView.Adapter<*>>()
        //如果设置了下拉刷新
        if (header) {
            //将刷新头添加到适配器列表中
            val headerView = RefreshHeaderView(rv.context)
            val headerAdapter = RefreshHeaderAdapter(headerView)
            adapters.add(headerAdapter)
            rv.setRefreshHeader(headerView)
        }

        adapters.add(adapter!!)
        refreshWrapperAdapter = ConcatAdapter(adapters)
        return refreshWrapperAdapter!!
    }

    /**
     * 初始化adapter
     * 确保所有配置项都已经设置
     */
    private fun initAdapter() {
        if (list != null) {
            adapter!!.submitList(list)
        }
        adapter!!.apply {
            clickChildIds?.let { addChildClickViewIds(*it.toIntArray()) }
            mOnItemClickListener?.let {setOnItemClickListener(it)}
            mOnItemLongClickListener?.let { setOnItemLongClickListener(it) }
            mOnItemChildClickListener?.let { setOnItemChildClickListener(it) }
            initViewHolderBlock?.let { initViewHolder(it) }
        }
    }
}