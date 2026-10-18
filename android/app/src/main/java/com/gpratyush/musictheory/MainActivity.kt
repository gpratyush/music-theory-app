package com.gpratyush.musictheory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gpratyush.musictheory.audio.PianoSynth
import com.gpratyush.musictheory.ui.AppPrefs
import com.gpratyush.musictheory.ui.ChordEarScreen
import com.gpratyush.musictheory.ui.EarTrainingScreen
import com.gpratyush.musictheory.ui.FlashcardsScreen
import com.gpratyush.musictheory.ui.HarmonyScreen
import com.gpratyush.musictheory.ui.ProgressionsScreen
import com.gpratyush.musictheory.ui.Game
import com.gpratyush.musictheory.ui.HomeScreen
import com.gpratyush.musictheory.ui.MusicTheoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPrefs(this)
        PianoSynth.prepare(this)
        setContent {
            MusicTheoryTheme {
                var game by remember { mutableStateOf<Game?>(null) }
                BackHandler(enabled = game != null) { game = null }
                val back = { game = null }
                when (game) {
                    null -> HomeScreen(prefs = prefs, onOpen = { game = it })
                    Game.FLASHCARDS -> FlashcardsScreen(prefs, back)
                    Game.EAR -> EarTrainingScreen(prefs, back)
                    Game.HARMONY -> HarmonyScreen(prefs, back)
                    Game.CHORD_EAR -> ChordEarScreen(prefs, back)
                    Game.PROGRESSIONS -> ProgressionsScreen(back)
                }
            }
        }
    }
}
