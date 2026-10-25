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

    /**
     * 聊天详情路由参数：会话 ID（Long）。
     * 缺失、非数字或 <= 0 表示参数非法，详情页不得按会话 0 打开。
     */
    const val KEY_BUNDLE_CONVERSATION_ID = "key_conversation_id"

    /**
     * 聊天详情路由参数：对方用户 ID（Long）。
     * 缺失、非数字或 <= 0 表示无法确定聊天对象，详情页不得据此请求他人数据。
     */
    const val KEY_BUNDLE_PEER_USER_ID = "key_peer_user_id"

    /**
     * 聊天详情路由参数：对方昵称（String?，可缺省）。
     * 缺省或加密不可用时应使用安全占位，不能当作真实昵称展示。
     */
    const val KEY_BUNDLE_PEER_NICKNAME = "key_peer_nickname"

    /**
     * 聊天详情路由参数：对方头像 URL（String?，可缺省）。
     * 缺省或加密不可用时应使用首字占位，不得把占位文案当作 URL 加载。
     */
    const val KEY_BUNDLE_PEER_AVATAR = "key_peer_avatar"
}