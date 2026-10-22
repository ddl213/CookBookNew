package com.rhys.auth

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.ext.gone
import com.android.common.ext.visible
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_REGISTER
import com.example.network.AuthException
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.auth.databinding.AuthFragmentLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 登录页。
 * 登录成功后返回进入登录前的页面；受限操作由调用方在返回后继续。
 */
@Route(PAGE_LOGIN)
class LoginFragment : BaseBindFragment<AuthFragmentLoginBinding>(AuthFragmentLoginBinding::inflate) {

    private var submitting = false

    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: AuthFragmentLoginBinding) {
        binding.titleBar.setLeftClick {
            findNavController().popBackStack()
        }
    }

    override fun initData(binding: AuthFragmentLoginBinding) {
    }

    override fun initListener(binding: AuthFragmentLoginBinding) {
        binding.btnLogin.setOnClickListener {
            submitLogin()
        }
        binding.tvRegister.setOnClickListener {
            val controller = findNavController()
            // 避免注册 -> 登录 -> 注册时重复入栈。
            if (!controller.popBackStack(PAGE_REGISTER, false)) {
                NRoute.withNavController(controller)
                    .deepLink(PAGE_REGISTER)
                    .go()
            }
        }
    }

    private fun submitLogin() {
        if (submitting) return
        val binding = binding
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        val validation = AuthValidator.validateLogin(username, password)
        if (!validation.isValid) {
            showError(validation.errorMessage)
            return
        }

        setLoading(true)
        hideError()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SessionManager.login(username, password)
                if (view != null) {
                    findNavController().popBackStack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                showError(e.message)
            } catch (e: Exception) {
                showError(getString(R.string.auth_error_default))
            } finally {
                if (view != null) {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (view == null) return
        submitting = loading
        binding.btnLogin.isEnabled = !loading
        binding.etUsername.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.btnLogin.text = if (loading) {
            getString(R.string.auth_logging_in)
        } else {
            getString(R.string.auth_login_button)
        }
        if (loading) binding.pbLoading.visible() else binding.pbLoading.gone()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.auth_error_default)
        binding.tvError.visible()
    }

    private fun hideError() {
        if (view == null) return
        binding.tvError.gone()
    }
}
