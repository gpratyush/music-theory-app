package com.gpratyush.musictheory.theory

import kotlin.random.Random

/** How the two notes are played. */
enum class EarDirection(val displayName: String) {
    ASCENDING("Up"), DESCENDING("Down"), HARMONIC("Together"),
}

/** What the learner is asked after hearing the notes. */
enum class EarQuestionStyle {
    /** Two steps: name the first note, then the second. */
    NAME_NOTES,
    /** One step: name the interval between them. */
    NAME_INTERVAL,
}

data class EarTrainingSettings(
    val whiteKeysOnly: Boolean = true,
    val intervals: List<Interval> = Interval.simple,
    val directions: Set<EarDirection> = setOf(EarDirection.ASCENDING),
    val style: EarQuestionStyle = EarQuestionStyle.NAME_NOTES,
)

/** One played note: its spelled name and MIDI number. */
data class PlayedNote(val name: NoteName, val midi: Int)

/** A reference note (named to the learner), then two notes to identify. */
data class EarQuestion(
    val reference: PlayedNote,
    val first: PlayedNote,
    val second: PlayedNote,
    val interval: Interval,
    val direction: EarDirection,
    val firstOptions: List<NoteName>,
    val secondOptions: List<NoteName>,
    val intervalOptions: List<Interval>,
) {
    fun isCorrectFirst(n: NoteName) = n.isEnharmonicTo(first.name)
    fun isCorrectSecond(n: NoteName) = n.isEnharmonicTo(second.name)
    fun isCorrectInterval(iv: Interval) = iv.semitones == interval.semitones
}

class EarTrainingGenerator(val settings: EarTrainingSettings = EarTrainingSettings()) {
    private data class Combo(val first: NoteName, val interval: Interval, val direction: EarDirection, val second: NoteName)

    private val pool: List<NoteName>
        get() = if (settings.whiteKeysOnly) NoteName.naturals else NoteName.commonSpellings

    fun next(random: Random = Random.Default): EarQuestion {
        val intervals = settings.intervals.filter { !it.isCompound }
        // Every (first note, interval, direction) whose second note stays in the allowed set.
        val combos = mutableListOf<Combo>()
        for (first in pool) for (iv in intervals) for (dir in EarDirection.entries) {
            if (dir !in settings.directions) continue
            val second = if (dir == EarDirection.DESCENDING) first.down(iv) else first.up(iv)
            if (second in pool) combos += Combo(first, iv, dir, second)
        }
        if (combos.isEmpty()) throw NoQuestionsPossible()
        val pick = combos.random(random)

        // Reference: a natural note, C4...B4.
        val refName = NoteName.naturals.random(random)
        val reference = PlayedNote(refName, 60 + refName.pitchClass)

        // First note within a fifth or so of the reference, so it's easy to relate.
        var firstMidi = reference.midi + (pick.first.pitchClass - refName.pitchClass)
        if (firstMidi - reference.midi > 6) firstMidi -= 12
        if (reference.midi - firstMidi > 6) firstMidi += 12
        val secondMidi = if (pick.direction == EarDirection.DESCENDING) firstMidi - pick.interval.semitones
        else firstMidi + pick.interval.semitones

        return EarQuestion(
            reference = reference,
            first = PlayedNote(pick.first, firstMidi),
            second = PlayedNote(pick.second, secondMidi),
            interval = pick.interval,
            direction = pick.direction,
            firstOptions = QuizOptions.notes(pick.first, pool, random),
            secondOptions = QuizOptions.notes(pick.second, pool, random),
            intervalOptions = QuizOptions.intervals(pick.interval, random),
        )
    }
}
