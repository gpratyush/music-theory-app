@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpratyush.musictheory.audio.PianoSynth
import com.gpratyush.musictheory.theory.Chord
import com.gpratyush.musictheory.theory.ChordQuality
import com.gpratyush.musictheory.theory.HarmonyQuestion
import com.gpratyush.musictheory.theory.HarmonyQuestionKind
import com.gpratyush.musictheory.theory.HarmonyQuizGenerator
import com.gpratyush.musictheory.theory.HarmonySettings
import com.gpratyush.musictheory.theory.NoQuestionsPossible
import kotlinx.coroutines.delay

private val chordGroups: List<Pair<String, List<ChordQuality>>> = listOf(
    "Triads" to listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED),
    "7th chords" to listOf(
        ChordQuality.DOMINANT7, ChordQuality.MAJOR7, ChordQuality.MINOR7,
        ChordQuality.HALF_DIMINISHED7, ChordQuality.DIMINISHED7, ChordQuality.MINOR_MAJOR7,
    ),
    "Suspended" to listOf(ChordQuality.SUS2, ChordQuality.SUS4, ChordQuality.DOMINANT7_SUS4),
    "Add chords" to listOf(ChordQuality.ADD2, ChordQuality.ADD4, ChordQuality.MAJOR6, ChordQuality.MINOR6),
)

@Composable
fun HarmonyScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val view = LocalView.current
    val round = rememberQuizRound(prefs)
    var settings by remember { mutableStateOf(HarmonySettings()) }
    var question by remember { mutableStateOf<HarmonyQuestion?>(null) }
    var selection by remember { mutableStateOf(setOf<Int>()) }
    var revealed by remember { mutableStateOf(false) }
    var questionId by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun play(chord: Chord) {
        PianoSynth.releaseAll()
        PianoSynth.noteOn(chord.voicing)
    }

    fun nextQuestion() {
        selection = emptySet()
        revealed = false
        questionId += 1
        try {
            question = HarmonyQuizGenerator(settings).next()
            errorText = null
        } catch (e: NoQuestionsPossible) {
            question = null
            errorText = "No questions match these settings. Try turning more options on."
        }
    }

    fun restart() {
        round.reset()
        nextQuestion()
    }

    fun reveal(q: HarmonyQuestion) {
        revealed = true
        val right = q.isCorrect(selection)
        round.record(right)
        answerHaptic(view, right)
        play(q.chord)
    }

    fun tap(i: Int, q: HarmonyQuestion) {
        if (revealed || round.isOver) return
        if (q.isMultiSelect) {
            selection = if (i in selection) selection - i else selection + i
        } else {
            selection = setOf(i)
            reveal(q)
        }
    }

    LaunchedEffect(round.timed) { restart() }
    QuizClock(round)
    DisposableEffect(Unit) { onDispose { PianoSynth.releaseAll() } }
    LaunchedEffect(questionId, revealed) {
        val q = question ?: return@LaunchedEffect
        if (!revealed || !round.advancesAutomatically) return@LaunchedEffect
        // Wrong answers stay up longer so the explanation can be read.
        delay(if (q.isCorrect(selection)) 1000 else 3000)
        nextQuestion()
    }

    Scaffold(topBar = { GameTopBar("Harmony", onBack, { showSettings = true }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        q.prompt,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                            .semantics { heading() },
                    )
                    // Hidden until answered for "name the chord", so it doesn't give the name away.
                    val hideHear = q.kind == HarmonyQuestionKind.NAME_CHORD && !revealed
                    OutlinedButton(
                        onClick = { play(q.chord) },
                        enabled = !hideHear,
                        modifier = Modifier.alpha(if (hideHear) 0f else 1f),
                    ) { Text("🔊 Hear ${q.chord}") }

                    AnswerGrid(q.options.indices.toList()) { i, cell ->
                        val state = when {
                            !revealed -> if (i in selection) AnswerState.SELECTED else AnswerState.NORMAL
                            i in q.correct -> AnswerState.CORRECT
                            i in selection -> AnswerState.WRONG
                            else -> AnswerState.NORMAL
                        }
                        AnswerButton(
                            title = q.options[i],
                            state = state,
                            enabled = !revealed,
                            minHeight = 56.dp,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier = cell,
                        ) { tap(i, q) }
                    }

                    if (q.isMultiSelect && !revealed) {
                        Button(onClick = { reveal(q) }, enabled = selection.isNotEmpty(), modifier = Modifier.width(180.dp)) {
                            Text("Check")
                        }
                    }
                    if (revealed) {
                        val right = q.isCorrect(selection)
                        FeedbackBanner(right, if (right) "Correct" else "Not quite")
                        Text(
                            q.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!round.advancesAutomatically) NextButton { nextQuestion() }
                    }
                }
                else -> errorText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.heightIn(min = 8.dp))
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
                Text("Harmony settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SectionTitle("Question types")
                HarmonyQuestionKind.entries.forEach { kind ->
                    SwitchRow(kind.displayName, kind in settings.kinds) { on ->
                        settings = settings.copy(kinds = if (on) settings.kinds + kind else settings.kinds - kind)
                    }
                }
                SectionTitle("Chords")
                chordGroups.forEach { (name, qualities) ->
                    SwitchRow(name, settings.qualities.containsAll(qualities)) { on ->
                        val rest = settings.qualities.filter { it !in qualities }
                        settings = settings.copy(qualities = if (on) rest + qualities else rest)
                    }
                }
                SwitchRow("Roots on white keys only", settings.naturalRootsOnly) {
                    settings = settings.copy(naturalRootsOnly = it)
                }
                Text(
                    "Secondary-dominant questions always use dominant 7th chords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlaySettings(prefs)
            }
        }
    }
}
