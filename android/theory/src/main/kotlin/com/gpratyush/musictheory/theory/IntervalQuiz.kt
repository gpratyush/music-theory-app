package com.gpratyush.musictheory.theory

import kotlin.random.Random

/** The three flashcard question styles. */
enum class IntervalQuestionKind {
    /** "What is the major 3rd above C?" → E */
    NOTE_ABOVE,
    /** "C is the perfect 5th of which note?" → F */
    ROOT_BELOW,
    /** "What interval is C up to E?" → Major 3rd */
    NAME_INTERVAL,
}

data class IntervalQuizSettings(
    /** Keep every note in the question and answer on the white keys. */
    val whiteKeysOnly: Boolean = true,
    /** Allow 9ths, 11ths and 13ths. */
    val includeCompound: Boolean = false,
    val intervals: List<Interval> = Interval.simple,
    val kinds: Set<IntervalQuestionKind> = IntervalQuestionKind.entries.toSet(),
) {
    internal val activeIntervals: List<Interval>
        get() = if (includeCompound) intervals + Interval.compound else intervals.filter { !it.isCompound }
}

sealed interface QuizAnswer {
    data class Note(val note: NoteName) : QuizAnswer
    data class Iv(val interval: Interval) : QuizAnswer
}

data class IntervalQuestion(
    val kind: IntervalQuestionKind,
    /** The note shown in the question. */
    val given: NoteName,
    /** For NAME_INTERVAL, the second note shown. */
    val other: NoteName?,
    val interval: Interval,
    val options: List<QuizAnswer>,
    val correct: QuizAnswer,
) {
    /**
     * Enharmonic answers count: Ab and G# are both right, and so are
     * an augmented 4th and a diminished 5th.
     */
    fun isCorrect(answer: QuizAnswer): Boolean = when {
        answer is QuizAnswer.Note && correct is QuizAnswer.Note -> answer.note.isEnharmonicTo(correct.note)
        answer is QuizAnswer.Iv && correct is QuizAnswer.Iv -> answer.interval.semitones == correct.interval.semitones
        else -> false
    }

    fun prompt(label: (Interval) -> String): String = when (kind) {
        IntervalQuestionKind.NOTE_ABOVE -> "What is the ${label(interval)} above $given?"
        IntervalQuestionKind.ROOT_BELOW -> "$given is the ${label(interval)} of which note?"
        IntervalQuestionKind.NAME_INTERVAL -> "What interval is $given up to $other?"
    }
}

class IntervalQuizGenerator(val settings: IntervalQuizSettings = IntervalQuizSettings()) {
    private data class Candidate(val kind: IntervalQuestionKind, val given: NoteName, val interval: Interval, val note: NoteName)

    private val notePool: List<NoteName>
        get() = if (settings.whiteKeysOnly) NoteName.naturals else NoteName.commonSpellings

    private fun allowed(note: NoteName) = note in notePool

    /** Every (kind, given note, interval) combination the settings allow. */
    private fun candidates(): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (kind in IntervalQuestionKind.entries) {
            if (kind !in settings.kinds) continue
            for (given in notePool) {
                for (iv in settings.activeIntervals) {
                    when (kind) {
                        IntervalQuestionKind.NOTE_ABOVE -> given.up(iv).let { if (allowed(it)) out += Candidate(kind, given, iv, it) }
                        IntervalQuestionKind.ROOT_BELOW -> given.down(iv).let { if (allowed(it)) out += Candidate(kind, given, iv, it) }
                        IntervalQuestionKind.NAME_INTERVAL -> {
                            // Without octaves, only intervals inside one octave are unambiguous.
                            if (iv.isCompound || iv.number >= 8) continue
                            given.up(iv).let { if (allowed(it)) out += Candidate(kind, given, iv, it) }
                        }
                    }
                }
            }
        }
        return out
    }

    fun next(random: Random = Random.Default): IntervalQuestion {
        val all = candidates()
        if (all.isEmpty()) throw NoQuestionsPossible()
        val pick = all.random(random)
        return when (pick.kind) {
            IntervalQuestionKind.NOTE_ABOVE, IntervalQuestionKind.ROOT_BELOW -> {
                val correct = QuizAnswer.Note(pick.note)
                val options = QuizOptions.notes(pick.note, notePool, random).map { QuizAnswer.Note(it) }
                IntervalQuestion(pick.kind, pick.given, null, pick.interval, options, correct)
            }
            IntervalQuestionKind.NAME_INTERVAL -> {
                val correct = QuizAnswer.Iv(pick.interval)
                val options = QuizOptions.intervals(pick.interval, random, Interval.simple.filter { it.number < 8 })
                    .map { QuizAnswer.Iv(it) }
                IntervalQuestion(pick.kind, pick.given, pick.note, pick.interval, options, correct)
            }
        }
    }
}

/**
 * Builds 4-option answer sets shared by the games: the right answer plus three
 * nearby wrong ones, never two that sound the same.
 */
object QuizOptions {
    fun notes(correct: NoteName, pool: List<NoteName>, random: Random): List<NoteName> {
        val seen = mutableSetOf(correct.pitchClass)
        val near = pool.shuffled(random)
            .sortedBy { circularDistance(it.pitchClass, correct.pitchClass) }
            .filter { seen.add(it.pitchClass) }
        val wrong = near.take(5).shuffled(random).take(3)
        return (listOf(correct) + wrong).shuffled(random)
    }

    fun intervals(correct: Interval, random: Random, source: List<Interval> = Interval.simple): List<Interval> {
        val seen = mutableSetOf(correct.semitones)
        val near = source.shuffled(random)
            .sortedBy { kotlin.math.abs(it.semitones - correct.semitones) }
            .filter { seen.add(it.semitones) }
        val wrong = near.take(5).shuffled(random).take(3)
        return (listOf(correct) + wrong).shuffled(random)
    }
}
