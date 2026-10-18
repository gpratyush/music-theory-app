package com.gpratyush.musictheory.theory

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Practice = untimed. Timed = as many right as possible before the clock runs out. */
sealed interface QuizMode {
    data object Practice : QuizMode
    data class Timed(val seconds: Int) : QuizMode
}

/**
 * Score and clock for one round of any quiz game. Shared by every game,
 * so timed mode works the same everywhere. Times are epoch milliseconds.
 */
data class QuizSession(
    val mode: QuizMode,
    val startedAt: Long = System.currentTimeMillis(),
    val correct: Int = 0,
    val answered: Int = 0,
) {
    /** Seconds left, or null in practice mode. */
    fun remaining(now: Long = System.currentTimeMillis()): Int? {
        val timed = mode as? QuizMode.Timed ?: return null
        val left = timed.seconds - (now - startedAt) / 1000.0
        return max(0, ceil(left).toInt())
    }

    fun isOver(now: Long = System.currentTimeMillis()): Boolean = remaining(now) == 0

    /** Returns the session with this answer counted. Ignored once a timed round is over. */
    fun record(isCorrect: Boolean, now: Long = System.currentTimeMillis()): QuizSession {
        if (isOver(now)) return this
        return copy(answered = answered + 1, correct = correct + if (isCorrect) 1 else 0)
    }

    /** 0..100, or null before the first answer. */
    val accuracy: Int?
        get() = if (answered == 0) null else (correct.toDouble() / answered * 100).roundToInt()
}
