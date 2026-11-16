package com.grammarpeace.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grammarpeace.app.GrammarPeaceApp
import com.grammarpeace.app.core.AiException
import com.grammarpeace.app.core.Language
import com.grammarpeace.app.core.WritingAction
import com.grammarpeace.app.core.WritingEngine
import com.grammarpeace.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResultState {
    data object Idle : ResultState

    data class Loading(val action: WritingAction) : ResultState

    /**
     * @param original the text the action ran on, kept so the diff stays right if the input is edited afterwards
     * @param suggestion what the AI returned
     * @param text what Copy/Replace will use: [suggestion], or an alternative the user picked
     */
    data class Success(
        val action: WritingAction,
        val original: String,
        val suggestion: String,
        val text: String = suggestion,
    ) : ResultState {
        val unchanged: Boolean get() = original.trim() == text.trim()
        val isTranslation: Boolean get() = action is WritingAction.Translate
    }

    data class Failure(val action: WritingAction, val message: String, val needsSetup: Boolean) : ResultState
}

/** A follow-up the user asks for on demand (alternatives, explanation). */
sealed interface Extra<out T> {
    data object Hidden : Extra<Nothing>
    data object Loading : Extra<Nothing>
    data class Ready<T>(val value: T) : Extra<T>
    data class Failed(val message: String) : Extra<Nothing>
}

data class WritingUiState(
    val input: String = "",
    val result: ResultState = ResultState.Idle,
    val alternatives: Extra<List<String>> = Extra.Hidden,
    val explanation: Extra<String> = Extra.Hidden,
    val showChanges: Boolean = true,
    val translateTo: Language = Language.EN,
    val hasApiKey: Boolean = true,
)

class WritingViewModel(
    private val engine: WritingEngine,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(WritingUiState(input = savedState[KEY_INPUT] ?: ""))
    val state: StateFlow<WritingUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var alternativesJob: Job? = null
    private var explanationJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { current ->
                _state.update { it.copy(translateTo = current.translateTo, hasApiKey = current.hasApiKey) }
            }
        }
    }

    fun setInput(text: String) {
        // Very large values don't fit in saved state, and aren't worth restoring.
        savedState[KEY_INPUT] = text.takeIf { it.length <= MAX_SAVED_INPUT }
        _state.update { it.copy(input = text) }
    }

    /**
     * Sets the text and runs [action] on it, once. Used when the app is opened with text from
     * another app, so a rotation or process restore doesn't send the request again.
     */
    fun startWith(text: String, action: WritingAction = WritingAction.Fix) {
        if (savedState.get<Boolean>(KEY_STARTED) == true) return
        savedState[KEY_STARTED] = true
        setInput(text)
        run(action)
    }

    fun clear() {
        cancelAll()
        setInput("")
        _state.update { it.copy(result = ResultState.Idle, alternatives = Extra.Hidden, explanation = Extra.Hidden) }
    }

    fun run(action: WritingAction) {
        val text = _state.value.input
        if (text.isBlank()) return

        cancelAll()
        if (action is WritingAction.Translate) {
            viewModelScope.launch { settings.setTranslateTo(action.target) }
        }
        _state.update {
            it.copy(result = ResultState.Loading(action), alternatives = Extra.Hidden, explanation = Extra.Hidden)
        }
        runJob = viewModelScope.launch {
            val result = try {
                ResultState.Success(action, original = text, suggestion = engine.run(action, text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResultState.Failure(action, e.userMessage(), needsSetup = e.needsSetup())
            }
            _state.update { it.copy(result = result) }
        }
    }

    fun retry() {
        (_state.value.result as? ResultState.Failure)?.let { run(it.action) }
    }

    fun toggleChanges() = _state.update { it.copy(showChanges = !it.showChanges) }

    fun loadAlternatives() {
        val success = _state.value.result as? ResultState.Success ?: return
        if (_state.value.alternatives is Extra.Loading) return
        _state.update { it.copy(alternatives = Extra.Loading) }
        alternativesJob = viewModelScope.launch {
            val extra = try {
                val options = engine.alternatives(success.original, success.suggestion)
                if (options.isEmpty()) Extra.Failed("No alternatives came back. Try again.") else Extra.Ready(options)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Extra.Failed(e.userMessage())
            }
            _state.update { it.copy(alternatives = extra) }
        }
    }

    fun loadExplanation() {
        val success = _state.value.result as? ResultState.Success ?: return
        if (_state.value.explanation is Extra.Loading) return
        _state.update { it.copy(explanation = Extra.Loading) }
        explanationJob = viewModelScope.launch {
            val extra = try {
                Extra.Ready(engine.explain(success.original, success.suggestion))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Extra.Failed(e.userMessage())
            }
            _state.update { it.copy(explanation = extra) }
        }
    }

    /** Makes [text] (the suggestion or one of the alternatives) the version Copy and Replace use. */
    fun choose(text: String) {
        _state.update { state ->
            val success = state.result as? ResultState.Success ?: return@update state
            state.copy(result = success.copy(text = text))
        }
    }

    private fun cancelAll() {
        runJob?.cancel()
        alternativesJob?.cancel()
        explanationJob?.cancel()
    }

    private fun Exception.userMessage(): String =
        (this as? AiException)?.message ?: "Something went wrong. Please try again."

    private fun Exception.needsSetup(): Boolean =
        this is AiException && (reason == AiException.Reason.MISSING_KEY || reason == AiException.Reason.BAD_KEY)

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_STARTED = "started"
        private const val MAX_SAVED_INPUT = 20_000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GrammarPeaceApp).container
                WritingViewModel(container.engine, container.settings, createSavedStateHandle())
            }
        }
    }
}
