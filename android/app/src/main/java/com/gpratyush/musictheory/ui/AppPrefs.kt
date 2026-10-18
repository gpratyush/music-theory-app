package com.gpratyush.musictheory.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gpratyush.musictheory.theory.IntervalNotation

/** App-wide settings, saved on the phone. Same keys and defaults as the iOS app. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var autoNextState by mutableStateOf(sp.getBoolean("autoNext", false))
    private var timedSecondsState by mutableStateOf(sp.getInt("timedSeconds", 60))
    private var notationState by mutableStateOf(
        IntervalNotation.entries.firstOrNull { it.name == sp.getString("intervalNotation", null) }
            ?: IntervalNotation.WESTERN,
    )

    /** Go to the next question by itself after answering. */
    var autoNext: Boolean
        get() = autoNextState
        set(value) {
            autoNextState = value
            sp.edit().putBoolean("autoNext", value).apply()
        }

    var timedSeconds: Int
        get() = timedSecondsState
        set(value) {
            timedSecondsState = value
            sp.edit().putInt("timedSeconds", value).apply()
        }

    var notation: IntervalNotation
        get() = notationState
        set(value) {
            notationState = value
            sp.edit().putString("intervalNotation", value.name).apply()
        }
}
