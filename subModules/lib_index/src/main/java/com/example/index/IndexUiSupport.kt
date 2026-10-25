package com.example.index

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.Constants.KEY_USER_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_USER_PROFILE
import com.example.index.databinding.IndexItemRecipeBinding
import com.bumptech.glide.Glide
import com.example.network.NetworkConfig
import com.example.network.bean.RecipeDto
import com.marky.route.api.NRoute

/**
 * 首页与搜索页共用的卡片绑定、头像占位和路由入口。
 * 只做展示与导航，不替后端判断菜谱可见性或用户权限。
 */
internal fun bindIndexRecipeCard(
    binding: IndexItemRecipeBinding,
    item: RecipeDto,
    fragment: Fragment
) {
    val imageUrl = NetworkConfig.resolveMediaUrl(item.imageUrl)
    if (imageUrl == null) {
        // 取消可能仍在途的旧图片请求，避免复用时闪现上一道菜品。
        Glide.with(binding.ivRecipeImage).clear(binding.ivRecipeImage)
        binding.ivRecipeImage.setImageDrawable(null)
    } else {
        binding.ivRecipeImage.load(imageUrl)
    }

    binding.tvRecipeName.text = item.name

    val description = item.description.orEmpty().trim()
    binding.tvRecipeDesc.text = description
    if (description.isEmpty()) binding.tvRecipeDesc.gone() else binding.tvRecipeDesc.visible()

    val category = item.category.orEmpty().trim()
    binding.tvRecipeCategory.text = category
    if (category.isEmpty()) binding.tvRecipeCategory.gone() else binding.tvRecipeCategory.visible()

    val author = item.author
    val authorName = author?.displayName?.trim().orEmpty()
    binding.tvAuthorName.text = authorName
    bindIndexAvatar(
        binding.ivAuthorAvatar,
        binding.tvAuthorFallback,
        authorName,
        author?.avatar
    )
    binding.llAuthor.setOnClickListener {
        openIndexUserProfile(fragment, author?.id ?: 0L)
    }
}

/** 有头像时显示图片，否则显示昵称首字占位，避免页面因加载失败出现空白块。 */
internal fun bindIndexAvatar(
    imageView: ImageView,
    fallbackView: TextView,
    displayName: String,
    avatarUrl: String?
) {
    val url = NetworkConfig.resolveMediaUrl(avatarUrl)
    if (url == null) {
        Glide.with(imageView).clear(imageView)
        imageView.gone()
        fallbackView.visible()
        fallbackView.text = firstCharacter(displayName)
    } else {
        fallbackView.gone()
        imageView.visible()
        imageView.load(url)
    }
}

/** 取昵称首字作为头像占位，避免截断 emoji 等代理对字符。 */
internal fun firstCharacter(text: String): String {
    if (text.isEmpty()) return ""
    val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
    return text.substring(0, end)
}

internal fun openIndexRecipeDetail(fragment: Fragment, recipeId: Long) {
    if (recipeId <= 0L) return
    runCatching {
        NRoute.withNavController(fragment.findNavController())
            .deepLink(PAGE_COOK_BOOK_DETAIL)
            .args(bundleOf(KEY_BUNDLE_RECIPE_ID to recipeId))
            .go()
    }.onFailure {
        toastIndex(fragment, R.string.index_open_failed)
    }
}

internal fun openIndexUserProfile(fragment: Fragment, userId: Long) {
    if (userId <= 0L) return
    val args = Bundle().apply { putLong(KEY_USER_ID, userId) }
    runCatching {
        NRoute.withNavController(fragment.findNavController())
            .deepLink(PAGE_USER_PROFILE)
            .args(args)
            .go()
    }.onFailure {
        toastIndex(fragment, R.string.index_open_failed)
    }
}

internal fun openIndexLogin(fragment: Fragment): Boolean {
    return runCatching {
        NRoute.withNavController(fragment.findNavController())
            .deepLink(PAGE_LOGIN)
            .go()
    }.onFailure {
        toastIndex(fragment, R.string.index_login_failed)
    }.isSuccess
}

internal fun toastIndex(fragment: Fragment, @StringRes messageRes: Int) {
    if (!fragment.isAdded) return
    Toast.makeText(fragment.requireContext(), messageRes, Toast.LENGTH_SHORT).show()
}

internal fun toastIndex(fragment: Fragment, message: String) {
    if (!fragment.isAdded) return
    Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
}
