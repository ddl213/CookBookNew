package com.rhys.main

import android.app.Application
import com.android.common.helper.AppHelper

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppHelper.init(this)
    }

}