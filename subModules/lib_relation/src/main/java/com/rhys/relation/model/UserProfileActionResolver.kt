package com.rhys.relation.model

/**
 * 用户主页操作区应展示的动作。
 * 只决定 UI 展示，不替代后端权限校验。
 */
enum class UserProfileAction {
    /** 未登录：显示登录提示与登录按钮，不展示私有数据。 */
    LOGIN_REQUIRED,

    /** 自己：不显示加好友/解除好友入口。 */
    NONE,

    /** 好友：可发消息、解除好友。 */
    MESSAGE_AND_UNFRIEND,

    /** 非好友：可添加好友。 */
    ADD_FRIEND
}

object UserProfileActionResolver {

    fun resolve(isLoggedIn: Boolean, isSelf: Boolean, isFriend: Boolean): UserProfileAction {
        return when {
            !isLoggedIn -> UserProfileAction.LOGIN_REQUIRED
            isSelf -> UserProfileAction.NONE
            isFriend -> UserProfileAction.MESSAGE_AND_UNFRIEND
            else -> UserProfileAction.ADD_FRIEND
        }
    }
}
