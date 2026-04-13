package com.android.common.bean

import androidx.annotation.Keep

@Keep
data class HistorySearchInfo(
    val id : Int?,
    val queryStr : String,
    var time : Long
)

