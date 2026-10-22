package com.rhys.main

import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.android.common.base.BaseBindActivity
import com.example.network.session.SessionManager
import com.marky.route.api.NRoute
import com.rhys.main.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : BaseBindActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    override fun initListener(binding: ActivityMainBinding) {

    }

    override fun initData(binding: ActivityMainBinding) {
        // 启动时异步恢复登录态；SessionManager 内部已捕获网络异常，不阻塞 UI。
        lifecycleScope.launch {
            SessionManager.restore()
        }
    }

    override fun initView(binding: ActivityMainBinding) {
        val navController = findNavController(R.id.nav_host_fragment)
        NRoute.init(this, navController)

    }
}
