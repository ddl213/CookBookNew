package com.android.common.helper

import android.app.Application

object AppHelper {
    lateinit var application : Application

    fun init(application: Application) {
        AppHelper.application = application
    }

}