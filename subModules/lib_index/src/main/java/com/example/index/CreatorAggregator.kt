package com.example.index

import com.example.network.bean.RecipeDto
import com.example.network.bean.UserSummaryDto

/**
 * 把公开菜谱列表聚合为菜谱作者，按首次出现顺序去重。
 *
 * 只做客户端展示聚合，不代表评分、认证或个性化推荐；作者合法性以后端为准，
 * 这里仅跳过缺失或 id 非法的页面数据，避免生成不可点击的创作者条目。
 */
object CreatorAggregator {

    fun collect(recipes: List<RecipeDto>): List<UserSummaryDto> {
        val seenIds = HashSet<Long>()
        val creators = ArrayList<UserSummaryDto>()
        recipes.forEach { recipe ->
            val author = recipe.author ?: return@forEach
            if (author.id <= 0L) return@forEach
            if (seenIds.add(author.id)) creators.add(author)
        }
        return creators
    }
}
