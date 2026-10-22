package com.rhys.main

import android.app.Application
import com.android.common.helper.AppHelper
import com.tencent.mmkv.MMKV

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // MMKVUtils 依赖默认 MMKV 实例，必须先初始化。
        MMKV.initialize(this)
        AppHelper.init(this)
    }

}
