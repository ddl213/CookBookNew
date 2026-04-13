package com.campaign.common.listener


interface IMVIState<S,E,F> {
    fun sentEvent(event: E)

    interface State
    interface Event

    interface Effect
}