@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpratyush.musictheory.theory.Interval
import com.gpratyush.musictheory.theory.IntervalNotation
import androidx.compose.foundation.selection.selectable

enum class Game(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val color: Color,
    val isAvailable: Boolean,
) {
    FLASHCARDS("Interval Flashcards", "Find notes and name intervals", "🃏", Color(0xFF007AFF), true),
    EAR("Ear Training", "Hear two notes, name them", "👂", Color(0xFFAF52DE), false),
    HARMONY("Harmony", "Chords, keys and modes", "🎹", Color(0xFFFF9500), false),
    CHORD_EAR("Chord Ear Training", "Hear a chord, name its type", "🎧", Color(0xFFFF2D55), false),
    PROGRESSIONS("Progressions", "Type chords, hear them played", "🎼", Color(0xFF30B0C7), false),
}

@Composable
fun HomeScreen(prefs: AppPrefs, onOpen: (Game) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Learn") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            items(Game.entries) { game -> GameTile(game) { if (game.isAvailable) onOpen(game) } }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            AppSettings(prefs)
        }
    }
}

@Composable
private fun GameTile(game: Game, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = game.isAvailable,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .alpha(if (game.isAvailable) 1f else 0.55f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.verticalGradient(listOf(game.color, game.color.copy(alpha = 0.75f))),
                        RoundedCornerShape(12.dp),
                    ),
            ) { Text(game.emoji, fontSize = 24.sp) }
            Spacer(Modifier.height(16.dp))
            Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (game.isAvailable) game.subtitle else "Coming soon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppSettings(prefs: AppPrefs) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Interval names", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        IntervalNotation.entries.forEach { n ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = prefs.notation == n, role = Role.RadioButton) { prefs.notation = n },
            ) {
                RadioButton(selected = prefs.notation == n, onClick = null)
                Text(n.displayName, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "Example: ${prefs.notation.label(Interval.P5)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
