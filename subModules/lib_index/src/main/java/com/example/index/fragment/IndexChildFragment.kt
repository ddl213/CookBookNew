package com.example.index.fragment

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBindFragment
import com.android.common.base.adapter.BaseAdapter
import com.android.common.ext.buildAdapter
import com.android.common.utils.RvDecoration
import com.example.network.bean.HomeFeedItem
import com.example.network.bean.Recipe
import com.example.network.bean.User
import com.campaign.common.constants.RoutePath.PAGE_INDEX_CHILD
import com.android.common.ext.liner
import com.android.common.ext.load
import com.example.index.R
import com.example.index.databinding.IndexFragmentIndexChildBinding
import com.example.index.databinding.IndexLayoutIndexChildHorizontalItemBinding
import com.example.index.databinding.IndexLayoutIndexChildVerticalItemBinding
import com.marky.route.annotation.Route

@Route(PAGE_INDEX_CHILD)
class IndexChildFragment : BaseBindFragment<IndexFragmentIndexChildBinding>(IndexFragmentIndexChildBinding::inflate) {

    private val list = mutableListOf(
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
            ),
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
            )
        ),
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
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
            )
        ),
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
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
            )
        ),
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
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
            )
        ),
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
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
            )
        ),
        HomeFeedItem(
            User(
                "1",
                "Rhys",
                "https://avatars.githubusercontent.com/u/26372905?v=4",
                "",
                0,
                0,
                0
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
    )

    override fun initView(binding: IndexFragmentIndexChildBinding) {
        //初始化rvVertical
        val adapter = binding.rvVertical.liner().buildAdapter <HomeFeedItem>{
            setDiff(areItemsSame = { old, new -> old.user.id == new.user.id} ,
                areContentsSame = { old, new -> old == new})
            setLayout(0,IndexLayoutIndexChildHorizontalItemBinding::class.java)
            setLayout(1,IndexLayoutIndexChildVerticalItemBinding::class.java)
            setViewTypeDelegate { position, _ ->
                if (position % 2 == 0) {
                    0
                } else {
                    1
                }
            }
            bindMulti { holder, _, item,_ ->
                when(holder.itemViewType){
                    0 ->{
                        holder.withBinding<IndexLayoutIndexChildHorizontalItemBinding>{
                            ivPicture.load(item.recipe.imageUrl)
                        }
                    }
                    1 ->{
                        holder.withBinding<IndexLayoutIndexChildVerticalItemBinding>{
                            tvTitle.text = item.recipe.name
                            tvDesc.text = item.recipe.desc
                            tvLikes.text = item.recipe.likes.toString()
                            tvComments.text = item.recipe.commentsCount.toString()
                            tvName.text = item.user.name
                        }
                    }
                }
            }

            setList(list)
        }
        binding.rvVertical.addItemDecoration(RvDecoration.Builder().setVertical(10).build())


        //初始化rvHorizontal

        binding.rvHorizontal.addItemDecoration(RvDecoration.Builder().setHorizontal(10).setTop(14).build())
        binding.rvHorizontal.liner(RecyclerView.HORIZONTAL).buildAdapter <HomeFeedItem>{
            setDiff(areItemsSame = { old, new -> old.user.id == new.user.id },
                areContentsSame = { old, new -> old == new})
            setLayout(IndexLayoutIndexChildHorizontalItemBinding::class.java)
            bind<IndexLayoutIndexChildHorizontalItemBinding>{ holder, _, _,_ ->
                holder.binding.ivPicture.load(R.drawable.img_home_top)
            }
            setOnItemClickListener { adapter, view, position ->}
            setList(list)
        }


    }


    override fun initData(binding: IndexFragmentIndexChildBinding) {

    }

    override fun initTitleBar(): View? = null
}