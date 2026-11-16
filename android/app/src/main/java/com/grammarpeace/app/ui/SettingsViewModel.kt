package com.grammarpeace.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grammarpeace.app.GrammarPeaceApp
import com.grammarpeace.app.core.AiException
import com.grammarpeace.app.core.AiSettings
import com.grammarpeace.app.core.WritingEngine
import com.grammarpeace.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestStatus {
    data object None : TestStatus
    data object Running : TestStatus
    data object Passed : TestStatus
    data class Failed(val message: String) : TestStatus
}

data class SettingsUiState(
    /** Null until the stored settings have loaded. */
    val draft: AiSettings? = null,
    val test: TestStatus = TestStatus.None,
)

/** Settings are saved as they are edited, there is no Save button to forget. */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val engine: WritingEngine,
    /** Outlives the screen so the last edit is still written when the user leaves right after typing. */
    private val saveScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            val stored = repository.current()
            _state.update { if (it.draft == null) it.copy(draft = stored) else it }
        }
    }

    fun edit(change: (AiSettings) -> AiSettings) {
        val current = _state.value.draft ?: return
        val updated = change(current)
        if (updated == current) return
        testJob?.cancel()
        _state.update { it.copy(draft = updated, test = TestStatus.None) }
        saveScope.launch { repository.save(updated) }
    }

    fun test() {
        val draft = _state.value.draft ?: return
        testJob?.cancel()
        _state.update { it.copy(test = TestStatus.Running) }
        testJob = viewModelScope.launch {
            val status = try {
                engine.test(draft)
                TestStatus.Passed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TestStatus.Failed((e as? AiException)?.message ?: "Something went wrong. Please try again.")
            }
            _state.update { it.copy(test = status) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GrammarPeaceApp
                SettingsViewModel(app.container.settings, app.container.engine, app.container.appScope)
            }
        }
    }
}
