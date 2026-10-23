package com.campaign.common.constants

object Constants {
    const val KEY_LAST_EXIT_TIME = "last_exit_time"

    /**
     * 用户主页等页面路由参数：目标用户 ID（Long）。
     * 后续菜谱作者头像等入口复用同一 key。
     */
    const val KEY_USER_ID = "key_user_id"

    /**
     * 添加/编辑菜谱页路由参数：目标菜谱 ID（Long）。
     * 缺省或不合法时按新建处理。
     */
    const val KEY_BUNDLE_RECIPE_ID = "key_recipe_id"
}