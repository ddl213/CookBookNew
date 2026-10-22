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
import com.rhys.auth.databinding.AuthFragmentRegisterBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 注册页。
 * 注册成功后自动登录，并优先返回到登录页之前的页面。
 */
@Route(PAGE_REGISTER)
class RegisterFragment :
    BaseBindFragment<AuthFragmentRegisterBinding>(AuthFragmentRegisterBinding::inflate) {

    private var submitting = false

    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: AuthFragmentRegisterBinding) {
        binding.titleBar.setLeftClick {
            findNavController().popBackStack()
        }
    }

    override fun initData(binding: AuthFragmentRegisterBinding) {
    }

    override fun initListener(binding: AuthFragmentRegisterBinding) {
        binding.btnRegister.setOnClickListener {
            submitRegister()
        }
        binding.tvLogin.setOnClickListener {
            val controller = findNavController()
            // 优先回到已有登录页，避免重复入栈导致登录成功后返回注册页。
            if (!controller.popBackStack(PAGE_LOGIN, false)) {
                NRoute.withNavController(controller)
                    .deepLink(PAGE_LOGIN)
                    .go()
            }
        }
    }

    private fun submitRegister() {
        if (submitting) return
        val binding = binding
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val nickname = binding.etNickname.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        val validation =
            AuthValidator.validateRegister(username, nickname, password, confirmPassword)
        if (!validation.isValid) {
            showError(validation.errorMessage)
            return
        }

        setLoading(true)
        hideError()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SessionManager.register(username, password, nickname.takeIf { it.isNotEmpty() })
                if (view != null) {
                    val controller = findNavController()
                    val returned = controller.popBackStack(PAGE_LOGIN, true)
                    if (!returned) {
                        controller.popBackStack()
                    }
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
        binding.btnRegister.isEnabled = !loading
        binding.etUsername.isEnabled = !loading
        binding.etNickname.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.etConfirmPassword.isEnabled = !loading
        binding.btnRegister.text = if (loading) {
            getString(R.string.auth_registering)
        } else {
            getString(R.string.auth_register_button)
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
