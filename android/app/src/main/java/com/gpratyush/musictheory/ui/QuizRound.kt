package com.gpratyush.musictheory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gpratyush.musictheory.theory.QuizMode
import com.gpratyush.musictheory.theory.QuizSession
import kotlinx.coroutines.delay

/** Mode, score and clock for one game screen. */
@Stable
class QuizRound(private val prefs: AppPrefs) {
    var timed by mutableStateOf(false)
    var session by mutableStateOf(QuizSession(QuizMode.Practice))
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())

    val isOver: Boolean get() = session.isOver(now)

    /** Timed rounds always move on by themselves. */
    val advancesAutomatically: Boolean get() = timed || prefs.autoNext

    fun reset() {
        session = QuizSession(if (timed) QuizMode.Timed(prefs.timedSeconds) else QuizMode.Practice)
        now = System.currentTimeMillis()
    }

    fun record(correct: Boolean) {
        session = session.record(correct)
    }
}

@Composable
fun rememberQuizRound(prefs: AppPrefs): QuizRound = remember { QuizRound(prefs) }

/** Ticks the countdown while a timed round runs; calls [onTimeUp] when it ends. */
@Composable
fun QuizClock(round: QuizRound, onTimeUp: () -> Unit = {}) {
    LaunchedEffect(round.session.startedAt, round.timed) {
        if (!round.timed) return@LaunchedEffect
        while (!round.session.isOver(System.currentTimeMillis())) {
            round.now = System.currentTimeMillis()
            delay(250)
        }
        round.now = System.currentTimeMillis()
        onTimeUp()
    }
}

/** The mode switch and score for a round. */
@Composable
fun RoundHeader(round: QuizRound, prefs: AppPrefs) {
    GameModeHeader(round.timed, { round.timed = it }, prefs.timedSeconds, round.session, round.now)
}
