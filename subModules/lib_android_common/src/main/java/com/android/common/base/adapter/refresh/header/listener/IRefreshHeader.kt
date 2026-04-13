package com.android.common.base.adapter.refresh.header.listener

import android.view.View

interface IRefreshHeader {
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
     * 下拉松开
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