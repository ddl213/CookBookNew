package com.android.common.base.adapter.refresh.header.listener

import android.view.View
import com.android.common.base.adapter.refresh.header.RefreshHeaderEnum

interface IRefreshHeader {
    /** 刷新头是刷新状态的唯一来源；DONE 期间不接受下一次刷新。 */
    val state: RefreshHeaderEnum

    /**
     * 重置状态
     */
    fun onReset()

    /**
     * 处于可以刷新的状态，已经过了指定距离
     */
    fun onPrepare()

    /**
     * 正在刷新
     */
    fun onRefreshing()

    /**
     * 下拉移动
     */
    fun onMove(offSet : Float, sumOffSet : Float)

    /**
     * 下拉松开，仅当本次释放新开始刷新时返回 true。
     */
    fun onRelease() : Boolean

    /**
     * 下拉刷新完成
     */
    fun refreshComplete()

    /**
     * 获取HeaderView
     */
    fun getHeaderView() : View

    /**
     * 获取Header的显示高度
     */
    fun getVisibleHeight() : Int
}
