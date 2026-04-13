package com.rhys.cookbook.viewmodel

import com.campaign.common.listener.IMVIState
import com.example.network.bean.Recipe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

interface ICBChildState : IMVIState<ICBChildState.State, ICBChildState.Event, ICBChildState.Effect> {
    abstract val state : MutableStateFlow<State>
    abstract val effect : MutableSharedFlow<Effect>
    data class State(
        val recipesList : List<Recipe> = emptyList(),
    )

    sealed class Event {
    }

    sealed class Effect {
    }
}