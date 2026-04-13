package com.android.common.ext

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.common.base.adapter.AdapterBuilder
import com.android.common.base.adapter.refresh.RefreshRecyclerView
import com.android.common.bean.TabInfo
import net.lucode.hackware.magicindicator.MagicIndicator
import net.lucode.hackware.magicindicator.buildins.commonnavigator.CommonNavigator
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.CommonNavigatorAdapter
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerIndicator
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerTitleView



/**
 * --------------------------------- View ---------------------------------
 */
fun View.visible(){
    if (visibility == View.VISIBLE) return
    visibility = View.VISIBLE
}

fun View.gone(){
    if (visibility == View.GONE) return
    visibility = View.GONE
}


fun Fragment.finish() {
    findNavController().popBackStack()
}

/**
 * --------------------------------- MagicIndicator ---------------------------------
 */

fun MagicIndicator.setNavigator(context : Context, tabList: List<TabInfo>, isAdjustMode : Boolean = true, block : (com.android.common.bean.TabInfo, Int) -> IPagerTitleView){

    navigator = CommonNavigator(context).apply {
        adapter = object : CommonNavigatorAdapter() {
            override fun getCount(): Int {
                return tabList.size
            }

            override fun getTitleView(context: Context?, index: Int): IPagerTitleView {
                val item = tabList[ index]
                return block.invoke( item,index)
            }

            override fun getIndicator(context: Context?): IPagerIndicator? {
                return null
            }
        }
        this.isAdjustMode = isAdjustMode
    }
}



/**
 * --------------------------------- ViewPager2 ---------------------------------
 */
fun ViewPager2.attach(indicator: MagicIndicator) {
    this.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int, positionOffset: Float, positionOffsetPixels: Int
        ) {
            super.onPageScrolled(position, positionOffset, positionOffsetPixels)
            indicator.onPageScrolled(position, positionOffset, positionOffsetPixels)
        }

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            indicator.onPageSelected(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            indicator.onPageScrollStateChanged(state)
        }
    })
}


/**
 * --------------------------------- RecyclerView ---------------------------------
 */
//构建适配器，必需显示设置数据类型，不然设置差异回调时类型一直是any
inline fun <reified T : Any> RecyclerView.buildAdapter(block: AdapterBuilder<T>.() -> Unit): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
    val builder = AdapterBuilder<T>()
    builder.block()
    val adapter = builder.build()

    this.adapter = adapter
    return adapter
}
//构建可上拉刷新，下拉加载的适配器
inline fun <reified T : Any> RefreshRecyclerView.buildRefreshAdapter(header:Boolean = false, footer:Boolean = false, block: AdapterBuilder<T>.() -> Unit): ConcatAdapter {
    val builder = AdapterBuilder<T>()
    builder.block()
    val adapter = builder.buildRefresh(this,header,footer)

    this.adapter = adapter
    return adapter
}