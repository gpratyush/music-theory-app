@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpratyush.musictheory.theory.IntervalQuestion
import com.gpratyush.musictheory.theory.IntervalQuestionKind
import com.gpratyush.musictheory.theory.IntervalQuizGenerator
import com.gpratyush.musictheory.theory.IntervalQuizSettings
import com.gpratyush.musictheory.theory.NoQuestionsPossible
import com.gpratyush.musictheory.theory.QuizAnswer
import com.gpratyush.musictheory.theory.QuizMode
import com.gpratyush.musictheory.theory.QuizSession
import kotlinx.coroutines.delay

@Composable
fun FlashcardsScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val view = LocalView.current
    var settings by remember { mutableStateOf(IntervalQuizSettings()) }
    var timed by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(QuizSession(QuizMode.Practice)) }
    var question by remember { mutableStateOf<IntervalQuestion?>(null) }
    var picked by remember { mutableStateOf<QuizAnswer?>(null) }
    var questionId by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSettings by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Timed rounds always move on by themselves.
    val advancesAutomatically = timed || prefs.autoNext
    val isOver = session.isOver(now)

    fun nextQuestion() {
        picked = null
        questionId += 1
        try {
            question = IntervalQuizGenerator(settings).next()
            errorText = null
        } catch (e: NoQuestionsPossible) {
            question = null
            errorText = "No questions match these settings. Try turning more options on."
        }
    }

    fun restart() {
        session = QuizSession(if (timed) QuizMode.Timed(prefs.timedSeconds) else QuizMode.Practice)
        now = System.currentTimeMillis()
        nextQuestion()
    }

    fun choose(option: QuizAnswer, q: IntervalQuestion) {
        if (picked != null || isOver) return
        picked = option
        val right = q.isCorrect(option)
        session = session.record(right)
        answerHaptic(view, right)
    }

    fun label(option: QuizAnswer): String = when (option) {
        is QuizAnswer.Note -> option.note.toString()
        is QuizAnswer.Iv -> prefs.notation.label(option.interval)
    }

    LaunchedEffect(timed) { restart() }

    // Ticks the countdown while a timed round runs.
    LaunchedEffect(session.startedAt, timed) {
        if (!timed) return@LaunchedEffect
        while (!session.isOver(System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    // Moves on by itself after an answer when auto-advance is on.
    LaunchedEffect(questionId, picked) {
        val q = question ?: return@LaunchedEffect
        val p = picked ?: return@LaunchedEffect
        if (!advancesAutomatically) return@LaunchedEffect
        delay(autoNextDelayMs(q.isCorrect(p), timed))
        nextQuestion()
    }

    Scaffold(
        topBar = { GameTopBar("Interval Flashcards", onBack = onBack, onSettings = { showSettings = true }) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            GameModeHeader(timed, { timed = it }, prefs.timedSeconds, session, now)

            val q = question
            when {
                isOver -> RoundResults(session, onPlayAgain = { restart() })
                q != null -> {
                    Text(
                        q.prompt(prefs.notation::label),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                            .semantics { heading() },
                    )

                    // Two columns of answer buttons.
                    q.options.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { option ->
                                AnswerButton(
                                    title = label(option),
                                    state = answerState(q.isCorrect(option), picked == option, picked != null),
                                    enabled = picked == null,
                                    minHeight = 64.dp,
                                    modifier = Modifier.weight(1f),
                                ) { choose(option, q) }
                            }
                        }
                    }

                    picked?.let { p ->
                        val right = q.isCorrect(p)
                        FeedbackBanner(right, if (right) "Correct" else "Answer: ${label(q.correct)}")
                        if (!advancesAutomatically) NextButton { nextQuestion() }
                    }
                }
                else -> errorText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = {
            showSettings = false
            restart()
        }) {
            FlashcardSettings(settings, { settings = it }, prefs)
        }
    }
}

@Composable
private fun FlashcardSettings(settings: IntervalQuizSettings, onChange: (IntervalQuizSettings) -> Unit, prefs: AppPrefs) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Flashcard settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SectionTitle("Notes")
        SwitchRow("White keys only", settings.whiteKeysOnly) { onChange(settings.copy(whiteKeysOnly = it)) }
        SwitchRow("Include 9ths, 11ths, 13ths", settings.includeCompound) { onChange(settings.copy(includeCompound = it)) }

        SectionTitle("Question types")
        listOf(
            IntervalQuestionKind.NOTE_ABOVE to "Note above (C + M3 = ?)",
            IntervalQuestionKind.ROOT_BELOW to "Root below (C is the 5th of ?)",
            IntervalQuestionKind.NAME_INTERVAL to "Name the interval (C → E = ?)",
        ).forEach { (kind, title) ->
            SwitchRow(title, kind in settings.kinds) { on ->
                onChange(settings.copy(kinds = if (on) settings.kinds + kind else settings.kinds - kind))
            }
        }

        PlaySettings(prefs)
    }
}

/** Auto-next and timed-round length, shared by every game's settings. */
@Composable
fun PlaySettings(prefs: AppPrefs) {
    SectionTitle("Play")
    SwitchRow("Go to next question automatically", prefs.autoNext) { prefs.autoNext = it }
    Text("Timed round length", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    listOf(30 to "30 seconds", 60 to "60 seconds", 120 to "2 minutes").forEach { (secs, title) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = prefs.timedSeconds == secs, role = Role.RadioButton) { prefs.timedSeconds = secs }
                .padding(vertical = 4.dp),
        ) {
            RadioButton(selected = prefs.timedSeconds == secs, onClick = null)
            Text(title, modifier = Modifier.padding(start = 8.dp))
        }
    }
    Text(
        "Timed rounds always move on automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
