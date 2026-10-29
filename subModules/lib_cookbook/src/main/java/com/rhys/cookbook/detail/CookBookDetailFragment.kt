package com.rhys.cookbook.detail

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildAdapter
import com.android.common.ext.dp
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.load
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.Constants.KEY_BUNDLE_RECIPE_ID
import com.campaign.common.constants.Constants.KEY_USER_ID
import com.campaign.common.constants.RoutePath.PAGE_COOK_BOOK_DETAIL
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_USER_PROFILE
import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.CommentDto
import com.example.network.bean.FriendDto
import com.example.network.bean.RecipeDto
import com.example.network.repository.RecipeRepository
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.marky.route.api.NRoute
import com.rhys.cookbook.R
import com.rhys.cookbook.databinding.CookbookFragmentDetailBinding
import com.rhys.cookbook.databinding.CookbookItemCommentBinding
import com.rhys.cookbook.form.RecipeFormOptions
import com.rhys.order.ui.ReservationConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 菜谱详情：展示内容、收藏、作者分享与内嵌评论。
 *
 * 可见性、收藏与分享资格全部由后端判定；404 统一提示不存在或无权限，
 * 网络失败单独提供重试，避免泄露私密菜谱是否存在。
 */
@Route(PAGE_COOK_BOOK_DETAIL)
class CookBookDetailFragment :
    BaseBindFragment<CookbookFragmentDetailBinding>(CookbookFragmentDetailBinding::inflate) {

    private val repository = RecipeRepository()
    private val comments = mutableListOf<CommentDto>()

    private val recipeId: Long by lazy(LazyThreadSafetyMode.NONE) {
        readRecipeId(arguments)
    }

    private var recipe: RecipeDto? = null
    private var loggedIn = false
    private var currentUserId = 0L
    private var detailLoading = false
    private var detailLoaded = false
    private var detailGeneration = 0
    private var reloadAnonymously = false

    private var commentsLoading = false
    private var commentsLoaded = false
    private var commentsPage = 1
    private var commentsHasMore = true
    private var commentsTotal = 0
    private var commentsGeneration = 0

    private var collecting = false
    private var sharing = false
    private var sendingComment = false
    private var shareDialog: ShareFriendDialog? = null
    private var reservationDialog: ReservationConfirmDialog? = null

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: CookbookFragmentDetailBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRetry.setOnClickListener { loadDetail(force = true) }
        binding.btnCollect.setOnClickListener { toggleCollection() }
        binding.btnShare.setOnClickListener { openShareDialog() }
        binding.btnReserve.setOnClickListener { openReservationDialog() }
        binding.btnCommentLogin.setOnClickListener { openLogin() }
        binding.btnCommentsRetry.setOnClickListener { loadComments(reset = true) }
        binding.btnSendComment.setOnClickListener { submitComment() }
        binding.llCommentsMore.setOnClickListener { loadMoreComments() }
        binding.llAuthor.setOnClickListener { openAuthorProfile() }
        binding.scrollContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
                maybeLoadMoreFromScroll()
            }
        )
        // 键盘弹出时把 IME 高度作为根布局底部内边距，保证评论输入栏不被遮挡。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                imeBottom
            )
            insets
        }
        initCommentList()
    }

    override fun initData(binding: CookbookFragmentDetailBinding) {
        if (recipeId <= 0L) {
            showNotFound()
        } else {
            showLoading()
        }
    }

    override fun initObserver(binding: CookbookFragmentDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state -> renderAuthState(state) }
            }
        }
    }

    override fun onViewDestroy() {
        shareDialog?.dismissDialog()
        shareDialog = null
        reservationDialog?.dismissDialog()
        reservationDialog = null
        // Fragment 实例可能留在返回栈，View 重建时必须丢弃旧页面状态并重新加载。
        detailGeneration++
        detailLoading = false
        detailLoaded = false
        recipe = null
        reloadAnonymously = false
        commentsGeneration++
        commentsLoading = false
        commentsLoaded = false
        commentsPage = 1
        commentsHasMore = true
        commentsTotal = 0
        comments.clear()
        collecting = false
        sharing = false
        sendingComment = false
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        if (recipeId <= 0L) {
            showNotFound()
            return
        }
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                val authChanged = loggedIn
                loggedIn = false
                currentUserId = 0L
                resetComments()
                updateCommentInput()
                if (authChanged) {
                    // 退出后必须按匿名身份重新取详情，避免继续展示上一账号的收藏状态。
                    invalidateDetailRequest()
                    loadDetail(force = true)
                } else if (!detailLoaded && !detailLoading) {
                    loadDetail(force = false)
                }
            }

            is AuthState.LoggedIn -> {
                val userChanged = currentUserId != 0L && currentUserId != state.user.id
                val authChanged = !loggedIn || userChanged
                loggedIn = true
                currentUserId = state.user.id
                when {
                    authChanged -> {
                        resetComments()
                        invalidateDetailRequest()
                        loadDetail(force = true)
                    }

                    !detailLoaded && !detailLoading -> loadDetail(force = false)
                    detailLoaded -> {
                        recipe?.let { renderRecipe(it) }
                        loadCommentsIfNeeded()
                    }
                }
            }
        }
    }

    /** 账号或登录态变化时丢弃在途详情，防止旧账号响应覆盖当前页面。 */
    private fun invalidateDetailRequest() {
        detailGeneration++
        detailLoading = false
        reloadAnonymously = false
    }

    private fun loadDetail(force: Boolean) {
        if (recipeId <= 0L) {
            showNotFound()
            return
        }
        if (detailLoading) return
        if (detailLoaded && !force && recipe != null) {
            renderRecipe(recipe!!)
            loadCommentsIfNeeded()
            return
        }
        detailLoading = true
        detailLoaded = false
        if (force) recipe = null
        val generation = ++detailGeneration
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.get(recipeId)
                if (!isDetailCurrent(generation)) return@launch
                recipe = result
                detailLoaded = true
                renderRecipe(result)
                loadCommentsIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isDetailCurrent(generation)) return@launch
                when {
                    e.isUnauthorized -> {
                        // 登出后由状态流重新按匿名身份加载公开内容。
                        reloadAnonymously = true
                        SessionManager.logout()
                    }

                    e.httpStatus == 404 -> showNotFound()
                    else -> showDetailError(e.message)
                }
            } catch (e: Exception) {
                if (!isDetailCurrent(generation)) return@launch
                showDetailError(getString(R.string.cookbook_detail_error))
            } finally {
                if (generation == detailGeneration) {
                    detailLoading = false
                    if (reloadAnonymously) {
                        reloadAnonymously = false
                        loadDetail(force = false)
                    }
                }
            }
        }
    }

    private fun isDetailCurrent(generation: Int): Boolean {
        return view != null && generation == detailGeneration
    }

    private fun renderRecipe(recipe: RecipeDto) {
        if (view == null) return
        detailLoaded = true

        binding.pbPageLoading.gone()
        binding.llError.gone()
        binding.llNotFound.gone()
        binding.scrollContent.visible()
        binding.llContent.visible()
        updateCommentInput()

        val imageUrl = NetworkConfig.resolveMediaUrl(recipe.imageUrl)
        if (imageUrl == null) {
            binding.ivCover.setImageDrawable(null)
        } else {
            binding.ivCover.load(imageUrl)
        }

        binding.tvName.text = recipe.name

        val author = recipe.author
        if (author != null && author.id > 0L) {
            binding.llAuthor.visible()
            binding.tvAuthorName.text = author.displayName
            bindDetailAvatar(
                binding.ivAuthorAvatar,
                binding.tvAuthorFallback,
                author.displayName,
                author.avatar
            )
        } else {
            binding.llAuthor.gone()
        }

        renderBadges(recipe)
        renderDescription(recipe)
        renderIngredients(recipe)
        renderSteps(recipe)
        renderSocialActions(recipe)
        updateCollectButton(recipe)
        updateShareButton(recipe)
    }

    private fun isDraft(recipe: RecipeDto?): Boolean {
        return recipe != null &&
            RecipeFormOptions.statusOf(recipe.status) == RecipeFormOptions.STATUS_DRAFT
    }

    private fun renderSocialActions(recipe: RecipeDto) {
        if (isDraft(recipe)) {
            binding.llCommentsSection.gone()
            binding.llActions.gone()
            binding.btnCollect.gone()
            binding.btnReserve.gone()
        } else {
            binding.llCommentsSection.visible()
            binding.llActions.visible()
            binding.btnCollect.visible()
            binding.btnReserve.visible()
        }
    }

    private fun renderBadges(recipe: RecipeDto) {
        val category = recipe.category.orEmpty().trim()
        if (category.isEmpty()) {
            binding.tvCategory.gone()
        } else {
            binding.tvCategory.text = category
            binding.tvCategory.visible()
        }

        binding.tvVisibility.text = if (
            RecipeFormOptions.visibilityOf(recipe.visibility) == RecipeFormOptions.VISIBILITY_PUBLIC
        ) {
            getString(R.string.cookbook_visibility_public)
        } else {
            getString(R.string.cookbook_visibility_friends)
        }
        binding.tvVisibility.visible()

        if (RecipeFormOptions.statusOf(recipe.status) == RecipeFormOptions.STATUS_DRAFT) {
            binding.tvStatus.text = getString(R.string.cookbook_status_draft)
            binding.tvStatus.visible()
        } else {
            binding.tvStatus.gone()
        }
    }

    private fun renderDescription(recipe: RecipeDto) {
        val description = recipe.description.orEmpty().trim()
        if (description.isEmpty()) {
            binding.tvDescription.gone()
        } else {
            binding.tvDescription.text = description
            binding.tvDescription.visible()
        }
    }

    private fun renderIngredients(recipe: RecipeDto) {
        val items = recipe.ingredients.map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            binding.tvIngredientsTitle.gone()
            binding.llIngredients.gone()
            return
        }
        binding.tvIngredientsTitle.visible()
        binding.llIngredients.visible()
        fillLines(binding.llIngredients, items) { _, value ->
            getString(R.string.cookbook_ingredient_format, value)
        }
    }

    private fun renderSteps(recipe: RecipeDto) {
        val items = recipe.steps.map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            binding.tvStepsTitle.gone()
            binding.llSteps.gone()
            return
        }
        binding.tvStepsTitle.visible()
        binding.llSteps.visible()
        fillLines(binding.llSteps, items) { index, value ->
            getString(R.string.cookbook_step_format, index + 1, value)
        }
    }

    private fun fillLines(
        container: LinearLayout,
        values: List<String>,
        format: (Int, String) -> String
    ) {
        container.removeAllViews()
        values.forEachIndexed { index, value ->
            val textView = TextView(requireContext()).apply {
                text = format(index, value)
                setTextColor(ContextCompat.getColor(context, com.android.common.R.color.textNormal))
                textSize = 14f
                setLineSpacing(2.dp(), 1f)
                setPadding(0, 5.dp().toInt(), 0, 5.dp().toInt())
            }
            container.addView(
                textView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun updateCollectButton(recipe: RecipeDto) {
        if (view == null) return
        binding.btnCollect.text = getString(
            if (recipe.collected) R.string.cookbook_action_collected else R.string.cookbook_action_collect
        )
        binding.btnCollect.isEnabled = detailLoaded && !collecting
    }

    private fun updateShareButton(recipe: RecipeDto) {
        if (view == null) return
        val authorId = recipe.author?.id ?: 0L
        val canShare = !isDraft(recipe) && loggedIn && currentUserId > 0L && authorId == currentUserId
        if (canShare) binding.btnShare.visible() else binding.btnShare.gone()
        binding.btnShare.isEnabled = canShare && detailLoaded && !sharing
    }

    private fun toggleCollection() {
        val current = recipe ?: return
        if (isDraft(current)) return
        if (!loggedIn) {
            openLogin()
            return
        }
        if (collecting) return
        collecting = true
        binding.btnCollect.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val collected = repository.setCollected(current.id, !current.collected)
                if (view == null) return@launch
                val updated = (recipe ?: current).copy(collected = collected)
                recipe = updated
                updateCollectButton(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (view == null) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    toast(R.string.cookbook_session_expired)
                } else if (e.httpStatus == 404) {
                    showNotFound()
                } else {
                    toast(e.message ?: getString(R.string.cookbook_error_default))
                }
            } catch (e: Exception) {
                if (view == null) return@launch
                toast(R.string.cookbook_error_default)
            } finally {
                collecting = false
                if (view != null) binding.btnCollect.isEnabled = detailLoaded
            }
        }
    }

    /**
     * 预订入口：仅登录用户、非本人、后端判定 canReserve 的已发布菜谱可打开确认弹窗。
     * 不把公开菜谱默认视为可预订，资格仍以服务端返回为准。
     */
    private fun openReservationDialog() {
        val current = recipe ?: return
        if (isDraft(current)) return
        if (!loggedIn) {
            openLogin()
            return
        }
        val author = current.author
        if (author == null || author.id <= 0L) {
            toast(R.string.cookbook_reserve_author_missing)
            return
        }
        if (author.id == currentUserId) {
            toast(R.string.cookbook_reserve_self)
            return
        }
        if (!current.canReserve) {
            toast(R.string.cookbook_reserve_not_friend)
            return
        }
        reservationDialog?.dismissDialog()
        val dialog = ReservationConfirmDialog(
            context = requireContext(),
            sellerId = author.id,
            sellerName = author.displayName,
            currentRecipe = current
        )
        reservationDialog = dialog
        dialog.setOnDismissListener {
            if (reservationDialog === dialog) reservationDialog = null
        }
        dialog.show()
    }

    private fun openShareDialog() {
        val current = recipe ?: return
        if (isDraft(current)) return
        if (!loggedIn) {
            openLogin()
            return
        }
        val authorId = current.author?.id ?: 0L
        if (authorId <= 0L || authorId != currentUserId || sharing) return
        val dialog = ShareFriendDialog(requireContext())
        dialog.onFriendSelected = { friend -> performShare(current.id, friend, dialog) }
        shareDialog = dialog
        dialog.show()
    }

    private fun performShare(recipeId: Long, friend: FriendDto, dialog: ShareFriendDialog) {
        if (sharing) return
        sharing = true
        dialog.setSubmitting(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.share(recipeId, friend.id)
                if (view == null) return@launch
                if (!result.shared) {
                    toast(R.string.cookbook_share_failed)
                    return@launch
                }
                dialog.dismissDialog()
                toast(getString(R.string.cookbook_share_success, friend.displayName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (view == null) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    dialog.dismissDialog()
                    toast(R.string.cookbook_session_expired)
                } else {
                    toast(e.message ?: getString(R.string.cookbook_share_failed))
                }
            } catch (e: Exception) {
                if (view == null) return@launch
                toast(R.string.cookbook_share_failed)
            } finally {
                sharing = false
                if (view != null && dialog.isShowing) dialog.setSubmitting(false)
            }
        }
    }

    private fun initCommentList() {
        binding.rvComments.liner().buildAdapter<CommentDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(CookbookItemCommentBinding::class.java)
            bind<CookbookItemCommentBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvCommentAuthor.text = item.author.displayName
                itemBinding.tvCommentContent.text = item.content
                val time = formatCommentTime(item.createdAt)
                if (time.isEmpty()) {
                    itemBinding.tvCommentTime.gone()
                } else {
                    itemBinding.tvCommentTime.text = time
                    itemBinding.tvCommentTime.visible()
                }
                bindDetailAvatar(
                    itemBinding.ivCommentAvatar,
                    itemBinding.tvCommentAvatarFallback,
                    item.author.displayName,
                    item.author.avatar
                )
            }
        }
        binding.rvComments.addItemDecoration(RvDecoration.Builder().setVertical(2).build())
    }

    private fun loadCommentsIfNeeded() {
        if (isDraft(recipe)) return
        if (loggedIn && detailLoaded && !commentsLoaded && !commentsLoading) {
            loadComments(reset = true)
        }
    }

    private fun loadMoreComments() {
        if (!loggedIn || !commentsLoaded || commentsLoading || !commentsHasMore) return
        loadComments(reset = false)
    }

    private fun maybeLoadMoreFromScroll() {
        if (!loggedIn || !commentsLoaded || commentsLoading || !commentsHasMore) return
        val content = binding.scrollContent.getChildAt(0) ?: return
        val viewport = binding.scrollContent.height
        if (viewport <= 0) return
        val threshold = 32.dp().toInt()
        if (binding.scrollContent.scrollY + viewport >= content.height - threshold) {
            loadComments(reset = false)
        }
    }

    private fun loadComments(reset: Boolean) {
        if (isDraft(recipe)) return
        if (!detailLoaded || !loggedIn || commentsLoading) return
        if (!reset && (!commentsLoaded || !commentsHasMore)) return
        commentsLoading = true
        if (reset) showCommentsLoading() else showCommentsLoadingMore()
        val generation = ++commentsGeneration
        val pageToLoad = if (reset) 1 else commentsPage
        viewLifecycleOwner.lifecycleScope.launch {
            var errorShown = false
            try {
                val result = repository.listComments(recipeId, page = pageToLoad)
                if (!isCommentsCurrent(generation)) return@launch
                if (reset) comments.clear()
                // 发表后本地已插入的评论可能出现在后续分页中，按 id 去重避免重复展示。
                result.items.forEach { item ->
                    if (comments.none { it.id == item.id }) comments.add(item)
                }
                // 服务端按时间升序返回，本地追加的新评论 id 最大；分页合并后仍按 id 排序。
                comments.sortBy { it.id }
                commentsTotal = result.total
                commentsHasMore = result.hasMore
                commentsPage = result.page + 1
                commentsLoaded = true
                binding.rvComments.submitList(comments.toList())
                renderCommentsState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCommentsCurrent(generation)) return@launch
                when {
                    e.isUnauthorized -> {
                        SessionManager.logout()
                        toast(R.string.cookbook_session_expired)
                    }

                    e.httpStatus == 404 -> showNotFound()
                    reset -> {
                        errorShown = comments.isEmpty()
                        showCommentsError(e.message)
                    }

                    else -> toast(e.message ?: getString(R.string.cookbook_comments_error))
                }
            } catch (e: Exception) {
                if (!isCommentsCurrent(generation)) return@launch
                if (reset) {
                    errorShown = comments.isEmpty()
                    showCommentsError(getString(R.string.cookbook_comments_error))
                } else {
                    toast(R.string.cookbook_comments_error)
                }
            } finally {
                if (generation == commentsGeneration) {
                    commentsLoading = false
                    if (view != null && !errorShown) renderCommentsState()
                }
            }
        }
    }

    private fun isCommentsCurrent(generation: Int): Boolean {
        return view != null && generation == commentsGeneration
    }

    private fun submitComment() {
        val current = recipe ?: return
        if (isDraft(current)) return
        if (!loggedIn) {
            openLogin()
            return
        }
        if (sendingComment) return
        val content = binding.etComment.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) {
            toast(R.string.cookbook_comment_empty)
            return
        }
        if (content.length > RecipeRepository.MAX_COMMENT_LENGTH) {
            toast(R.string.cookbook_comment_too_long)
            return
        }
        sendingComment = true
        binding.btnSendComment.isEnabled = false
        binding.btnSendComment.text = getString(R.string.cookbook_comment_sending)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val comment = repository.createComment(current.id, content)
                if (view == null) return@launch
                binding.etComment.text?.clear()
                val wasLoaded = commentsLoaded
                if (comments.none { it.id == comment.id }) comments.add(comment)
                commentsTotal += 1
                if (wasLoaded) {
                    binding.rvComments.submitList(comments.toList())
                    renderCommentsState()
                } else {
                    commentsPage = 1
                    commentsHasMore = true
                    renderCommentsState()
                    loadComments(reset = true)
                }
                toast(R.string.cookbook_comment_success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (view == null) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    toast(R.string.cookbook_session_expired)
                } else if (e.httpStatus == 404) {
                    showNotFound()
                } else {
                    toast(e.message ?: getString(R.string.cookbook_comment_error_default))
                }
            } catch (e: Exception) {
                if (view == null) return@launch
                toast(R.string.cookbook_comment_error_default)
            } finally {
                sendingComment = false
                if (view != null) {
                    binding.btnSendComment.text = getString(R.string.cookbook_comment_send)
                    binding.btnSendComment.isEnabled = loggedIn && detailLoaded
                }
            }
        }
    }

    private fun renderCommentsState() {
        if (view == null) return
        updateCommentsTitle()
        if (!loggedIn) {
            binding.pbComments.gone()
            binding.llCommentsError.gone()
            binding.tvCommentsEmpty.gone()
            binding.rvComments.gone()
            binding.llCommentsMore.gone()
            binding.llCommentsLogin.visible()
            return
        }
        binding.llCommentsLogin.gone()
        binding.llCommentsError.gone()
        binding.pbComments.gone()
        if (comments.isEmpty()) {
            binding.rvComments.gone()
            binding.llCommentsMore.gone()
            binding.tvCommentsEmpty.visible()
        } else {
            binding.tvCommentsEmpty.gone()
            binding.rvComments.visible()
            updateCommentsMore()
        }
    }

    private fun showCommentsLoading() {
        if (view == null) return
        updateCommentsTitle()
        binding.pbComments.visible()
        binding.llCommentsLogin.gone()
        binding.tvCommentsEmpty.gone()
        binding.llCommentsError.gone()
        binding.rvComments.gone()
        binding.llCommentsMore.gone()
    }

    private fun showCommentsLoadingMore() {
        if (view == null) return
        binding.llCommentsMore.visible()
        binding.pbCommentsMore.visible()
        binding.tvCommentsMore.text = getString(R.string.cookbook_comments_loading_more)
        binding.llCommentsMore.isClickable = false
    }

    private fun showCommentsError(message: String?) {
        if (view == null) return
        val text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.cookbook_comments_error)
        // 已有评论时不整段隐藏，避免一次刷新失败把用户刚发表的评论也藏掉。
        if (comments.isNotEmpty()) {
            renderCommentsState()
            toast(text)
            return
        }
        updateCommentsTitle()
        binding.pbComments.gone()
        binding.llCommentsLogin.gone()
        binding.tvCommentsEmpty.gone()
        binding.rvComments.gone()
        binding.llCommentsMore.gone()
        binding.tvCommentsError.text = text
        binding.llCommentsError.visible()
    }

    private fun updateCommentsTitle() {
        if (view == null) return
        binding.tvCommentsTitle.text = if (loggedIn && commentsLoaded) {
            getString(R.string.cookbook_comments_title_count, commentsTotal)
        } else {
            getString(R.string.cookbook_comments_title)
        }
    }

    private fun updateCommentsMore() {
        if (view == null) return
        binding.llCommentsMore.visible()
        when {
            commentsLoading -> {
                binding.pbCommentsMore.visible()
                binding.tvCommentsMore.text = getString(R.string.cookbook_comments_loading_more)
                binding.llCommentsMore.isClickable = false
            }

            commentsHasMore -> {
                binding.pbCommentsMore.gone()
                binding.tvCommentsMore.text = getString(R.string.cookbook_comments_load_more)
                binding.llCommentsMore.isClickable = true
            }

            else -> {
                binding.pbCommentsMore.gone()
                binding.tvCommentsMore.text = getString(R.string.cookbook_comments_no_more)
                binding.llCommentsMore.isClickable = false
            }
        }
    }

    private fun resetComments() {
        commentsGeneration++
        commentsLoading = false
        commentsLoaded = false
        commentsPage = 1
        commentsHasMore = true
        commentsTotal = 0
        comments.clear()
        if (view != null) {
            binding.rvComments.submitList(emptyList())
            renderCommentsState()
        }
    }

    private fun updateCommentInput() {
        if (view == null) return
        val canComment = !isDraft(recipe) && loggedIn && detailLoaded
        if (canComment) {
            binding.llCommentInput.visible()
        } else {
            binding.llCommentInput.gone()
        }
        binding.etComment.isEnabled = canComment
        binding.btnSendComment.isEnabled = canComment && !sendingComment
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbPageLoading.visible()
        binding.scrollContent.gone()
        binding.btnReserve.gone()
        binding.llError.gone()
        binding.llNotFound.gone()
        binding.llCommentInput.gone()
    }

    private fun showDetailError(message: String?) {
        if (view == null) return
        binding.pbPageLoading.gone()
        binding.scrollContent.gone()
        binding.btnReserve.gone()
        binding.llNotFound.gone()
        binding.llCommentInput.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.cookbook_detail_error)
        binding.llError.visible()
    }

    private fun showNotFound() {
        if (view == null) return
        shareDialog?.dismissDialog()
        shareDialog = null
        recipe = null
        detailLoaded = false
        resetComments()
        binding.pbPageLoading.gone()
        binding.scrollContent.gone()
        binding.btnReserve.gone()
        binding.llError.gone()
        binding.llCommentInput.gone()
        binding.llNotFound.visible()
    }

    private fun openLogin() {
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_LOGIN)
                .go()
        }.onFailure {
            toast(R.string.cookbook_login_failed)
        }
    }

    private fun openAuthorProfile() {
        val authorId = recipe?.author?.id ?: 0L
        if (authorId <= 0L) return
        runCatching {
            NRoute.withNavController(findNavController())
                .deepLink(PAGE_USER_PROFILE)
                .args(bundleOf(KEY_USER_ID to authorId))
                .go()
        }.onFailure {
            toast(R.string.cookbook_error_default)
        }
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
