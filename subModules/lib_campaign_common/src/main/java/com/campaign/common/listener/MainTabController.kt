package com.campaign.common.listener

/**
 * 五个底部标签的切换能力。
 *
 * 由承载 ViewPager2 的 HomeFragment 实现；子页面可通过
 * `(parentFragment as? MainTabController)?.switchTab(index)` 切换标签。
 * 调用方必须处理宿主未实现该接口的情况，不能假设切换一定成功。
 */
interface MainTabController {

    /**
     * 切换到指定标签。
     * @param index 标签下标，顺序固定为：首页 0、菜谱 1、消息 2、订单 3、我的 4。
     */
    fun switchTab(index: Int)
}
