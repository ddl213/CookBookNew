package com.rhys.cookbook.viewmodel

import com.android.common.base.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class CookBookChildVM: BaseViewModel(), ICBChildState {

    override val state = MutableStateFlow(ICBChildState.State())
    override val effect = MutableSharedFlow<ICBChildState.Effect>()
    override fun sentEvent(event: ICBChildState.Event) {

    }

}