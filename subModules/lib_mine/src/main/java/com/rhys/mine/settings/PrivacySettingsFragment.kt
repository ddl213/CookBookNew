package com.rhys.mine.settings

import com.campaign.common.constants.RoutePath.PAGE_SETTING_PRIVACY
import com.marky.route.annotation.Route
import com.rhys.mine.R

/**
 * 隐私设置说明页。
 *
 * 只陈述菜谱可见性、好友可见范围与登录要求等已确认事实，不提供隐私开关。
 */
@Route(PAGE_SETTING_PRIVACY)
class PrivacySettingsFragment : SettingInfoFragment() {

    override val pageTitleRes: Int = R.string.mine_privacy_title
    override val pageBodyRes: Int = R.string.mine_privacy_body
}
