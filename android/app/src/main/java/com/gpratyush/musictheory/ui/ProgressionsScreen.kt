@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpratyush.musictheory.audio.PianoSynth
import com.gpratyush.musictheory.theory.Chord
import com.gpratyush.musictheory.theory.Letter
import com.gpratyush.musictheory.theory.NoteName
import com.gpratyush.musictheory.theory.ProgressionParseError
import com.gpratyush.musictheory.theory.ProgressionParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

private val presets = listOf(
    "ii – V – I in C" to "Dm7 - G7 - Cmaj7",
    "Pop (I–V–vi–IV)" to "C - G - Am - F",
    "Jazz turnaround" to "Cmaj7 - Am7 - Dm7 - G7",
    "Minor ii – V – i" to "Dm7b5 - G7 - Cm7",
    "Secondary dominants" to "C - A7 - Dm - D7 - G7 - C",
    "Your example" to "Cm7 - Am7 - D7 - Gmaj7",
)

private val keys = listOf(
    NoteName(Letter.C), NoteName(Letter.D, -1), NoteName(Letter.D), NoteName(Letter.E, -1), NoteName(Letter.E),
    NoteName(Letter.F), NoteName(Letter.F, 1), NoteName(Letter.G), NoteName(Letter.A, -1), NoteName(Letter.A),
    NoteName(Letter.B, -1), NoteName(Letter.B),
)

/** Saved settings for this screen, same keys as the iOS app. */
private class ProgressionPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var text: String
        get() = sp.getString("progression.text", null) ?: "Cm7 - Am7 - D7 - Gmaj7"
        set(v) = sp.edit().putString("progression.text", v).apply()
    var bpm: Int
        get() = sp.getInt("progression.bpm", 90)
        set(v) = sp.edit().putInt("progression.bpm", v).apply()
    var beats: Int
        get() = sp.getInt("progression.beats", 4)
        set(v) = sp.edit().putInt("progression.beats", v).apply()
    var loop: Boolean
        get() = sp.getBoolean("progression.loop", true)
        set(v) = sp.edit().putBoolean("progression.loop", v).apply()
    var keyIndex: Int
        get() = sp.getInt("progression.key", -1)
        set(v) = sp.edit().putInt("progression.key", v).apply()
    var saved: List<String>
        get() = runCatching {
            val arr = JSONArray(sp.getString("progression.saved", "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        set(v) = sp.edit().putString("progression.saved", JSONArray(v).toString()).apply()
}

@Composable
fun ProgressionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProgressionPrefs(context) }
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(store.text) }
    var bpm by remember { mutableIntStateOf(store.bpm) }
    var beats by remember { mutableIntStateOf(store.beats) }
    var loop by remember { mutableStateOf(store.loop) }
    var keyIndex by remember { mutableIntStateOf(store.keyIndex) }
    var saved by remember { mutableStateOf(store.saved) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var currentIndex by remember { mutableStateOf<Int?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var showExamples by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }

    val parsed: Result<List<Chord>> = remember(text) { runCatching { ProgressionParser.parse(text) } }
    val chords = parsed.getOrDefault(emptyList())
    val errorMessage = parsed.exceptionOrNull()?.let { (it as? ProgressionParseError)?.message ?: "Couldn't read that" }
    val isPlaying = playJob?.isActive == true

    fun stop() {
        playJob?.cancel()
        playJob = null
        PianoSynth.releaseAll()
        currentIndex = null
    }

    fun play() {
        stop()
        if (chords.isEmpty()) return
        val list = chords
        val chordMs = (60_000.0 / bpm * beats).toLong()
        val repeat = loop
        playJob = scope.launch {
            do {
                for ((i, chord) in list.withIndex()) {
                    currentIndex = i
                    PianoSynth.releaseAll()
                    PianoSynth.noteOn(chord.voicing)
                    delay(chordMs)
                }
            } while (repeat)
            PianoSynth.releaseAll()
            currentIndex = null
            playJob = null
        }
    }

    DisposableEffect(Unit) { onDispose { stop() } }

    val shownChord = (currentIndex ?: selected)?.let { chords.getOrNull(it) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Progressions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Editor
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chords", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { showExamples = true }) { Text("Examples") }
                    DropdownMenu(expanded = showExamples, onDismissRequest = { showExamples = false }) {
                        presets.forEach { (name, value) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                text = value; store.text = value; stop(); showExamples = false
                            })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; store.text = it; stop() },
                placeholder = { Text("e.g. Cm7 - Am7 - D7 - Gmaj7") },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                isError = parsed.isFailure,
                supportingText = errorMessage?.let { msg -> @Composable { Text(msg) } },
                modifier = Modifier.fillMaxWidth(),
            )

            // Chord chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                chords.forEachIndexed { i, chord ->
                    val active = (currentIndex ?: selected) == i
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                selected = i
                                PianoSynth.releaseAll()
                                PianoSynth.noteOn(chord.voicing)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                        Text(chord.toString(), fontWeight = FontWeight.SemiBold, color = fg)
                        if (keyIndex >= 0) {
                            Text(
                                chord.romanNumeral(keys[keyIndex]),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            PianoKeyboard(highlighted = shownChord?.voicing?.toSet() ?: emptySet())

            // Transport
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (isPlaying) stop() else play() },
                    enabled = chords.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPlaying) Text("■  Stop") else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
                FilledTonalIconButton(
                    onClick = {
                        if (text !in saved) {
                            saved = listOf(text) + saved
                            store.saved = saved
                        }
                    },
                    enabled = chords.isNotEmpty(),
                ) { Icon(Icons.Filled.Star, contentDescription = "Save progression") }
            }

            // Options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo: $bpm BPM", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { bpm = (bpm - 5).coerceAtLeast(40); store.bpm = bpm; stop() }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { bpm = (bpm + 5).coerceAtMost(200); store.bpm = bpm; stop() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Faster")
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(1, 2, 4).forEachIndexed { i, b ->
                    SegmentedButton(
                        selected = beats == b,
                        onClick = { beats = b; store.beats = b; stop() },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(if (b == 1) "1 beat" else "$b beats") }
                }
            }
            SwitchRow("Loop", loop) { loop = it; store.loop = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show Roman numerals in", modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { showKeys = true }) {
                        Text(if (keyIndex < 0) "Off" else "${keys[keyIndex]} major")
                    }
                    DropdownMenu(expanded = showKeys, onDismissRequest = { showKeys = false }) {
                        DropdownMenuItem(text = { Text("Off") }, onClick = { keyIndex = -1; store.keyIndex = -1; showKeys = false })
                        keys.forEachIndexed { i, k ->
                            DropdownMenuItem(text = { Text("$k major") }, onClick = {
                                keyIndex = i; store.keyIndex = i; showKeys = false
                            })
                        }
                    }
                }
            }

            // Saved
            if (saved.isNotEmpty()) {
                Text("Saved", style = MaterialTheme.typography.titleMedium)
                saved.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { text = item; store.text = item; stop() }, modifier = Modifier.weight(1f)) {
                            Text(
                                item,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(onClick = { saved = saved - item; store.saved = saved }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete $item", tint = WrongRed)
                        }
                    }
                }
            }
        }
    }
}
