package com.gpratyush.musictheory.theory

import kotlin.math.abs
import kotlin.random.Random

data class ChordEarSettings(
    val qualities: List<ChordQuality> = listOf(
        ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED,
    ),
)

/** "Listen: what kind of chord was that?" */
data class ChordEarQuestion(val chord: Chord, val options: List<ChordQuality>) {
    fun isCorrect(q: ChordQuality) = q == chord.quality
}

class ChordEarGenerator(val settings: ChordEarSettings = ChordEarSettings()) {
    fun next(random: Random = Random.Default): ChordEarQuestion {
        if (settings.qualities.isEmpty()) throw NoQuestionsPossible()
        val quality = settings.qualities.random(random)
        val root = NoteName.commonSpellings.random(random)
        val chord = Chord(root, quality)

        // Wrong answers come from the qualities being practised first, then the closest
        // others (same number of notes), so there are always four choices.
        val practised = settings.qualities.filter { it != quality }.shuffled(random)
        val others = HarmonySettings.v1Qualities
            .filter { it != quality && it !in settings.qualities }
            .shuffled(random)
            .sortedBy { abs(it.offsets.size - quality.offsets.size) }
        val wrong = (practised + others).take(3)
        return ChordEarQuestion(chord, (listOf(quality) + wrong).shuffled(random))
    }
}
