package com.jaivish.gowork.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide live status published by the accessibility service. */
object LiveState {
    private val state = MutableStateFlow(LiveStatus())

    val status: StateFlow<LiveStatus> = state.asStateFlow()

    fun update(status: LiveStatus) {
        state.value = status
    }
}
