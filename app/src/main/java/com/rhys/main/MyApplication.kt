package com.rhys.main

import android.app.Application
import com.android.common.helper.AppHelper
import com.rhys.chat.data.session.ChatSessionGuard
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
        // 按登录用户隔离聊天缓存：明确退出/切换账号时清空加密聊天数据。
        ChatSessionGuard.start(this)
    }

}
