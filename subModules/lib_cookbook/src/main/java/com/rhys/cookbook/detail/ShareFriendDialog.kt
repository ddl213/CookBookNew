package com.rhys.cookbook.detail

import android.content.Context
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.BaseBottomSheetDialog
import com.android.common.ext.buildAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.example.network.AuthException
import com.example.network.bean.FriendDto
import com.example.network.repository.FriendRepository
import com.example.network.session.SessionManager
import com.rhys.cookbook.R
import com.rhys.cookbook.databinding.CookbookDialogShareFriendBinding
import com.rhys.cookbook.databinding.CookbookItemShareFriendBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * App 内好友选择弹窗。
 *
 * 只负责好友列表分页与选择回调；真正的分享请求由详情页发起，避免弹窗持有页面状态。
 */
class ShareFriendDialog(
    context: Context
) : BaseBottomSheetDialog<CookbookDialogShareFriendBinding>(context, CookbookDialogShareFriendBinding::inflate) {

    /** 详情页设置选择回调；弹窗只负责列表，不直接发分享请求。 */
    var onFriendSelected: ((FriendDto) -> Unit)? = null

    private val repository = FriendRepository()
    private val friends = mutableListOf<FriendDto>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loaded = false
    private var submitting = false
    private var viewAlive = false
    private var requestGeneration = 0

    override fun initView(binding: CookbookDialogShareFriendBinding) {
        viewAlive = true
        // 好友列表需要可滚动上限；按屏幕高度动态计算，不写死设计稿尺寸。
        binding.rvShareFriends.layoutParams = binding.rvShareFriends.layoutParams.apply {
            height = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        }

        binding.tvShareCancel.setOnClickListener { dismissDialog() }
        binding.btnShareRetry.setOnClickListener { loadFirstPage() }

        binding.rvShareFriends.liner().buildAdapter<FriendDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(CookbookItemShareFriendBinding::class.java)
            bind<CookbookItemShareFriendBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                itemBinding.tvFriendName.text = item.displayName
                itemBinding.tvFriendUsername.text = "@${item.username}"
                bindDetailAvatar(
                    itemBinding.ivFriendAvatar,
                    itemBinding.tvFriendAvatarFallback,
                    item.displayName,
                    item.avatar
                )
            }
            setOnItemClickListener { _, _, position ->
                if (submitting) return@setOnItemClickListener
                val friend = friends.getOrNull(position) ?: return@setOnItemClickListener
                onFriendSelected?.invoke(friend)
            }
        }
        binding.rvShareFriends.addItemDecoration(RvDecoration.Builder().setVertical(2).build())
        binding.rvShareFriends.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) loadMore()
            }
        })
    }

    override fun initData(binding: CookbookDialogShareFriendBinding) {
        loadFirstPage()
    }

    override fun onStop() {
        viewAlive = false
        scope.cancel()
        super.onStop()
    }

    /** 详情页提交分享期间禁用重复选择与取消，避免重复请求。 */
    fun setSubmitting(value: Boolean) {
        submitting = value
        if (!viewAlive) return
        binding.tvShareCancel.isEnabled = !value
        binding.rvShareFriends.isEnabled = !value
        binding.tvShareTitle.text = if (value) {
            context.getString(R.string.cookbook_share_submitting)
        } else {
            context.getString(R.string.cookbook_share_title)
        }
    }

    private fun loadFirstPage() {
        if (loading) return
        loaded = false
        page = 1
        hasMore = true
        load(reset = true)
    }

    private fun loadMore() {
        if (loading || !loaded || !hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) showLoading()
        val generation = if (reset) ++requestGeneration else requestGeneration
        scope.launch {
            try {
                val result = repository.listFriends(page = page)
                if (!isCurrent(generation)) return@launch
                if (reset) friends.clear()
                friends.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                binding.rvShareFriends.submitList(friends.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (!isCurrent(generation)) return@launch
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    dismissDialog()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toast(e.message ?: context.getString(R.string.cookbook_share_error))
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) return@launch
                if (reset) {
                    showError(context.getString(R.string.cookbook_share_error))
                } else {
                    toast(R.string.cookbook_share_error)
                }
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return viewAlive && generation == requestGeneration
    }

    private fun renderContent() {
        if (!viewAlive) return
        binding.pbShare.gone()
        binding.llShareError.gone()
        if (friends.isEmpty()) {
            binding.rvShareFriends.gone()
            binding.tvShareEmpty.visible()
        } else {
            binding.tvShareEmpty.gone()
            binding.rvShareFriends.visible()
        }
    }

    private fun showLoading() {
        if (!viewAlive) return
        binding.pbShare.visible()
        binding.rvShareFriends.gone()
        binding.tvShareEmpty.gone()
        binding.llShareError.gone()
    }

    private fun showError(message: String?) {
        if (!viewAlive) return
        binding.pbShare.gone()
        binding.rvShareFriends.gone()
        binding.tvShareEmpty.gone()
        binding.tvShareError.text = message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.cookbook_share_error)
        binding.llShareError.visible()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
