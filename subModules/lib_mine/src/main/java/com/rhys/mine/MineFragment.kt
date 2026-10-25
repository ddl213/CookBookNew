package com.rhys.mine

import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.android.common.utils.LogUtils
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_FRIENDS
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_MINE
import com.campaign.common.listener.MainTabController
import com.example.network.NetworkConfig
import com.example.network.bean.UserDto
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.mine.databinding.MineFragmentMineBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * “我的”页。
 *
 * 只负责根据登录态展示入口，权限最终仍由后端校验；
 * 页面不伪造任何未实现功能，占位入口统一提示后续阶段开放。
 */
@Route(PAGE_MINE)
class MineFragment : BaseBindFragment<MineFragmentMineBinding>(MineFragmentMineBinding::inflate) {

    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: MineFragmentMineBinding) {
        LogUtils.d("MineFragment 初始化成功")
    }

    override fun initData(binding: MineFragmentMineBinding) {
    }

    override fun initListener(binding: MineFragmentMineBinding) {
        binding.btnLoginRegister.setOnClickListener {
            openLogin()
        }
        binding.btnBrowse.setOnClickListener {
            browseApp()
        }
        binding.btnLogout.setOnClickListener {
            SessionManager.logout()
        }
        binding.rowCollections.setOnClickListener { showPlaceholder() }
        binding.rowFriends.setOnClickListener { openFriends() }
        binding.rowOrders.setOnClickListener { openOrders() }
        binding.rowSettings.setOnClickListener { showPlaceholder() }
    }

    override fun initObserver(binding: MineFragmentMineBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> {
                binding.pbSession.visible()
                binding.llLoggedOut.gone()
                binding.llLoggedIn.gone()
            }

            AuthState.LoggedOut -> {
                binding.pbSession.gone()
                binding.llLoggedOut.visible()
                binding.llLoggedIn.gone()
            }

            is AuthState.LoggedIn -> {
                binding.pbSession.gone()
                binding.llLoggedOut.gone()
                binding.llLoggedIn.visible()
                bindUser(state.user)
            }
        }
    }

    private fun bindUser(user: UserDto) {
        if (view == null) return
        val displayName = user.displayName
        binding.tvNickname.text = displayName
        binding.tvUsername.text = "@${user.username}"

        val bio = user.bio.orEmpty().trim()
        binding.tvBio.text = bio
        if (bio.isEmpty()) binding.tvBio.gone() else binding.tvBio.visible()

        val avatar = NetworkConfig.resolveMediaUrl(user.avatar)
        if (avatar == null) {
            binding.ivAvatar.gone()
            binding.tvAvatarFallback.visible()
            binding.tvAvatarFallback.text = firstCharacter(displayName)
        } else {
            binding.tvAvatarFallback.gone()
            binding.ivAvatar.visible()
            binding.ivAvatar.load(avatar)
        }
    }

    /**
     * 取昵称首字作为头像占位，避免截断 emoji 等代理对字符。
     */
    private fun firstCharacter(text: String): String {
        if (text.isEmpty()) return ""
        val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
        return text.substring(0, end)
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.mine_login_failed)
        }
    }

    private fun openFriends() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_FRIENDS)
                .go()
        }.onFailure {
            toast(R.string.mine_friends_failed)
        }
    }

    /** 切到固定下标 3 的订单 Tab；宿主未实现 MainTabController 时提示，不新开重复页面。 */
    private fun openOrders() {
        val controller = parentFragment as? MainTabController
            ?: activity as? MainTabController
        if (controller == null) {
            toast(R.string.mine_orders_failed)
            return
        }
        controller.switchTab(3)
    }

    private fun browseApp() {
        val controller = parentFragment as? MainTabController
        if (controller == null) {
            toast(R.string.mine_browse_failed)
            return
        }
        controller.switchTab(0)
    }

    private fun showPlaceholder() {
        toast(R.string.mine_placeholder_toast)
    }

    private fun toast(messageRes: Int) {
        if (view == null) return
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }
}
