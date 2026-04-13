package com.android.common.bean

import android.os.Bundle
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
const val KEY_BUNDLE_PAGE_ID = "KEY_BUNDLE_PAGE_ID"

@Keep
data class TabInfo(
    val name: String = "",
    val id: String = "",
    val fragmentFullName: String = "",
    val fragmentClass: Class<out Fragment> = Class.forName(fragmentFullName) as Class<out Fragment>,
    val bundle: Bundle? = null
) {
}