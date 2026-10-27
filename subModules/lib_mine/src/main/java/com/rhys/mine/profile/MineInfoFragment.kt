package com.rhys.mine.profile

import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_MINE_INFO
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.PickedImageReader
import com.example.network.RecipeMediaRules
import com.example.network.bean.UserDto
import com.example.network.repository.RecipeRepository
import com.example.network.repository.UserRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.rhys.mine.R
import com.rhys.mine.databinding.MineFragmentMineInfoBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 个人信息编辑页：修改昵称、简介与头像。
 *
 * 只在首次拿到当前登录用户时填充表单，之后的状态更新不会覆盖用户正在编辑的内容；
 * 编辑过程中账号变化、登录态失效或接口返回 401 时提示并返回上一页，不把输入保存到其他账号。
 * 头像经系统照片选择器选取，校验通过并上传成功后才随保存提交。
 */
@Route(PAGE_MINE_INFO)
class MineInfoFragment :
    BaseBindFragment<MineFragmentMineInfoBinding>(MineFragmentMineInfoBinding::inflate) {

    private val userRepository = UserRepository()
    private val mediaRepository = RecipeRepository()

    /** 已填充表单的用户 id；null 表示尚未加载。 */
    private var loadedUserId: Long? = null

    /** 后端已保存的头像路径。 */
    private var currentAvatarPath: String? = null

    /** 本次会话中上传成功的头像路径；只有它会被提交。 */
    private var pendingAvatarPath: String? = null

    /** 本地预览 URI；上传完成前允许展示，但禁止保存。 */
    private var previewUri: Uri? = null

    private var uploading = false
    private var saving = false

    /** 已触发返回，避免重复 pop 或重复提示。 */
    private var leaving = false

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadPickedImage(uri)
        }

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: MineFragmentMineInfoBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.ivAvatar.setOnClickListener { launchImagePicker() }
        binding.tvAvatarFallback.setOnClickListener { launchImagePicker() }
        binding.tvAvatarHint.setOnClickListener { launchImagePicker() }
        binding.etNickname.doAfterTextChanged {
            if (view == null) return@doAfterTextChanged
            if (binding.ivAvatar.visibility != View.VISIBLE) {
                binding.tvAvatarFallback.text = firstCharacter(nicknameText())
            }
        }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.pbPageLoading.visible()
        binding.scrollContent.gone()
        refreshSaveEnabled()
    }

    override fun initData(binding: MineFragmentMineInfoBinding) {
    }

    override fun initObserver(binding: MineFragmentMineInfoBinding) {
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
            AuthState.Unknown -> showLoading()

            AuthState.LoggedOut -> finishWithSessionExpired()

            is AuthState.LoggedIn -> {
                val previousId = loadedUserId
                if (previousId == null) {
                    applyUser(state.user)
                } else if (previousId != state.user.id) {
                    // 编辑过程中切换账号：不把旧输入保存到新账号。
                    finishWithSessionExpired()
                }
                // 同一用户的状态更新（例如保存成功后同步缓存）不覆盖正在编辑的内容。
            }
        }
    }

    private fun applyUser(user: UserDto) {
        if (view == null) return
        loadedUserId = user.id
        currentAvatarPath = user.avatar?.takeIf { it.isNotBlank() }
        pendingAvatarPath = null
        previewUri = null
        binding.etNickname.setText(user.nickname)
        binding.etNickname.setSelection(binding.etNickname.text?.length ?: 0)
        binding.etBio.setText(user.bio.orEmpty())
        binding.etBio.setSelection(binding.etBio.text?.length ?: 0)
        hideError()
        updateAvatarUi()
        binding.pbPageLoading.gone()
        binding.scrollContent.visible()
        refreshSaveEnabled()
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbPageLoading.visible()
        binding.scrollContent.gone()
    }

    private fun launchImagePicker() {
        if (view == null || loadedUserId == null || uploading || saving || leaving) return
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun uploadPickedImage(uri: Uri) {
        if (view == null || loadedUserId == null || uploading || saving || leaving) return
        hideError()
        uploading = true
        updateAvatarUi()
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val meta = withContext(Dispatchers.IO) { PickedImageReader.queryMeta(resolver, uri) }
                val mime = PickedImageReader.resolveMimeType(resolver, uri, meta.fileName)
                if (mime == null || !RecipeMediaRules.isAllowedMimeType(mime)) {
                    showImageError(R.string.mine_info_image_invalid_type)
                    return@launch
                }
                val knownSize = meta.size
                if (knownSize != null && knownSize > 0L &&
                    !RecipeMediaRules.isWithinSizeLimit(knownSize)
                ) {
                    showImageError(R.string.mine_info_image_too_large)
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) { PickedImageReader.readBytes(resolver, uri) }
                if (bytes == null || bytes.isEmpty()) {
                    showImageError(R.string.mine_info_image_read_failed)
                    return@launch
                }
                if (!RecipeMediaRules.isWithinSizeLimit(bytes.size.toLong())) {
                    showImageError(R.string.mine_info_image_too_large)
                    return@launch
                }
                if (view == null) return@launch

                // 先展示本地预览；上传完成前 pendingAvatarPath 保持 null，保存会被阻止。
                previewUri = uri
                pendingAvatarPath = null
                updateAvatarUi()

                val path = mediaRepository.uploadImage(
                    bytes = bytes,
                    mimeType = mime,
                    fileName = RecipeMediaRules.uploadFileName(meta.fileName, mime)
                )
                if (view == null) return@launch
                pendingAvatarPath = path
                updateAvatarUi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    finishWithSessionExpired()
                } else {
                    discardFailedPreview()
                    showImageError(e.message ?: getString(R.string.mine_info_image_upload_failed))
                }
            } catch (e: Exception) {
                discardFailedPreview()
                showImageError(R.string.mine_info_image_upload_failed)
            } finally {
                uploading = false
                if (view != null) updateAvatarUi()
            }
        }
    }

    /**
     * 丢弃本次失败的本地预览，恢复已保存头像或首字占位，
     * 避免预览留在页面上却因没有可提交路径而永久阻断保存。
     */
    private fun discardFailedPreview() {
        if (view == null) return
        previewUri = null
        pendingAvatarPath = null
        updateAvatarUi()
    }

    private fun updateAvatarUi() {
        if (view == null) return
        val local = previewUri
        val remote = NetworkConfig.resolveMediaUrl(pendingAvatarPath ?: currentAvatarPath)
        when {
            local != null -> {
                binding.tvAvatarFallback.gone()
                binding.ivAvatar.visible()
                binding.ivAvatar.load(local)
            }

            remote != null -> {
                binding.tvAvatarFallback.gone()
                binding.ivAvatar.visible()
                binding.ivAvatar.load(remote)
            }

            else -> {
                binding.ivAvatar.gone()
                binding.tvAvatarFallback.visible()
                binding.tvAvatarFallback.text = firstCharacter(nicknameText())
            }
        }
        binding.pbAvatarUpload.visibility = if (uploading) View.VISIBLE else View.GONE
        refreshSaveEnabled()
    }

    private fun saveProfile() {
        if (saving || uploading || leaving) return
        val loadedId = loadedUserId
        if (loadedId == null) {
            finishWithSessionExpired()
            return
        }
        val user = currentUser()
        if (user == null) {
            finishWithSessionExpired()
            return
        }
        if (user.id != loadedId) {
            finishWithSessionExpired()
            return
        }
        val nickname = nicknameText().trim()
        if (nickname.length !in 1..UserRepository.MAX_NICKNAME_LENGTH) {
            showError(getString(R.string.mine_info_nickname_invalid))
            return
        }
        val bio = binding.etBio.text?.toString().orEmpty().trim()
        if (bio.length > UserRepository.MAX_BIO_LENGTH) {
            showError(getString(R.string.mine_info_bio_too_long))
            return
        }
        if (previewUri != null && pendingAvatarPath == null) {
            showError(getString(R.string.mine_info_image_upload_pending))
            return
        }

        saving = true
        hideError()
        binding.btnSave.text = getString(R.string.mine_info_saving)
        refreshSaveEnabled()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updated = userRepository.updateProfile(
                    nickname = nickname,
                    // 只有本次上传成功的头像才提交；null 表示不修改头像。
                    avatar = pendingAvatarPath,
                    // 简介清空时提交空字符串，确保 PATCH 能覆盖旧值。
                    bio = bio
                )
                SessionManager.updateUser(updated)
                if (view != null) {
                    toast(R.string.mine_info_save_success)
                    findNavController().popBackStack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    finishWithSessionExpired()
                } else {
                    showError(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.mine_info_save_failed)
                    )
                }
            } catch (e: Exception) {
                showError(getString(R.string.mine_info_save_failed))
            } finally {
                saving = false
                if (view != null) {
                    binding.btnSave.text = getString(R.string.mine_info_save)
                    refreshSaveEnabled()
                }
            }
        }
    }

    private fun currentUser(): UserDto? {
        return (SessionManager.state.value as? AuthState.LoggedIn)?.user
    }

    private fun nicknameText(): String = binding.etNickname.text?.toString().orEmpty()

    /** 取昵称首字作为头像占位，避免截断 emoji 等代理对字符。 */
    private fun firstCharacter(text: String): String {
        if (text.isEmpty()) return ""
        val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
        return text.substring(0, end)
    }

    private fun showImageError(messageRes: Int) {
        showImageError(getString(messageRes))
    }

    /** 图片错误同时显示在页面错误区与 Toast，保证用户能立即看到。 */
    private fun showImageError(message: String) {
        showError(message)
        toast(message)
    }

    private fun showError(message: String) {
        if (view == null) return
        binding.tvError.text = message
        binding.tvError.visible()
    }

    private fun hideError() {
        if (view == null) return
        binding.tvError.gone()
    }

    private fun refreshSaveEnabled() {
        if (view == null) return
        binding.btnSave.isEnabled = loadedUserId != null && !saving && !uploading && !leaving
    }

    private fun finishWithSessionExpired() {
        if (view == null || leaving) return
        leaving = true
        toast(R.string.mine_info_session_expired)
        findNavController().popBackStack()
    }

    private fun toast(messageRes: Int) {
        if (view == null) return
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        if (view == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
