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
    internal var adapterFactory: ((DiffUtil.ItemCallback<T>) -> BaseAdapter<T>)? = null
    @PublishedApi
    internal var boundViewBindingClass: Class<out ViewBinding>? = null

    private var initialList: List<T>? = null
    private var singleLayout: Class<out ViewBinding>? = null
    @PublishedApi
    internal val multiLayouts = mutableMapOf<Int, Class<out ViewBinding>>()
    @PublishedApi
    internal var viewTypeDelegate: ((position: Int, item: T) -> Int)? = null

    private var diffCallback: DiffUtil.ItemCallback<T>? = null

    //adapter单次初始化view
    private var initViewHolderBlock: ((BaseViewHolder<ViewBinding>) -> Unit)? = null

    // item的点击事件
    private val clickChildIds = linkedSetOf<Int>()
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
                getChangePayload : ((oldItem: T, newItem: T) -> Any?)?){
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

    /**
     * 设置构建时的初始数据，并复制列表快照。
     * 构建后请通过控件或 Adapter 的 submitList 替换数据、addItems 追加数据。
     */
    fun setList(list: List<T>) = apply {
        initialList = list.toList()
    }

    /** 累加需要监听点击事件的子控件 ID，重复 ID 只保留一次。 */
    fun addChildClickViewIds(@IdRes vararg childIds : Int) = also {
        clickChildIds.addAll(childIds.toList())
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

    /** 设置每个 ViewHolder 创建时执行的初始化回调。 */
    fun initViewHolderBlock(viewHolder: (BaseViewHolder<ViewBinding>) -> Unit) = apply {
        initViewHolderBlock = viewHolder
    }


    /** 保存单布局绑定逻辑，统一在 build 时校验最终配置并创建 Adapter。 */
    inline fun <reified VB : ViewBinding> bind(
        crossinline setData: (holder: BaseViewHolder<VB>, position: Int, item: T, payloads: List<Any>?) -> Unit
    ) = also {
        boundViewBindingClass = VB::class.java
        adapterFactory = { callback ->
            object : SingleAdapter<T, VB>(VB::class.java, callback) {
                override fun setData(holder: BaseViewHolder<VB>, position: Int, item: T, payloads: List<Any>?) {
                    setData(holder, position, item, payloads)
                    LogUtils.d("$position,$item")
                }
            }
        }
    }

    /**
     * 保存多布局绑定逻辑，配置顺序不限。
     * ConcatAdapter 会重映射 itemViewType，绑定时请按 holder.localViewType 区分布局。
     */
    inline fun bindMulti(
        crossinline setData : (holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>?) -> Unit
    ) = also {
        boundViewBindingClass = null
        adapterFactory = { callback ->
            val layouts = multiLayouts.toMutableMap()
            val delegate = checkNotNull(viewTypeDelegate)
            object : MutableAdapter<T>(delegate, callback) {
                override fun setData(holder: BaseViewHolder<ViewBinding>, position: Int, item: T, payloads: List<Any>?) {
                    LogUtils.d("setData: $position")
                    setData(holder, position, item, payloads)
                }

                override fun setLayouts(): MutableMap<Int, Class<out ViewBinding>> = layouts
            }
        }
    }


    /** 校验最终配置并创建 Adapter；仅同模块可见。 */
    @PublishedApi
    internal fun build() : BaseAdapter<T>{
        val factory = checkNotNull(adapterFactory) {
            "请使用 bind 或 bindMulti 设置 Adapter 的绑定逻辑。"
        }
        val callback = checkNotNull(diffCallback) {
            "请使用 setDiff 设置差量比较规则。"
        }
        val bindingClass = boundViewBindingClass
        if (bindingClass != null) {
            val layout = checkNotNull(singleLayout) {
                "bind 需要通过 setLayout(layout) 设置单布局。"
            }
            check(layout == bindingClass) {
                "setLayout 的 ${layout.name} 与 bind 的 ${bindingClass.name} 不一致。"
            }
        } else {
            check(multiLayouts.isNotEmpty()) {
                "bindMulti 需要通过 setLayout(viewType, layout) 设置多布局。"
            }
            checkNotNull(viewTypeDelegate) {
                "bindMulti 需要通过 setViewTypeDelegate 设置布局类型委托。"
            }
        }

        return factory(callback).apply {
            addChildClickViewIds(*clickChildIds.toIntArray())
            mOnItemClickListener?.let { setOnItemClickListener(it) }
            mOnItemLongClickListener?.let { setOnItemLongClickListener(it) }
            mOnItemChildClickListener?.let { setOnItemChildClickListener(it) }
            initViewHolderBlock?.let { initViewHolder(it) }
            initialList?.let { submitList(it) }
        }
    }

    /** 构建可选下拉刷新头的 Adapter；footer 暂不支持。 */
    @PublishedApi
    internal fun buildRefresh(rv: RefreshRecyclerView, header: Boolean, footer: Boolean) : ConcatAdapter{
        require(!footer) { "footer 暂不支持，请使用 footer = false。" }
        val dataAdapter = build()
        val headerView = if (header) RefreshHeaderView(rv.context) else null
        val adapters = mutableListOf<RecyclerView.Adapter<*>>()
        if (headerView != null) {
            adapters.add(RefreshHeaderAdapter(headerView))
        }
        adapters.add(dataAdapter)
        rv.setRefreshHeader(headerView)
        return ConcatAdapter(adapters)
    }
}
