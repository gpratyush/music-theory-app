@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpratyush.musictheory.audio.PianoSynth
import com.gpratyush.musictheory.theory.Chord
import com.gpratyush.musictheory.theory.ChordEarGenerator
import com.gpratyush.musictheory.theory.ChordEarQuestion
import com.gpratyush.musictheory.theory.ChordEarSettings
import com.gpratyush.musictheory.theory.ChordQuality
import com.gpratyush.musictheory.theory.HarmonySettings
import com.gpratyush.musictheory.theory.NoQuestionsPossible
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ChordPlayStyle(val displayName: String) {
    BLOCK("Together"),
    BROKEN("One note at a time"),
    BOTH("Broken, then together"),
}

@Composable
fun ChordEarScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val round = rememberQuizRound(prefs)
    var settings by remember { mutableStateOf(ChordEarSettings()) }
    var playStyle by remember { mutableStateOf(loadPlayStyle(context)) }
    var question by remember { mutableStateOf<ChordEarQuestion?>(null) }
    var picked by remember { mutableStateOf<ChordQuality?>(null) }
    var questionId by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<Job?>(null) }

    fun stopSound() {
        playback?.cancel()
        PianoSynth.releaseAll()
    }

    fun play(chord: Chord) {
        stopSound()
        val notes = chord.voicing
        val style = playStyle
        playback = scope.launch {
            if (style != ChordPlayStyle.BLOCK) {
                for (note in notes) {
                    PianoSynth.noteOn(listOf(note), velocity = 0.7)
                    delay(350)
                }
                delay(600)
                PianoSynth.releaseAll()
            }
            if (style != ChordPlayStyle.BROKEN) {
                delay(100)
                PianoSynth.noteOn(notes)
                delay(2000)
                PianoSynth.releaseAll()
            }
        }
    }

    fun nextQuestion() {
        picked = null
        questionId += 1
        try {
            val q = ChordEarGenerator(settings).next()
            question = q
            errorText = null
            play(q.chord)
        } catch (e: NoQuestionsPossible) {
            question = null
            errorText = "Pick at least one chord type in settings."
        }
    }

    fun restart() {
        round.reset()
        nextQuestion()
    }

    LaunchedEffect(round.timed) { restart() }
    QuizClock(round, onTimeUp = { stopSound() })
    DisposableEffect(Unit) { onDispose { stopSound() } }
    LaunchedEffect(questionId, picked) {
        val q = question ?: return@LaunchedEffect
        val p = picked ?: return@LaunchedEffect
        if (!round.advancesAutomatically) return@LaunchedEffect
        delay(if (q.isCorrect(p)) 900 else 2000)
        nextQuestion()
    }

    Scaffold(topBar = { GameTopBar("Chord Ear Training", onBack, { showSettings = true }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RoundHeader(round, prefs)
            val q = question
            when {
                round.isOver -> RoundResults(round.session) { restart() }
                q != null -> {
                    Text(
                        "What kind of chord is this?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    )
                    Button(onClick = { play(q.chord) }, modifier = Modifier.width(180.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play again")
                    }
                    AnswerGrid(q.options) { option, cell ->
                        AnswerButton(
                            title = option.displayName,
                            state = answerState(q.isCorrect(option), picked == option, picked != null),
                            enabled = picked == null,
                            modifier = cell,
                        ) {
                            if (picked == null && !round.isOver) {
                                picked = option
                                val right = q.isCorrect(option)
                                round.record(right)
                                answerHaptic(view, right)
                            }
                        }
                    }
                    picked?.let { p ->
                        val right = q.isCorrect(p)
                        FeedbackBanner(right, if (right) "Correct" else "It was ${q.chord.quality.displayName}")
                        Text(
                            "${q.chord} = ${q.chord.spelledTones.joinToString(" ")}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!round.advancesAutomatically) NextButton { nextQuestion() }
                    }
                }
                else -> errorText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false; restart() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("Chord ear settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SectionTitle("Chord types to practise")
                HarmonySettings.v1Qualities.forEach { quality ->
                    SwitchRow(quality.displayName, quality in settings.qualities) { on ->
                        val rest = settings.qualities - quality
                        settings = settings.copy(qualities = if (on) rest + quality else rest)
                    }
                }
                Text(
                    "Start with major and minor, then add more as they get easy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionTitle("Play chords")
                ChordPlayStyle.entries.forEach { style ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = playStyle == style, role = Role.RadioButton) {
                                playStyle = style
                                savePlayStyle(context, style)
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = playStyle == style, onClick = null)
                        Text(style.displayName, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                PlaySettings(prefs)
            }
        }
    }
}

private fun loadPlayStyle(context: Context): ChordPlayStyle {
    val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("chordEar.playStyle", null)
    return ChordPlayStyle.entries.firstOrNull { it.name == name } ?: ChordPlayStyle.BOTH
}

private fun savePlayStyle(context: Context, style: ChordPlayStyle) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("chordEar.playStyle", style.name).apply()
}
