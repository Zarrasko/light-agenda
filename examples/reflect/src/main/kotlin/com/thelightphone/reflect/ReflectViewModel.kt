package com.thelightphone.reflect

import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed class ReflectScreenMode {
    data object Card : ReflectScreenMode()
    data object Browse : ReflectScreenMode()
}

data class ReflectUiState(
    val mode: ReflectScreenMode = ReflectScreenMode.Card,
    val currentPrompt: ReflectPrompt,
    val isDailyPrompt: Boolean = true,
)

class ReflectViewModel : LightViewModel<Unit>() {
    private val dailyPrompt = todaysPrompt()

    private val _uiState = MutableStateFlow(
        ReflectUiState(currentPrompt = dailyPrompt, isDailyPrompt = true),
    )
    val uiState: StateFlow<ReflectUiState> = _uiState.asStateFlow()

    fun shufflePrompt() {
        _uiState.update { state ->
            val candidates = PromptList.prompts.filter { it.id != state.currentPrompt.id }
            val next = candidates.randomOrNull() ?: state.currentPrompt
            state.copy(currentPrompt = next, isDailyPrompt = next.id == dailyPrompt.id)
        }
    }

    fun openPromptList() {
        _uiState.update { it.copy(mode = ReflectScreenMode.Browse) }
    }

    fun selectPrompt(prompt: ReflectPrompt) {
        _uiState.update {
            it.copy(
                mode = ReflectScreenMode.Card,
                currentPrompt = prompt,
                isDailyPrompt = prompt.id == dailyPrompt.id,
            )
        }
    }

    fun closePromptList() {
        _uiState.update { it.copy(mode = ReflectScreenMode.Card) }
    }

    override fun onBackPressed(): Boolean {
        if (_uiState.value.mode == ReflectScreenMode.Browse) {
            closePromptList()
            return true
        }
        return false
    }

    private fun todaysPrompt(): ReflectPrompt {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return PromptList.promptForDate(today)
    }
}
