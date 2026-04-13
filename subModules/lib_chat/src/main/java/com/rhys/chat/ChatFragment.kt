package com.rhys.chat


import com.android.common.base.BaseBindFragment
import com.android.common.utils.LogUtils
import com.android.common.view.TitleBar
import com.campaign.common.constants.RoutePath.PAGE_CHAT
import com.marky.route.annotation.Route
import com.rhys.chat.databinding.ChatFragmentChatBinding

@Route(PAGE_CHAT)
class ChatFragment : BaseBindFragment<ChatFragmentChatBinding>(ChatFragmentChatBinding::inflate) {


    override fun initTitleBar(): TitleBar {
        return binding.titleBar
    }

    override fun initView(binding: ChatFragmentChatBinding) {
        LogUtils.d("ChatFragment 初始化成功")
    }

    override fun initData(binding: ChatFragmentChatBinding) {

    }
}