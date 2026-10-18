package com.gpratyush.musictheory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

private val blackPitchClasses = setOf(1, 3, 6, 8, 10)

/** A read-only piano keyboard (C2 to B5) that lights up the given MIDI notes. */
@Composable
fun PianoKeyboard(highlighted: Set<Int>, modifier: Modifier = Modifier) {
    val range = 36..83
    val whiteKeys = range.filter { it % 12 !in blackPitchClasses }
    val keyWidth = 22.dp
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .width(keyWidth * whiteKeys.size)
            .height(110.dp)
            .clearAndSetSemantics { },
    ) {
        val w = keyWidth.toPx()
        val h = size.height
        whiteKeys.forEachIndexed { i, note ->
            val topLeft = Offset(i * w, 0f)
            drawRect(if (note in highlighted) accent else Color.White, topLeft, Size(w, h))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft, Size(w, h), style = Stroke(1f))
        }
        var whiteIndex = 0
        for (note in range) {
            if (note % 12 in blackPitchClasses) {
                // Sits across the line between the previous white key and the next.
                val x = whiteIndex * w - w * 0.3f
                if (x >= 0) drawRect(if (note in highlighted) accent else Color.Black, Offset(x, 0f), Size(w * 0.6f, h * 0.62f))
            } else {
                whiteIndex++
            }
        }
    }
}
