package com.rhys.cookbook.add

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.android.common.view.TitleBar
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_ADD
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.PickedImageReader
import com.example.network.RecipeMediaRules
import com.example.network.bean.RecipeDto
import com.example.network.bean.RecipeWriteBody
import com.example.network.repository.RecipeRepository
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.cookbook.R
import com.rhys.cookbook.databinding.CookbookFragmentAddRecipesBinding
import com.rhys.cookbook.form.RecipeFormErrors
import com.rhys.cookbook.form.RecipeFormInput
import com.rhys.cookbook.form.RecipeFormOptions
import com.rhys.cookbook.form.RecipeFormValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 新建 / 编辑菜谱表单。
 *
 * 路由可选携带 [KEY_BUNDLE_RECIPE_ID]；缺省为新建，存在时先 GET 预填。
 * 图片使用系统照片选择器，校验 MIME 与大小后上传；上传未完成或失败时禁止保存，
 * 避免把不存在的图片 URL 写入菜谱。草稿与已发布遵守同一必填规则。
 */
@Route(PAGE_COOK_BOOK_ADD)
class AddRecipesFragment :
    BaseBindFragment<CookbookFragmentAddRecipesBinding>(CookbookFragmentAddRecipesBinding::inflate) {

    private val repository = RecipeRepository()

    private val recipeId: Long by lazy(LazyThreadSafetyMode.NONE) {
        readRecipeId(arguments)
    }

    private val isEditing: Boolean
        get() = recipeId > 0L

    /** 后端已保存的图片；移除后置空，避免表单再次回退到旧图片。 */
    private var savedImageUrl: String? = null

    /** 当前准备提交的图片 URL。空字符串表示显式清空，保存时不能省略该字段。 */
    private var currentImageUrl: String? = null

    /** 本地预览 URI；非空表示用户正在查看新选择或上传失败的图片。 */
    private var previewUri: Uri? = null

    private var loaded = false
    private var loading = false
    private var uploading = false
    private var saving = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadPickedImage(uri)
    }

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: CookbookFragmentAddRecipesBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }

        bindCategoryChips()

        binding.ivImage.setOnClickListener { launchImagePicker() }
        // 提示文字覆盖在图片上方，必须同样可点击，否则会拦截图片点击。
        binding.tvImageHint.setOnClickListener { launchImagePicker() }
        binding.tvRemoveImage.setOnClickListener { removeImage() }
        binding.btnSave.setOnClickListener { saveRecipe() }
        binding.btnLoadRetry.setOnClickListener { loadRecipe() }

        binding.etCategory.doAfterTextChanged { updateCategoryChipSelection() }

        refreshSaveEnabled()
    }

    override fun initData(binding: CookbookFragmentAddRecipesBinding) {
        if (isEditing) {
            loadRecipe()
        } else {
            renderNewForm()
        }
    }

    private fun renderNewForm() {
        if (view == null) return
        loaded = true
        binding.titleBar.setTitle(getString(R.string.cookbook_add_title))
        binding.rgVisibility.check(R.id.rb_visibility_friends)
        binding.rgStatus.check(R.id.rb_status_draft)
        savedImageUrl = null
        currentImageUrl = null
        previewUri = null
        updateImageUi()
        binding.scrollContent.visible()
        binding.llLoadError.gone()
        binding.pbPageLoading.gone()
        refreshSaveEnabled()
    }

    private fun loadRecipe() {
        if (!isEditing || loading) return
        loaded = false
        loading = true
        binding.titleBar.setTitle(getString(R.string.cookbook_edit_title))
        binding.pbPageLoading.visible()
        binding.llLoadError.gone()
        binding.scrollContent.gone()
        refreshSaveEnabled()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val recipe = repository.get(recipeId)
                if (view == null) return@launch
                applyRecipe(recipe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    toast(R.string.cookbook_form_session_expired)
                    if (view != null) findNavController().popBackStack()
                } else {
                    showLoadError(e.message)
                }
            } catch (e: Exception) {
                showLoadError(getString(R.string.cookbook_form_load_failed))
            } finally {
                loading = false
                if (view != null) {
                    binding.pbPageLoading.gone()
                    refreshSaveEnabled()
                }
            }
        }
    }

    private fun applyRecipe(recipe: RecipeDto) {
        savedImageUrl = recipe.imageUrl?.takeIf { it.isNotBlank() }
        currentImageUrl = savedImageUrl
        previewUri = null
        binding.etName.setText(recipe.name)
        binding.etCategory.setText(recipe.category.orEmpty())
        binding.etDescription.setText(recipe.description.orEmpty())
        binding.etIngredients.setText(recipe.ingredients.joinToString("\n"))
        binding.etSteps.setText(recipe.steps.joinToString("\n"))
        if (RecipeFormOptions.visibilityOf(recipe.visibility) == RecipeFormOptions.VISIBILITY_PUBLIC) {
            binding.rbVisibilityPublic.isChecked = true
        } else {
            binding.rbVisibilityFriends.isChecked = true
        }
        if (RecipeFormOptions.statusOf(recipe.status) == RecipeFormOptions.STATUS_PUBLISHED) {
            binding.rbStatusPublished.isChecked = true
        } else {
            binding.rbStatusDraft.isChecked = true
        }
        updateImageUi()
        updateCategoryChipSelection()
        clearFieldErrors()
        hideFormError()
        loaded = true
        binding.scrollContent.visible()
        binding.llLoadError.gone()
        binding.pbPageLoading.gone()
        refreshSaveEnabled()
    }

    private fun showLoadError(message: String?) {
        if (view == null) return
        binding.scrollContent.gone()
        binding.pbPageLoading.gone()
        binding.tvLoadError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.cookbook_form_load_failed)
        binding.llLoadError.visible()
        refreshSaveEnabled()
    }

    private fun launchImagePicker() {
        if (!loaded || uploading || saving) return
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun bindCategoryChips() {
        val chips = listOf(
            binding.tvCategoryHome to getString(R.string.cookbook_category_home),
            binding.tvCategoryBreakfast to getString(R.string.cookbook_category_breakfast),
            binding.tvCategoryDessert to getString(R.string.cookbook_category_dessert)
        )
        chips.forEach { (view, value) ->
            view.setOnClickListener {
                binding.etCategory.setText(value)
                binding.etCategory.setSelection(binding.etCategory.text?.length ?: 0)
            }
        }
    }

    private fun updateCategoryChipSelection() {
        if (view == null) return
        val current = binding.etCategory.text?.toString()?.trim().orEmpty()
        binding.tvCategoryHome.isSelected = current == getString(R.string.cookbook_category_home)
        binding.tvCategoryBreakfast.isSelected = current == getString(R.string.cookbook_category_breakfast)
        binding.tvCategoryDessert.isSelected = current == getString(R.string.cookbook_category_dessert)
    }

    private fun uploadPickedImage(uri: Uri) {
        if (view == null || !loaded || uploading || saving) return
        hideFormError()
        uploading = true
        binding.pbImageUpload.visible()
        refreshSaveEnabled()
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val meta = withContext(Dispatchers.IO) { PickedImageReader.queryMeta(resolver, uri) }
                val mime = PickedImageReader.resolveMimeType(resolver, uri, meta.fileName)
                if (mime == null || !RecipeMediaRules.isAllowedMimeType(mime)) {
                    showImageError(R.string.cookbook_form_image_invalid_type)
                    return@launch
                }
                val knownSize = meta.size
                if (knownSize != null && knownSize > 0L &&
                    !RecipeMediaRules.isWithinSizeLimit(knownSize)
                ) {
                    showImageError(R.string.cookbook_form_image_too_large)
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) { PickedImageReader.readBytes(resolver, uri) }
                if (bytes == null || bytes.isEmpty()) {
                    showImageError(R.string.cookbook_form_image_read_failed)
                    return@launch
                }
                if (!RecipeMediaRules.isWithinSizeLimit(bytes.size.toLong())) {
                    showImageError(R.string.cookbook_form_image_too_large)
                    return@launch
                }
                if (view == null) return@launch

                // 先展示本地预览，但 URL 置空，上传完成前不允许保存。
                previewUri = uri
                currentImageUrl = null
                updateImageUi()

                val url = repository.uploadImage(
                    bytes = bytes,
                    mimeType = mime,
                    fileName = RecipeMediaRules.uploadFileName(meta.fileName, mime)
                )
                if (view == null) return@launch
                currentImageUrl = url
                updateImageUi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    toast(R.string.cookbook_form_session_expired)
                    findNavController().popBackStack()
                } else {
                    showImageError(e.message ?: getString(R.string.cookbook_form_image_upload_failed))
                    updateImageUi()
                }
            } catch (e: Exception) {
                showImageError(R.string.cookbook_form_image_upload_failed)
                updateImageUi()
            } finally {
                uploading = false
                if (view != null) {
                    binding.pbImageUpload.gone()
                    refreshSaveEnabled()
                }
            }
        }
    }

    /**
     * 移除最终图片：提交空字符串清空后端 imageUrl。
     * 先清 savedImageUrl，避免后续逻辑回退到旧图；未保存前不会改动后端数据。
     */
    private fun removeImage() {
        if (uploading || saving) return
        previewUri = null
        savedImageUrl = null
        currentImageUrl = ""
        updateImageUi()
        hideFormError()
        refreshSaveEnabled()
    }

    private fun updateImageUi() {
        if (view == null) return
        val local = previewUri
        val submitted = currentImageUrl?.takeIf { it.isNotBlank() }
        val remote = NetworkConfig.resolveMediaUrl(submitted)
        when {
            local != null -> binding.ivImage.load(local)
            remote != null -> binding.ivImage.load(remote)
            else -> binding.ivImage.setImageDrawable(null)
        }
        val hasImage = local != null || submitted != null
        if (hasImage) binding.tvImageHint.gone() else binding.tvImageHint.visible()
        // 只要当前有图（含已保存图片）就允许移除；移除后以空字符串清空。
        if (hasImage) binding.tvRemoveImage.visible() else binding.tvRemoveImage.gone()
    }

    private fun saveRecipe() {
        if (saving || uploading || !loaded) return
        val name = binding.etName.text?.toString().orEmpty()
        val category = binding.etCategory.text?.toString().orEmpty()
        val ingredientsText = binding.etIngredients.text?.toString().orEmpty()
        val stepsText = binding.etSteps.text?.toString().orEmpty()

        val errors = RecipeFormValidator.validate(
            RecipeFormInput(
                name = name,
                category = category,
                ingredientsText = ingredientsText,
                stepsText = stepsText
            )
        )
        showFieldErrors(errors)
        if (!errors.isValid) {
            focusFirstInvalid(errors)
            return
        }
        if (previewUri != null && currentImageUrl == null) {
            showFormError(getString(R.string.cookbook_form_image_upload_pending))
            return
        }

        val body = RecipeWriteBody(
            name = name.trim(),
            // 清空简介/图片时发送空字符串，使 PATCH 能真正覆盖旧值。
            description = binding.etDescription.text?.toString()?.trim().orEmpty(),
            imageUrl = currentImageUrl ?: "",
            category = category.trim(),
            visibility = if (binding.rbVisibilityPublic.isChecked) {
                RecipeFormOptions.VISIBILITY_PUBLIC
            } else {
                RecipeFormOptions.VISIBILITY_FRIENDS
            },
            status = if (binding.rbStatusPublished.isChecked) {
                RecipeFormOptions.STATUS_PUBLISHED
            } else {
                RecipeFormOptions.STATUS_DRAFT
            },
            ingredients = RecipeFormValidator.parseLines(ingredientsText),
            steps = RecipeFormValidator.parseLines(stepsText)
        )

        saving = true
        hideFormError()
        binding.btnSave.text = getString(R.string.cookbook_form_saving)
        refreshSaveEnabled()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isEditing) repository.update(recipeId, body) else repository.create(body)
                if (view != null) {
                    toast(R.string.cookbook_form_saved)
                    findNavController().popBackStack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    toast(R.string.cookbook_form_session_expired)
                    if (view != null) findNavController().popBackStack()
                } else {
                    showFormError(e.message ?: getString(R.string.cookbook_form_error_default))
                }
            } catch (e: Exception) {
                showFormError(getString(R.string.cookbook_form_error_default))
            } finally {
                saving = false
                if (view != null) {
                    binding.btnSave.text = getString(R.string.cookbook_form_save)
                    refreshSaveEnabled()
                }
            }
        }
    }

    private fun focusFirstInvalid(errors: RecipeFormErrors) {
        when {
            errors.name != null -> binding.etName.requestFocus()
            errors.category != null -> binding.etCategory.requestFocus()
            errors.ingredients != null -> binding.etIngredients.requestFocus()
            errors.steps != null -> binding.etSteps.requestFocus()
        }
    }

    private fun showFieldErrors(errors: RecipeFormErrors) {
        if (view == null) return
        setFieldError(binding.tvNameError, errors.name)
        setFieldError(binding.tvCategoryError, errors.category)
        setFieldError(binding.tvIngredientsError, errors.ingredients)
        setFieldError(binding.tvStepsError, errors.steps)
    }

    private fun clearFieldErrors() {
        if (view == null) return
        setFieldError(binding.tvNameError, null)
        setFieldError(binding.tvCategoryError, null)
        setFieldError(binding.tvIngredientsError, null)
        setFieldError(binding.tvStepsError, null)
    }

    private fun setFieldError(view: TextView, message: String?) {
        if (message.isNullOrBlank()) {
            view.gone()
        } else {
            view.text = message
            view.visible()
        }
    }

    private fun showFormError(message: String?) {
        if (view == null) return
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.cookbook_form_error_default)
        binding.tvError.visible()
    }

    /** 图片错误发生在表单顶部，同时用 Toast 保证用户能立即看到。 */
    private fun showImageError(messageRes: Int) {
        showImageError(getString(messageRes))
    }

    private fun showImageError(message: String) {
        showFormError(message)
        toast(message)
    }

    private fun hideFormError() {
        if (view == null) return
        binding.tvError.gone()
    }

    private fun refreshSaveEnabled() {
        if (view == null) return
        binding.btnSave.isEnabled = loaded && !saving && !uploading
    }

    private fun readRecipeId(arguments: Bundle?): Long {
        if (arguments == null) return 0L
        val direct = arguments.getLong(KEY_BUNDLE_RECIPE_ID, 0L)
        if (direct > 0L) return direct
        return NRoute.getArgument(arguments, KEY_BUNDLE_RECIPE_ID).toLongOrNull() ?: 0L
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
