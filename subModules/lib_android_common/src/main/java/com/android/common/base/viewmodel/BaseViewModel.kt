package com.android.common.base.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.common.utils.LogUtils
import kotlinx.coroutines.launch

open class BaseViewModel() : ViewModel() {


    inline fun launch(
        callerClass: String = getCallerClassName(),
        callerMethod: String = getCallerMethodName(),
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                LogUtils.d("当前执行的方法是：$callerClass.$callerMethod")
                block()
            } catch (e: Exception) {
                LogUtils.e(e,"崩溃位置在： $callerClass.$callerMethod")
            }
        }
    }

    @PublishedApi
    internal fun getCallerClassName(): String {
        return Throwable().stackTrace[2].className.substringAfterLast('.')
    }

    @PublishedApi
    internal fun getCallerMethodName(): String {
        return Throwable().stackTrace[2].methodName
    }
}