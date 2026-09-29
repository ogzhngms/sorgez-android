package com.ogzhngms.sorgez

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Profile : Screen
    data class Question(val step: Int) : Screen
    data object Confirm : Screen
    data object Loading : Screen
    data class Result(val itinerary: Itinerary, val json: String) : Screen
    data class Failed(@StringRes val message: Int, val detail: String?) : Screen
}

// planner turns the answers into the itinerary JSON: Gemini in the app, a stub in tests.
class TripViewModel(private val planner: suspend (TripAnswers) -> String) : ViewModel() {
    var answers by mutableStateOf(TripAnswers())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private var job: Job? = null

    // Set while one answer is being changed from the summary: Next and Back both return to it.
    var editing by mutableStateOf(false)
        private set

    fun update(change: (TripAnswers) -> TripAnswers) {
        answers = change(answers)
    }

    fun start() {
        screen = Screen.Question(0)
    }

    fun openProfile() {
        screen = Screen.Profile
    }

    fun next() {
        val step = (screen as? Screen.Question)?.step ?: return
        if (step == 0 && answers.destination.isBlank()) return
        screen = if (step < QUESTIONS.lastIndex && !editing) Screen.Question(step + 1) else Screen.Confirm
        editing = false
    }

    fun edit(step: Int) {
        editing = true
        screen = Screen.Question(step)
    }

    // Backing out of the first question to Home starts over, so Start always opens a blank trip.
    fun back() {
        screen = when (val current = screen) {
            Screen.Home, Screen.Profile -> Screen.Home
            is Screen.Question -> when {
                // The destination cannot be left blank on the way back.
                editing -> if (current.step == 0 && answers.destination.isBlank()) return else Screen.Confirm.also { editing = false }
                current.step == 0 -> return restart()
                else -> Screen.Question(current.step - 1)
            }
            Screen.Confirm -> Screen.Question(QUESTIONS.lastIndex)
            else -> {
                job?.cancel()
                Screen.Confirm
            }
        }
    }

    fun submit() {
        job?.cancel()
        screen = Screen.Loading
        job = viewModelScope.launch {
            screen = try {
                val json = planner(answers)
                Screen.Result(parseItinerary(json), json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Screen.Failed(errorMessage(e), errorDetail(e))
            }
        }
    }

    fun restart() {
        answers = TripAnswers()
        editing = false
        screen = Screen.Home
    }
}
