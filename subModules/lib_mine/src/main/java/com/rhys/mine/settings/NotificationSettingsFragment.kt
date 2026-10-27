package com.rhys.mine.settings

import com.campaign.common.constants.RoutePath.PAGE_SETTING_NOTIFICATION
import com.marky.route.annotation.Route
import com.rhys.mine.R

/**
 * 通知设置说明页。
 *
 * 只说明预订通知的展示位置与当前无通知开关的事实，不新增推送或开关能力。
 */
@Route(PAGE_SETTING_NOTIFICATION)
class NotificationSettingsFragment : SettingInfoFragment() {

    override val pageTitleRes: Int = R.string.mine_notification_title
    override val pageBodyRes: Int = R.string.mine_notification_body
}
