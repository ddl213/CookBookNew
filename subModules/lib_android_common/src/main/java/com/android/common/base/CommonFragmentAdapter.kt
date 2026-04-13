package com.android.common.base

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.common.bean.KEY_BUNDLE_PAGE_ID
import com.android.common.bean.TabInfo

class CommonFragmentAdapter : FragmentStateAdapter {


    constructor (tableList: List<TabInfo>, fragment: Fragment) : super(fragment) {
        mTableList = tableList.toMutableList()
    }

    constructor (tableList: List<TabInfo>, activity: FragmentActivity) : super(activity) {
        mTableList = tableList.toMutableList()
    }

    constructor (
        tableList: List<TabInfo>,
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : super(fragmentManager, lifecycle) {
        mTableList = tableList.toMutableList()
    }

    private val mTableList: MutableList<TabInfo>

    override fun getItemCount(): Int {
        return mTableList.size
    }

    override fun createFragment(position: Int): Fragment {

        var fragment: Fragment? = null

        val tabInfo = mTableList[position]
        tabInfo.apply {
            fragment = fragmentClass.newInstance()
            fragment!!.arguments = (bundle ?: bundleOf(KEY_BUNDLE_PAGE_ID to name))
        }



        return fragment!!
    }

    fun setNewData(list: List<TabInfo>) {
        mTableList.clear()
        mTableList.addAll(list)
        notifyDataSetChanged()
    }

}