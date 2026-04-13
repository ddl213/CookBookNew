package com.android.common.base.adapter.refresh.header

enum class RefreshHeaderEnum {
    /**
     * 正常状态
     */
    NORMAL,
    /**
     * 下拉的状态
     */
    RELEASE_TO_REFRESH,
    /**
     * 正在刷新的状态
     */
    REFRESHING,
    /**
     * 刷新完成的状态
     */
    DONE
}