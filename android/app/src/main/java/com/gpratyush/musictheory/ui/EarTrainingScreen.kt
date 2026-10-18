@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpratyush.musictheory.audio.PianoSynth
import com.gpratyush.musictheory.theory.EarDirection
import com.gpratyush.musictheory.theory.EarQuestion
import com.gpratyush.musictheory.theory.EarQuestionStyle
import com.gpratyush.musictheory.theory.EarTrainingGenerator
import com.gpratyush.musictheory.theory.EarTrainingSettings
import com.gpratyush.musictheory.theory.Interval
import com.gpratyush.musictheory.theory.NoQuestionsPossible
import com.gpratyush.musictheory.theory.NoteName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EarStep { FIRST, SECOND, INTERVAL, DONE }

@Composable
fun EarTrainingScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val round = rememberQuizRound(prefs)
    var settings by remember { mutableStateOf(EarTrainingSettings()) }
    var question by remember { mutableStateOf<EarQuestion?>(null) }
    var step by remember { mutableStateOf(EarStep.FIRST) }
    var pickedFirst by remember { mutableStateOf<NoteName?>(null) }
    var pickedSecond by remember { mutableStateOf<NoteName?>(null) }
    var pickedInterval by remember { mutableStateOf<Interval?>(null) }
    var questionId by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<Job?>(null) }

    fun stopSound() {
        playback?.cancel()
        PianoSynth.releaseAll()
    }

    /** Reference note, a short gap, then the two notes. */
    fun play(q: EarQuestion) {
        stopSound()
        playback = scope.launch {
            PianoSynth.noteOn(listOf(q.reference.midi))
            delay(900)
            PianoSynth.releaseAll()
            delay(500)
            if (q.direction == EarDirection.HARMONIC) {
                PianoSynth.noteOn(listOf(q.first.midi, q.second.midi))
                delay(1600)
            } else {
                PianoSynth.noteOn(listOf(q.first.midi))
                delay(800)
                PianoSynth.releaseAll()
                PianoSynth.noteOn(listOf(q.second.midi))
                delay(1000)
            }
            PianoSynth.releaseAll()
        }
    }

    fun playReference(q: EarQuestion) {
        stopSound()
        PianoSynth.noteOn(listOf(q.reference.midi))
    }

    fun isAllCorrect(q: EarQuestion): Boolean = when (settings.style) {
        EarQuestionStyle.NAME_NOTES ->
            pickedFirst?.let(q::isCorrectFirst) == true && pickedSecond?.let(q::isCorrectSecond) == true
        EarQuestionStyle.NAME_INTERVAL -> pickedInterval?.let(q::isCorrectInterval) == true
    }

    fun nextQuestion() {
        pickedFirst = null
        pickedSecond = null
        pickedInterval = null
        step = if (settings.style == EarQuestionStyle.NAME_NOTES) EarStep.FIRST else EarStep.INTERVAL
        questionId += 1
        try {
            val q = EarTrainingGenerator(settings).next()
            question = q
            errorText = null
            play(q)
        } catch (e: NoQuestionsPossible) {
            question = null
            errorText = "No questions match these settings. Try turning more options on."
        }
    }

    fun restart() {
        round.reset()
        nextQuestion()
    }

    fun answer(right: Boolean) {
        round.record(right)
        answerHaptic(view, right)
    }

    LaunchedEffect(round.timed) { restart() }
    QuizClock(round, onTimeUp = { stopSound() })
    DisposableEffect(Unit) { onDispose { stopSound() } }

    LaunchedEffect(questionId, step) {
        val q = question ?: return@LaunchedEffect
        if (step != EarStep.DONE || !round.advancesAutomatically) return@LaunchedEffect
        delay(if (isAllCorrect(q)) 900 else 2000)
        nextQuestion()
    }

    Scaffold(topBar = { GameTopBar("Ear Training", onBack, { showSettings = true }) }) { padding ->
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
                        "This is ${q.reference.name}. Listen:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { play(q) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Replay")
                        }
                        OutlinedButton(onClick = { playReference(q) }) { Text("Reference ${q.reference.name}") }
                    }
                    val harmonic = q.direction == EarDirection.HARMONIC
                    Text(
                        when (step) {
                            EarStep.FIRST -> if (harmonic) "What was the lower note?" else "What was the first note?"
                            EarStep.SECOND -> if (harmonic) "And the upper note?" else "And the second note?"
                            EarStep.INTERVAL -> "What interval was that?"
                            EarStep.DONE -> if (isAllCorrect(q)) "Correct!" else "Not quite"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    )

                    if (settings.style == EarQuestionStyle.NAME_INTERVAL) {
                        AnswerGrid(q.intervalOptions) { iv, cell ->
                            AnswerButton(
                                title = prefs.notation.label(iv),
                                state = answerState(q.isCorrectInterval(iv), pickedInterval == iv, pickedInterval != null),
                                enabled = pickedInterval == null,
                                modifier = cell,
                            ) {
                                if (step == EarStep.INTERVAL && !round.isOver) {
                                    pickedInterval = iv
                                    answer(q.isCorrectInterval(iv))
                                    step = EarStep.DONE
                                }
                            }
                        }
                    } else {
                        val first = step == EarStep.FIRST
                        val options = if (first) q.firstOptions else q.secondOptions
                        val picked = if (first) pickedFirst else pickedSecond
                        val isRight: (NoteName) -> Boolean = if (first) q::isCorrectFirst else q::isCorrectSecond
                        AnswerGrid(options) { n, cell ->
                            AnswerButton(
                                title = n.toString(),
                                state = answerState(isRight(n), picked == n, picked != null),
                                enabled = picked == null,
                                modifier = cell,
                            ) {
                                if (round.isOver) return@AnswerButton
                                if (step == EarStep.FIRST) {
                                    pickedFirst = n
                                    answer(q.isCorrectFirst(n))
                                    step = EarStep.SECOND
                                } else if (step == EarStep.SECOND) {
                                    pickedSecond = n
                                    answer(q.isCorrectSecond(n))
                                    step = EarStep.DONE
                                }
                            }
                        }
                    }

                    // One line per answered step, with the right answer when it was missed.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pickedFirst?.let { ResultLine(if (harmonic) "Lower" else "First", q.isCorrectFirst(it), q.first.name.toString()) }
                        pickedSecond?.let { ResultLine(if (harmonic) "Upper" else "Second", q.isCorrectSecond(it), q.second.name.toString()) }
                        if (step == EarStep.DONE) {
                            Text("Interval: ${prefs.notation.label(q.interval)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (step == EarStep.DONE && !round.advancesAutomatically) NextButton { nextQuestion() }
                }
                else -> errorText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false; restart() }) {
            EarSettings(settings, { settings = it }, prefs)
        }
    }
}

@Composable
private fun ResultLine(label: String, right: Boolean, answer: String) {
    val color = if (right) CorrectGreen else WrongRed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (right) Icons.Filled.CheckCircle else Icons.Filled.Close, contentDescription = null, tint = color)
        Spacer(Modifier.width(6.dp))
        Text("$label: $answer", style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun EarSettings(settings: EarTrainingSettings, onChange: (EarTrainingSettings) -> Unit, prefs: AppPrefs) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Ear training settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SectionTitle("Ask for")
        listOf(EarQuestionStyle.NAME_NOTES to "The two notes", EarQuestionStyle.NAME_INTERVAL to "The interval").forEach { (style, title) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = settings.style == style, role = Role.RadioButton) { onChange(settings.copy(style = style)) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = settings.style == style, onClick = null)
                Text(title, modifier = Modifier.padding(start = 8.dp))
            }
        }
        SwitchRow("White keys only", settings.whiteKeysOnly) { onChange(settings.copy(whiteKeysOnly = it)) }
        SectionTitle("How the notes are played")
        EarDirection.entries.forEach { dir ->
            SwitchRow(dir.displayName, dir in settings.directions) { on ->
                onChange(settings.copy(directions = if (on) settings.directions + dir else settings.directions - dir))
            }
        }
        PlaySettings(prefs)
    }
}

/** Answer buttons in two columns. */
@Composable
fun <T> AnswerGrid(options: List<T>, button: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option -> button(option, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
