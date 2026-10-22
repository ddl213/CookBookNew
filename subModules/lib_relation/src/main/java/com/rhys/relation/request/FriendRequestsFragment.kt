package com.rhys.relation.request

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.common.R as CommonR
import com.android.common.base.BaseBindFragment
import com.android.common.ext.buildAdapter
import com.android.common.ext.gone
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.visible
import com.android.common.utils.RvDecoration
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_FRIEND_REQUESTS
import com.example.network.AuthException
import com.example.network.bean.FriendRequestDto
import com.example.network.repository.FriendRepository
import com.example.network.repository.FriendRequestDirection
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.marky.route.annotation.Route
import com.rhys.relation.R
import com.rhys.relation.bindRelationAvatar
import com.rhys.relation.databinding.RelationFragmentFriendRequestsBinding
import com.rhys.relation.databinding.RelationItemFriendRequestBinding
import com.rhys.relation.model.FriendRequestStatus
import com.rhys.relation.openLoginPage
import com.rhys.relation.openUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 好友申请列表：收到/发出切换，收到且待处理的申请可接受或拒绝。
 */
@Route(PAGE_FRIEND_REQUESTS)
class FriendRequestsFragment :
    BaseBindFragment<RelationFragmentFriendRequestsBinding>(RelationFragmentFriendRequestsBinding::inflate) {

    private val repository = FriendRepository()
    private val items = mutableListOf<FriendRequestDto>()

    private var direction = FriendRequestDirection.INCOMING
    private var page = 1
    private var hasMore = true
    private var loading = false
    private var loaded = false
    private var loggedIn = false
    private var processingRequestId: Long? = null

    override fun initTitleBar(): TitleBar = binding.titleBar

    override fun initView(binding: RelationFragmentFriendRequestsBinding) {
        binding.titleBar.setLeftClick { findNavController().popBackStack() }
        renderTabs()
        binding.rvRequests.liner().buildAdapter<FriendRequestDto> {
            setDiff(
                areItemsSame = { old, new -> old.id == new.id },
                areContentsSame = { old, new -> old == new }
            )
            setLayout(RelationItemFriendRequestBinding::class.java)
            bind<RelationItemFriendRequestBinding> { holder, _, item, _ ->
                val itemBinding = holder.binding
                val counterpart = if (direction == FriendRequestDirection.INCOMING) item.from else item.to
                itemBinding.tvNickname.text = counterpart.displayName
                itemBinding.tvUsername.text = "@${counterpart.username}"
                bindRelationAvatar(
                    itemBinding.ivAvatar,
                    itemBinding.tvAvatarFallback,
                    counterpart.displayName,
                    counterpart.avatar
                )

                val status = FriendRequestStatus.fromRaw(item.status)
                itemBinding.tvStatus.setText(statusText(status))
                itemBinding.tvStatus.setTextColor(
                    requireContext().getColor(
                        if (status.isPending) CommonR.color.color_primary else CommonR.color.textNormal
                    )
                )

                val message = item.message.orEmpty().trim()
                itemBinding.tvMessage.text = message
                if (message.isEmpty()) itemBinding.tvMessage.gone() else itemBinding.tvMessage.visible()

                val canHandle = direction == FriendRequestDirection.INCOMING && status.isPending
                if (canHandle) {
                    itemBinding.llButtons.visible()
                    val enabled = processingRequestId != item.id
                    itemBinding.btnAccept.isEnabled = enabled
                    itemBinding.btnReject.isEnabled = enabled
                } else {
                    itemBinding.llButtons.gone()
                }
            }
            addChildClickViewIds(R.id.btn_accept, R.id.btn_reject)
            setOnItemClickListener { _, _, position ->
                val request = items.getOrNull(position) ?: return@setOnItemClickListener
                val counterpart =
                    if (direction == FriendRequestDirection.INCOMING) request.from else request.to
                openUserProfile(this@FriendRequestsFragment, counterpart.id)
            }
            setOnItemChildClickListener { _, view, position ->
                val request = items.getOrNull(position) ?: return@setOnItemChildClickListener
                when (view.id) {
                    R.id.btn_accept -> handleRequest(request, accept = true, clickedView = view)
                    R.id.btn_reject -> handleRequest(request, accept = false, clickedView = view)
                }
            }
        }
        binding.rvRequests.addItemDecoration(RvDecoration.Builder().setVertical(4).build())
        binding.rvRequests.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val count = recyclerView.adapter?.itemCount ?: 0
                if (count > 0 && lastVisible >= count - 2) {
                    loadMore()
                }
            }
        })
    }

    override fun initData(binding: RelationFragmentFriendRequestsBinding) {
    }

    override fun initListener(binding: RelationFragmentFriendRequestsBinding) {
        binding.tvTabIncoming.setOnClickListener {
            switchDirection(FriendRequestDirection.INCOMING)
        }
        binding.tvTabOutgoing.setOnClickListener {
            switchDirection(FriendRequestDirection.OUTGOING)
        }
        binding.btnLogin.setOnClickListener {
            if (!openLoginPage(this)) toast(R.string.relation_login_failed)
        }
        binding.btnRetry.setOnClickListener { loadFirst() }
    }

    override fun initObserver(binding: RelationFragmentFriendRequestsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    renderAuthState(state)
                }
            }
        }
    }

    private fun renderAuthState(state: AuthState) {
        if (view == null) return
        when (state) {
            AuthState.Unknown -> showLoading()
            AuthState.LoggedOut -> {
                loggedIn = false
                loaded = false
                items.clear()
                showLoginRequired()
            }

            is AuthState.LoggedIn -> {
                loggedIn = true
                if (!loaded && !loading) loadFirst()
            }
        }
    }

    private fun switchDirection(newDirection: FriendRequestDirection) {
        if (direction == newDirection) return
        direction = newDirection
        renderTabs()
        items.clear()
        loaded = false
        if (view != null) binding.rvRequests.submitList(emptyList())
        loadFirst()
    }

    private fun renderTabs() {
        if (view == null) return
        val incomingSelected = direction == FriendRequestDirection.INCOMING
        applyTabState(binding.tvTabIncoming, incomingSelected)
        applyTabState(binding.tvTabOutgoing, !incomingSelected)
    }

    private fun applyTabState(tab: android.widget.TextView, selected: Boolean) {
        tab.setTextColor(
            requireContext().getColor(
                if (selected) CommonR.color.color_primary else CommonR.color.textNormal
            )
        )
        tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun loadFirst() {
        if (!loggedIn || loading) return
        page = 1
        hasMore = true
        loaded = false
        loadPage(reset = true)
    }

    private fun loadMore() {
        if (!loggedIn || loading || !loaded || !hasMore) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        if (!loggedIn || loading) return
        loading = true
        if (reset) showLoading() else binding.llError.gone()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.listRequests(
                    direction = direction,
                    status = FriendRepository.STATUS_ALL,
                    page = page
                )
                if (reset) items.clear()
                items.addAll(result.items)
                hasMore = result.hasMore
                page = result.page + 1
                loaded = true
                binding.rvRequests.submitList(items.toList())
                renderContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else if (reset) {
                    showError(e.message)
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                }
            } catch (e: Exception) {
                if (reset) showError(getString(R.string.relation_error_default))
            } finally {
                loading = false
            }
        }
    }

    private fun handleRequest(request: FriendRequestDto, accept: Boolean, clickedView: android.view.View) {
        if (processingRequestId != null) return
        val status = FriendRequestStatus.fromRaw(request.status)
        if (!status.isPending) {
            toast(R.string.relation_request_not_pending)
            return
        }
        processingRequestId = request.id
        disableRowButtons(clickedView)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (accept) {
                    repository.acceptRequest(request.id)
                } else {
                    repository.rejectRequest(request.id)
                }
                processingRequestId = null
                loadFirst()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                processingRequestId = null
                if (e.isUnauthorized) {
                    SessionManager.logout()
                    showLoginRequired()
                } else {
                    toast(e.message ?: getString(R.string.relation_error_default))
                    // 状态可能已被其他端处理，刷新以恢复真实状态。
                    loadFirst()
                }
            } catch (e: Exception) {
                processingRequestId = null
                toast(R.string.relation_error_default)
            }
        }
    }

    private fun disableRowButtons(clickedView: android.view.View) {
        val container = clickedView.parent as? LinearLayout ?: return
        for (index in 0 until container.childCount) {
            container.getChildAt(index).isEnabled = false
        }
    }

    private fun statusText(status: FriendRequestStatus): Int = when (status) {
        FriendRequestStatus.PENDING -> R.string.relation_status_pending
        FriendRequestStatus.ACCEPTED -> R.string.relation_status_accepted
        FriendRequestStatus.REJECTED -> R.string.relation_status_rejected
        FriendRequestStatus.UNKNOWN -> R.string.relation_status_unknown
    }

    private fun renderContent() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        if (items.isEmpty()) {
            binding.rvRequests.gone()
            binding.tvEmpty.text = getString(
                if (direction == FriendRequestDirection.INCOMING) {
                    R.string.relation_request_empty_incoming
                } else {
                    R.string.relation_request_empty_outgoing
                }
            )
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
            binding.rvRequests.visible()
        }
    }

    private fun showLoading() {
        if (view == null) return
        binding.pbLoading.visible()
        binding.llLoginRequired.gone()
        binding.llError.gone()
        binding.tvEmpty.gone()
        binding.rvRequests.gone()
    }

    private fun showLoginRequired() {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvRequests.gone()
        binding.tvEmpty.gone()
        binding.llError.gone()
        binding.llLoginRequired.visible()
    }

    private fun showError(message: String?) {
        if (view == null) return
        binding.pbLoading.gone()
        binding.rvRequests.gone()
        binding.tvEmpty.gone()
        binding.llLoginRequired.gone()
        binding.tvError.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.relation_error_default)
        binding.llError.visible()
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
