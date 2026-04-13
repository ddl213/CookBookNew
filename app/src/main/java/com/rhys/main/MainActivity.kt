package com.rhys.main

import androidx.navigation.findNavController
import com.android.common.base.BaseBindActivity
import com.marky.route.api.NRoute
import com.rhys.main.databinding.ActivityMainBinding

class MainActivity : BaseBindActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    override fun initListener(binding: ActivityMainBinding) {

    }

    override fun initData(binding: ActivityMainBinding) {

    }

    override fun initView(binding: ActivityMainBinding) {
        val navController = findNavController(R.id.nav_host_fragment)
        NRoute.init(this, navController)

    }
}