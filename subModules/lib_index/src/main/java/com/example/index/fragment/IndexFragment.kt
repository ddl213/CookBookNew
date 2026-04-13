package com.example.index.fragment

import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildAdapter
import com.android.common.ext.gone
import com.android.common.ext.visible
import com.campaign.common.constants.RoutePath.PAGE_INDEX
import com.android.common.ext.liner
import com.android.common.utils.LogUtils
import com.example.index.databinding.IndexFragmentIndexBinding
import com.example.index.databinding.IndexLayoutRvIndexDiscoverBinding
import com.example.index.databinding.IndexLayoutRvIndexFeatureCategoriesBinding
import com.example.index.databinding.IndexLayoutRvIndexPopularBinding
import com.example.index.databinding.IndexLayoutRvIndexTagBinding
import com.marky.route.annotation.Route
import kotlin.math.abs

@Route(PAGE_INDEX)
class IndexFragment : BaseBindFragment<IndexFragmentIndexBinding>(IndexFragmentIndexBinding::inflate) {

    private val tagList = listOf(
        "推荐",
        "关注",
    )

    override fun initView(binding: IndexFragmentIndexBinding) {
        LogUtils.d("IndexFragment 初始化成功")
        binding.apply {
            initRecyclerView()


        }

    }

    private fun initRecyclerView(){
        binding.apply {
            //顶部分类标签
            rvTags.liner(RecyclerView.HORIZONTAL).buildAdapter {
                setDiff(
                    areItemsSame = { oldItem, newItem ->
                        oldItem == newItem
                    },
                    areContentsSame = { oldItem, newItem ->
                        oldItem == newItem
                    }
                )
                setLayout(IndexLayoutRvIndexTagBinding::class.java)
                bind <IndexLayoutRvIndexTagBinding>{ holder, position, item,_ ->

                }
                setList(tagList.toMutableList())

            }
            //分类
            rvFeaturedCategories.liner(RecyclerView.HORIZONTAL).buildAdapter {
                setDiff(
                    areItemsSame = { oldItem, newItem ->
                        oldItem == newItem
                    },
                    areContentsSame = { oldItem, newItem ->
                        oldItem == newItem
                    }
                )
                setLayout(IndexLayoutRvIndexFeatureCategoriesBinding::class.java)
                bind <IndexLayoutRvIndexFeatureCategoriesBinding>{ holder, position, item,_ ->

                }
                setList(tagList.toMutableList())

            }

            //好友动态
            rvDiscover.liner(RecyclerView.HORIZONTAL).buildAdapter {
                setDiff(
                    areItemsSame = { oldItem, newItem ->
                        oldItem == newItem
                    },
                    areContentsSame = { oldItem, newItem ->
                        oldItem == newItem
                    }
                )
                setLayout(IndexLayoutRvIndexDiscoverBinding::class.java)
                bind <IndexLayoutRvIndexDiscoverBinding>{ holder, position, item,_ ->

                }
                setList(tagList.toMutableList())
            }

            //热门推荐
            rvPopular.liner(RecyclerView.VERTICAL).buildAdapter {
                setDiff(
                    areItemsSame = { oldItem, newItem ->
                        oldItem == newItem
                    },
                    areContentsSame = { oldItem, newItem ->
                        oldItem == newItem
                    }
                )
                setLayout(IndexLayoutRvIndexPopularBinding::class.java)
                bind <IndexLayoutRvIndexPopularBinding>{ holder, position, item,_ ->

                }
                setList(tagList.toMutableList())
            }
        }
    }

    override fun initData(binding: IndexFragmentIndexBinding) {

    }

    override fun initListener(binding: IndexFragmentIndexBinding) {
        super.initListener(binding)
        binding.apply {
            //设置折叠监听
            appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                val totalScrollRange = appBarLayout.totalScrollRange
                val alpha = abs(verticalOffset / totalScrollRange.toFloat())

                ivSearch.alpha = alpha
                if (alpha > 0.5){
                    ivSearch.visible()
                }else if (alpha < 0.2){
                    ivSearch.gone()
                }
            }
        }
    }
}