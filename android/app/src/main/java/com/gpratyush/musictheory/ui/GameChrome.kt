@file:OptIn(ExperimentalMaterial3Api::class)

package com.gpratyush.musictheory.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpratyush.musictheory.theory.QuizSession

// Shared pieces every quiz game uses, so they all look and feel the same.

/** How an answer button looks right now. */
enum class AnswerState { NORMAL, SELECTED, CORRECT, WRONG }

/** Answer state for single-pick questions: green on the right answer, red on a wrong pick. */
fun answerState(isRight: Boolean, isPicked: Boolean, revealed: Boolean): AnswerState = when {
    !revealed -> AnswerState.NORMAL
    isRight -> AnswerState.CORRECT
    isPicked -> AnswerState.WRONG
    else -> AnswerState.NORMAL
}

/** A big, rounded answer button that turns green or red once answered. */
@Composable
fun AnswerButton(
    title: String,
    state: AnswerState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp = 60.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val target = when (state) {
        AnswerState.NORMAL -> MaterialTheme.colorScheme.onSurface
        AnswerState.SELECTED -> accent
        AnswerState.CORRECT -> CorrectGreen
        AnswerState.WRONG -> WrongRed
    }
    val tint by animateColorAsState(target, label = "answerTint")
    val background = if (state == AnswerState.NORMAL) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    else tint.copy(alpha = 0.15f)
    val border = if (state == AnswerState.NORMAL) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    else BorderStroke(2.dp, tint)
    val scale by animateFloatAsState(
        if (state == AnswerState.CORRECT) 1.03f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "answerScale",
    )
    val stateText = when (state) {
        AnswerState.NORMAL -> ""
        AnswerState.SELECTED -> "Selected"
        AnswerState.CORRECT -> "Correct answer"
        AnswerState.WRONG -> "Wrong"
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = background,
        contentColor = tint,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .scale(scale)
            .semantics { stateDescription = stateText },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            when (state) {
                AnswerState.CORRECT -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                AnswerState.WRONG -> Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                else -> {}
            }
            if (state == AnswerState.CORRECT || state == AnswerState.WRONG) Spacer(Modifier.width(6.dp))
            Text(title, style = textStyle, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

/** Practice / Timed switch plus the score or countdown under it. */
@Composable
fun GameModeHeader(timed: Boolean, onTimedChange: (Boolean) -> Unit, timedSeconds: Int, session: QuizSession, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !timed,
                onClick = { onTimedChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Practice") }
            SegmentedButton(
                selected = timed,
                onClick = { onTimedChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Timed ${timedSeconds}s") }
        }

        val left = session.remaining(now)
        if (left != null) {
            val urgent = left <= 10
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$left seconds left, ${session.correct} right"
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⏱ ${left}s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (urgent) WrongRed else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CorrectGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${session.correct}", style = MaterialTheme.typography.titleMedium, color = CorrectGreen)
                }
                LinearProgressIndicator(
                    progress = { left.toFloat() / timedSeconds.coerceAtLeast(1) },
                    color = if (urgent) WrongRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${session.correct} right out of ${session.answered}"
                },
            ) {
                Text(
                    "✓ ${session.correct} / ${session.answered}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                session.accuracy?.let {
                    Text("$it%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** End-of-round summary for timed mode. */
@Composable
fun RoundResults(session: QuizSession, onPlayAgain: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
    ) {
        Text("🏁", fontSize = 48.sp)
        Text("Time's up!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("${session.correct} right", fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Text(
            "out of ${session.answered} answered",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        session.accuracy?.let {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("$it% accuracy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Button(onClick = onPlayAgain, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play again")
        }
    }
}

/** "Correct" or "Answer: X" line shown after answering. */
@Composable
fun FeedbackBanner(isCorrect: Boolean, text: String) {
    val color = if (isCorrect) CorrectGreen else WrongRed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Close, contentDescription = null, tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** The big "Next" button for practice mode. */
@Composable
fun NextButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.width(180.dp)) {
        Text("Next")
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

/** Top bar for a game: back arrow, title, settings button. */
@Composable
fun GameTopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Game settings") }
        },
    )
}

/** A light buzz for right and wrong answers. */
fun answerHaptic(view: View, correct: Boolean) {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
    } else {
        if (correct) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.LONG_PRESS
    }
    view.performHapticFeedback(type)
}

/** Delay before moving on: wrong answers stay up longer so the right one can be read. */
fun autoNextDelayMs(correct: Boolean, timed: Boolean): Long = when {
    correct -> 700L
    timed -> 1_200L
    else -> 1_800L
}
