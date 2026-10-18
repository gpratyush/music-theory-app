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
import com.gpratyush.musictheory.ui.AppPrefs
import com.gpratyush.musictheory.ui.FlashcardsScreen
import com.gpratyush.musictheory.ui.Game
import com.gpratyush.musictheory.ui.HomeScreen
import com.gpratyush.musictheory.ui.MusicTheoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPrefs(this)
        setContent {
            MusicTheoryTheme {
                var game by remember { mutableStateOf<Game?>(null) }
                BackHandler(enabled = game != null) { game = null }
                when (game) {
                    null -> HomeScreen(prefs = prefs, onOpen = { game = it })
                    Game.FLASHCARDS -> FlashcardsScreen(prefs = prefs, onBack = { game = null })
                    else -> HomeScreen(prefs = prefs, onOpen = { game = it })
                }
            }
        }
    }
}
