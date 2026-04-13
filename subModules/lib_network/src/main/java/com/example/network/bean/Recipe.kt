package com.example.network.bean

data class Recipe(
    val id: String, // 菜谱的唯一ID
    val userId : String, // 用户ID
    val name: String, // 菜谱名称
    val imageUrl: String, // 菜谱图片URL
    val desc : String, // 菜谱描述
    val publishDate: String, // 发布时间
    val likes: Int, // 点赞数
    val collections: Int, // 收藏数
    val commentsCount: Int = 0, //菜谱的评论总数
)
