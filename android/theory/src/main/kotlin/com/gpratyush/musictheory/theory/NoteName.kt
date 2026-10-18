package com.gpratyush.musictheory.theory

import kotlin.math.min

/** A letter name with no accidental: C D E F G A B. */
enum class Letter(val semitone: Int, val symbol: String) {
    C(0, "C"), D(2, "D"), E(4, "E"), F(5, "F"), G(7, "G"), A(9, "A"), B(11, "B");

    /** The letter `steps` letters away (wraps around). */
    fun advanced(steps: Int): Letter = entries[((ordinal + steps) % 7 + 7) % 7]
}

/** A spelled note without octave, e.g. C, F#, Bb. */
data class NoteName(val letter: Letter, val accidental: Int = 0) {
    /** 0..11, C = 0. */
    val pitchClass: Int get() = mod12(letter.semitone + accidental)

    val isNatural: Boolean get() = accidental == 0

    override fun toString(): String = letter.symbol + when (accidental) {
        -2 -> "bb"
        -1 -> "b"
        1 -> "#"
        2 -> "##"
        else -> ""
    }

    /** Same sound, regardless of spelling (Ab == G#). */
    fun isEnharmonicTo(other: NoteName): Boolean = pitchClass == other.pitchClass

    companion object {
        val naturals: List<NoteName> = Letter.entries.map { NoteName(it) }

        /** Common spellings for the 12 pitch classes, used for roots and options. */
        val commonSpellings: List<NoteName> = listOf(
            NoteName(Letter.C), NoteName(Letter.C, 1), NoteName(Letter.D, -1), NoteName(Letter.D),
            NoteName(Letter.D, 1), NoteName(Letter.E, -1), NoteName(Letter.E), NoteName(Letter.F),
            NoteName(Letter.F, 1), NoteName(Letter.G, -1), NoteName(Letter.G), NoteName(Letter.G, 1),
            NoteName(Letter.A, -1), NoteName(Letter.A), NoteName(Letter.A, 1), NoteName(Letter.B, -1),
            NoteName(Letter.B),
        )
    }
}

fun mod12(x: Int): Int = ((x % 12) + 12) % 12

fun circularDistance(a: Int, b: Int): Int {
    val d = mod12(a - b)
    return min(d, 12 - d)
}

/** Thrown when the settings leave nothing to ask (e.g. no intervals picked). */
class NoQuestionsPossible : Exception("No questions match these settings")
