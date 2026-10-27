package com.rhys.mine.settings

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_SETTING
import com.campaign.common.constants.RoutePath.PAGE_SETTING_HELP
import com.campaign.common.constants.RoutePath.PAGE_SETTING_NOTIFICATION
import com.campaign.common.constants.RoutePath.PAGE_SETTING_PRIVACY
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.mine.R
import com.rhys.mine.databinding.MineFragmentSettingInfoBinding
import com.rhys.mine.databinding.MineFragmentSettingsBinding

/**
 * 设置页：仅提供隐私、通知、应用帮助三个说明入口。
 *
 * 当前版本不提供任何可配置开关，页面不读写设置项，也不调用后端接口。
 */
@Route(PAGE_SETTING)
class SettingsFragment :
    BaseBindFragment<MineFragmentSettingsBinding>(MineFragmentSettingsBinding::inflate) {

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: MineFragmentSettingsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
    }

    override fun initData(binding: MineFragmentSettingsBinding) {
    }

    override fun initListener(binding: MineFragmentSettingsBinding) {
        binding.rowPrivacy.setOnClickListener { openPage(PAGE_SETTING_PRIVACY) }
        binding.rowNotification.setOnClickListener { openPage(PAGE_SETTING_NOTIFICATION) }
        binding.rowHelp.setOnClickListener { openPage(PAGE_SETTING_HELP) }
    }

    private fun openPage(path: String) {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(path)
                .go()
        }.onFailure {
            if (view != null) {
                Toast.makeText(
                    requireContext(),
                    R.string.mine_settings_open_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

/**
 * 设置说明页共用基类：统一标题栏返回、标题与正文绑定。
 *
 * 说明页只展示已确认的产品事实，不承载开关、持久化或网络请求。
 */
abstract class SettingInfoFragment :
    BaseBindFragment<MineFragmentSettingInfoBinding>(MineFragmentSettingInfoBinding::inflate) {

    protected abstract val pageTitleRes: Int
    protected abstract val pageBodyRes: Int

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: MineFragmentSettingInfoBinding) {
        binding.titleBar.setTitle(getString(pageTitleRes))
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.tvSettingTitle.setText(pageTitleRes)
        binding.tvSettingBody.setText(pageBodyRes)
    }

    override fun initData(binding: MineFragmentSettingInfoBinding) {
    }
}
