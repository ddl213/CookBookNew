package com.example.network.bean

data class User(
    val id: String, // 用户的唯一ID
    val name: String, // 用户名
    val avatar: String, // 头像图片URL
    val bio: String = "", // 个人简介（所有用户都可有，默认为空）
    val fans: Int = 0, // 粉丝数
    val followingCount: Int = 0, // 关注数
    val recipesCount: Int = 0, // 发布的菜谱数量
    val isFollowing: Boolean = false // 当前用户是否已关注，可用于UI状态
)

