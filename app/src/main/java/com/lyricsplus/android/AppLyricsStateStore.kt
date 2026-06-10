package com.lyricsplus.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLyricsStateStore {
    private val _state = MutableStateFlow<LyricsUiState?>(null)
    val state: StateFlow<LyricsUiState?> = _state.asStateFlow()

    val latest: LyricsUiState?
        get() = _state.value

    fun update(state: LyricsUiState) {
        _state.value = state
    }
}
