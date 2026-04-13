package com.rhys.cookbook.add

import com.android.common.base.BaseBindFragment
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_ADD
import com.marky.route.annotation.Route
import com.rhys.cookbook.databinding.CookbookFragmentAddRecipesBinding

/**
 * 添加菜谱的页面
 * 需要在生命周期走到stop时主动保存。
 * 这样记录菜谱的草稿数据，以便下次可以继续编写
 */
@Route(PAGE_COOK_BOOK_ADD)
class AddRecipesFragment : BaseBindFragment<CookbookFragmentAddRecipesBinding>(CookbookFragmentAddRecipesBinding::inflate) {

    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }
    override fun initView(binding: CookbookFragmentAddRecipesBinding) {

    }

    override fun initData(binding: CookbookFragmentAddRecipesBinding) {

    }

}