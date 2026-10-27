package com.rhys.mine.settings

import android.content.pm.PackageManager
import android.os.Build
import com.android.common.ext.visible
import com.campaign.common.constants.RoutePath.PAGE_SETTING_HELP
import com.marky.route.annotation.Route
import com.rhys.mine.R
import com.rhys.mine.databinding.MineFragmentSettingInfoBinding

/**
 * 应用帮助页：展示使用说明与真实应用版本。
 *
 * 版本从 PackageManager 读取；读取失败时显示“未知”，不伪造版本信息。
 */
@Route(PAGE_SETTING_HELP)
class HelpFragment : SettingInfoFragment() {

    override val pageTitleRes: Int = R.string.mine_help_title
    override val pageBodyRes: Int = R.string.mine_help_body

    override fun initView(binding: MineFragmentSettingInfoBinding) {
        super.initView(binding)
        val version = readVersionName() ?: getString(R.string.mine_help_version_unknown)
        binding.tvSettingVersion.visible()
        binding.tvSettingVersion.text = getString(R.string.mine_help_version_format, version)
    }

    @Suppress("DEPRECATION")
    private fun readVersionName(): String? {
        return runCatching {
            val context = requireContext()
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
