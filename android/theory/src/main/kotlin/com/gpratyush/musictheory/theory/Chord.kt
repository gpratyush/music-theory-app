package com.gpratyush.musictheory.theory

import com.gpratyush.musictheory.theory.Interval.Companion.A5
import com.gpratyush.musictheory.theory.Interval.Companion.M2
import com.gpratyush.musictheory.theory.Interval.Companion.M3
import com.gpratyush.musictheory.theory.Interval.Companion.M6
import com.gpratyush.musictheory.theory.Interval.Companion.M7
import com.gpratyush.musictheory.theory.Interval.Companion.M9
import com.gpratyush.musictheory.theory.Interval.Companion.P1
import com.gpratyush.musictheory.theory.Interval.Companion.P4
import com.gpratyush.musictheory.theory.Interval.Companion.P5
import com.gpratyush.musictheory.theory.Interval.Companion.d5
import com.gpratyush.musictheory.theory.Interval.Companion.d7
import com.gpratyush.musictheory.theory.Interval.Companion.m3
import com.gpratyush.musictheory.theory.Interval.Companion.m7

/** Chord qualities the app understands (harmony v1 vocabulary plus common 9ths). */
enum class ChordQuality(
    /** How the chord is written after the root. */
    val symbol: String,
    /** Spoken name, used as the answer label in Chord Ear Training. */
    val displayName: String,
    /** Spelled intervals above the root, in voicing order. */
    val intervals: List<Interval>,
) {
    MAJOR("", "Major", listOf(P1, M3, P5)),
    MINOR("m", "Minor", listOf(P1, m3, P5)),
    DIMINISHED("dim", "Diminished", listOf(P1, m3, d5)),
    AUGMENTED("aug", "Augmented", listOf(P1, M3, A5)),
    SUS2("sus2", "Sus2", listOf(P1, M2, P5)),
    SUS4("sus4", "Sus4", listOf(P1, P4, P5)),
    MAJOR6("6", "Major 6th", listOf(P1, M3, P5, M6)),
    MINOR6("m6", "Minor 6th", listOf(P1, m3, P5, M6)),
    DOMINANT7("7", "Dominant 7th", listOf(P1, M3, P5, m7)),
    MAJOR7("maj7", "Major 7th", listOf(P1, M3, P5, M7)),
    MINOR7("m7", "Minor 7th", listOf(P1, m3, P5, m7)),
    HALF_DIMINISHED7("m7b5", "Half-diminished 7th", listOf(P1, m3, d5, m7)),
    DIMINISHED7("dim7", "Diminished 7th", listOf(P1, m3, d5, d7)),
    MINOR_MAJOR7("mMaj7", "Minor-major 7th", listOf(P1, m3, P5, M7)),
    DOMINANT7_SUS4("7sus4", "7sus4", listOf(P1, P4, P5, m7)),
    ADD2("add2", "Add2", listOf(P1, M2, M3, P5)),
    ADD4("add4", "Add4", listOf(P1, M3, P4, P5)),
    ADD9("add9", "Add9", listOf(P1, M3, P5, M9)),
    MINOR_ADD9("madd9", "Minor add9", listOf(P1, m3, P5, M9)),
    DOMINANT9("9", "Dominant 9th", listOf(P1, M3, P5, m7, M9)),
    MAJOR9("maj9", "Major 9th", listOf(P1, M3, P5, M7, M9)),
    MINOR9("m9", "Minor 9th", listOf(P1, m3, P5, m7, M9));

    /** Semitones above the root, in voicing order. */
    val offsets: List<Int> get() = intervals.map { it.semitones }

    /** Major-sounding chords get an upper-case Roman numeral. */
    internal val hasMajorThird: Boolean
        get() = 4 in offsets || this == SUS2 || this == SUS4 || this == DOMINANT7_SUS4

    companion object {
        /** Accepted spellings for each quality (exact match, so "M7" and "m7" differ). */
        val spellings: Map<String, ChordQuality> = buildMap {
            val table = listOf(
                MAJOR to listOf("", "maj", "M", "major"),
                MINOR to listOf("m", "min", "mi", "minor"),
                DIMINISHED to listOf("dim", "°", "o"),
                AUGMENTED to listOf("aug", "+"),
                SUS2 to listOf("sus2"),
                SUS4 to listOf("sus4", "sus"),
                MAJOR6 to listOf("6", "add6", "maj6"),
                MINOR6 to listOf("m6", "min6"),
                DOMINANT7 to listOf("7", "dom7"),
                MAJOR7 to listOf("maj7", "M7", "Δ", "Δ7", "ma7", "Maj7"),
                MINOR7 to listOf("m7", "min7", "mi7"),
                HALF_DIMINISHED7 to listOf("m7b5", "ø", "ø7", "min7b5", "m7♭5"),
                DIMINISHED7 to listOf("dim7", "°7", "o7"),
                MINOR_MAJOR7 to listOf("mMaj7", "mM7", "mmaj7", "minMaj7"),
                DOMINANT7_SUS4 to listOf("7sus4", "7sus"),
                ADD2 to listOf("add2", "2"),
                ADD4 to listOf("add4"),
                ADD9 to listOf("add9"),
                MINOR_ADD9 to listOf("madd9", "madd2", "minadd9"),
                DOMINANT9 to listOf("9"),
                MAJOR9 to listOf("maj9", "M9", "Δ9"),
                MINOR9 to listOf("m9", "min9"),
            )
            for ((q, names) in table) for (n in names) put(n, q)
        }
    }
}

data class Chord(
    val root: NoteName,
    val quality: ChordQuality,
    /** Slash-chord bass note, e.g. the E in C/E. */
    val bass: NoteName? = null,
) {
    override fun toString(): String = "$root${quality.symbol}" + (bass?.let { "/$it" } ?: "")

    /** Pitch classes in the chord (bass included). */
    val pitchClasses: Set<Int>
        get() = quality.offsets.map { mod12(root.pitchClass + it) }.toMutableSet().also { set ->
            bass?.let { set += it.pitchClass }
        }

    /**
     * A simple piano voicing as MIDI note numbers: a bass note in octave 2
     * and the chord in close position starting in octave 3.
     */
    val voicing: List<Int>
        get() {
            val bassNote = 36 + (bass ?: root).pitchClass
            val chordRoot = 48 + root.pitchClass
            return listOf(bassNote) + quality.offsets.map { chordRoot + it }
        }

    /** Chord tones spelled from the root, in order (no slash bass). */
    val spelledTones: List<NoteName> get() = quality.intervals.map { root.up(it) }

    /** Roman numeral in a major key, e.g. Dm7 in C → "ii7", D7 in C → "II7". */
    fun romanNumeral(majorKey: NoteName): String {
        val semis = mod12(root.pitchClass - majorKey.pitchClass)
        val names = listOf("I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII")
        var numeral = names[semis]
        if (!quality.hasMajorThird) numeral = numeral.lowercase()
        val suffix = when (quality) {
            ChordQuality.MAJOR, ChordQuality.MINOR -> ""
            ChordQuality.DIMINISHED -> "°"
            ChordQuality.AUGMENTED -> "+"
            ChordQuality.HALF_DIMINISHED7 -> "ø7"
            ChordQuality.DIMINISHED7 -> "°7"
            ChordQuality.MINOR7 -> "7"
            ChordQuality.MINOR6 -> "6"
            ChordQuality.MINOR9 -> "9"
            ChordQuality.MINOR_ADD9 -> "add9"
            else -> quality.symbol
        }
        return numeral + suffix + (bass?.let { "/$it" } ?: "")
    }
}
