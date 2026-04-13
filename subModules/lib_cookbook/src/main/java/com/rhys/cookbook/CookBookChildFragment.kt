package com.rhys.cookbook

import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildRefreshAdapter
import com.android.common.ext.dp
import com.android.common.ext.liner
import com.android.common.ext.load
import com.android.common.utils.LogUtils
import com.android.common.utils.RvDecoration
import com.campaign.common.constants.RoutePath
import com.example.network.bean.Recipe
import com.marky.route.annotation.Route
import com.photo.view.dialog.BasePhotoDialog
import com.rhys.cookbook.databinding.CookbookFragmentCookBookChildBinding
import com.rhys.cookbook.databinding.CookbookLayoutChildBinding

/**
 * 展示我的菜谱数据
 * 已发布的菜谱：点击会跳转到菜谱详情页面
 * 草稿状态：点击会携带当前信息，跳转到编辑页面
 *
 */
@Route(RoutePath.PAGE_COOK_BOOK_CHILD)
class CookBookChildFragment :
    BaseBindFragment<CookbookFragmentCookBookChildBinding>(CookbookFragmentCookBookChildBinding::inflate) {
    private val windowInsetsController by lazy { WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView) }

    private val list = mutableListOf(
        Recipe(
            "1",
            "Test",
            "菜谱1",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱1",
            "2022-01-01",
            1,
            3,
            4
        ),
        Recipe(
            "1",
            "Test",
            "菜谱2",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱2",
            "2022-01-01",
            1,
            3,
            4
        ),
        Recipe(
            "1",
            "Test",
            "菜谱3",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱3",
            "2022-01-01",
            1,
            3,
            4
        ),
        Recipe(
            "1",
            "Test",
            "菜谱4",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱4",
            "2022-01-01",
            1,
            3,
            4
        ),
        Recipe(
            "1",
            "Test",
            "菜谱5",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱5",
            "2022-01-01",
            1,
            3,
            4
        ),
        Recipe(
            "1",
            "Test",
            "菜谱6",
            "https://avatars.githubusercontent.com/u/26372905?v=4",
            "菜谱6",
            "2022-01-01",
            1,
            3,
            4
        )
    )

    override fun initTitleBar(): View? = null
    override fun initView(binding: CookbookFragmentCookBookChildBinding) {
        initRecyclerView()
    }

    override fun initData(binding: CookbookFragmentCookBookChildBinding) {

    }

    override fun onResume() {
        super.onResume()
        //showStatusBar()
    }

    private fun initRecyclerView() {
        binding.apply {
            rv.addItemDecoration(RvDecoration.Builder().setHorizontal(10).setVertical(30)
                .setDivider(Color.RED,0.33f.dp())
                .setShowLastDivider(false)
                .setDividerSpacing(15)
                .setDividerHorizontalSpacing(50).build())
            rv.liner().buildRefreshAdapter<Recipe>(true) {
                setDiff(areItemsSame = { old, new -> old.id == new.id}, areContentsSame = { old, new -> old == new})
                setLayout(CookbookLayoutChildBinding::class.java)
                setList(list)
                bind<CookbookLayoutChildBinding> { holder, position, item,_ ->
                    holder.binding.apply {
                        ivRecipePic.load(item.imageUrl)
                        tvRecipeName.text = item.name
                        tvRecipeDesc.text = item.desc
                    }
                }
                addChildClickViewIds(R.id.iv_recipe_pic)
                setOnItemChildClickListener { adapter, view, _ ->
                    //hideStatusBar()

                    LogUtils.addCurrentTagInFilter()
                    BasePhotoDialog.show(requireActivity(),view)
                }
            }

            // 添加刷新监听
            rv.setOnRefreshListener {
                // 模拟刷新操作
                rv.postDelayed({
                    rv.refreshComplete()
                }, 2000)
            }
        }
    }

}