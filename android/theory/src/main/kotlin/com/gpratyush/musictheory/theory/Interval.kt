package com.gpratyush.musictheory.theory

enum class IntervalQuality(val displayName: String, val abbreviation: String) {
    PERFECT("Perfect", "P"),
    MAJOR("Major", "M"),
    MINOR("Minor", "m"),
    AUGMENTED("Augmented", "A"),
    DIMINISHED("Diminished", "d"),
}

/**
 * A spelled interval such as a minor 3rd or a perfect 5th.
 * `number` is the letter distance plus one (unison = 1, octave = 8, 9th = 9...).
 */
data class Interval(val quality: IntervalQuality, val number: Int) {
    init {
        require(number >= 1) { "Interval number starts at 1 (unison)" }
    }

    val semitones: Int
        get() {
            val simple = (number - 1) % 7
            val octaves = (number - 1) / 7
            val base = intArrayOf(0, 2, 4, 5, 7, 9, 11)[simple]
            val offset = if (isPerfectType(number)) {
                when (quality) {
                    IntervalQuality.AUGMENTED -> 1
                    IntervalQuality.DIMINISHED -> -1
                    else -> 0
                }
            } else {
                when (quality) {
                    IntervalQuality.MINOR -> -1
                    IntervalQuality.AUGMENTED -> 1
                    IntervalQuality.DIMINISHED -> -2
                    else -> 0
                }
            }
            return base + offset + 12 * octaves
        }

    val isCompound: Boolean get() = number > 8

    val name: String
        get() = when {
            number == 1 && quality == IntervalQuality.PERFECT -> "Unison"
            number == 8 && quality == IntervalQuality.PERFECT -> "Octave"
            else -> "${quality.displayName} ${ordinal(number)}"
        }

    val shortName: String get() = "${quality.abbreviation}$number"

    override fun toString(): String = name

    companion object {
        /** 1, 4, 5, 8 (and their compounds) are perfect-type intervals. */
        fun isPerfectType(number: Int): Boolean = ((number - 1) % 7 + 1) in setOf(1, 4, 5)

        @JvmField val P1 = Interval(IntervalQuality.PERFECT, 1)
        @JvmField val m2 = Interval(IntervalQuality.MINOR, 2)
        @JvmField val M2 = Interval(IntervalQuality.MAJOR, 2)
        @JvmField val m3 = Interval(IntervalQuality.MINOR, 3)
        @JvmField val M3 = Interval(IntervalQuality.MAJOR, 3)
        @JvmField val P4 = Interval(IntervalQuality.PERFECT, 4)
        @JvmField val A4 = Interval(IntervalQuality.AUGMENTED, 4)
        @JvmField val d5 = Interval(IntervalQuality.DIMINISHED, 5)
        @JvmField val P5 = Interval(IntervalQuality.PERFECT, 5)
        @JvmField val A5 = Interval(IntervalQuality.AUGMENTED, 5)
        @JvmField val m6 = Interval(IntervalQuality.MINOR, 6)
        @JvmField val M6 = Interval(IntervalQuality.MAJOR, 6)
        @JvmField val d7 = Interval(IntervalQuality.DIMINISHED, 7)
        @JvmField val m7 = Interval(IntervalQuality.MINOR, 7)
        @JvmField val M7 = Interval(IntervalQuality.MAJOR, 7)
        @JvmField val P8 = Interval(IntervalQuality.PERFECT, 8)
        @JvmField val M9 = Interval(IntervalQuality.MAJOR, 9)

        /** The intervals a learner meets first, within one octave. */
        val simple: List<Interval> = listOf(m2, M2, m3, M3, P4, A4, d5, P5, m6, M6, m7, M7, P8)

        val compound: List<Interval> = listOf(
            Interval(IntervalQuality.MINOR, 9), M9,
            Interval(IntervalQuality.PERFECT, 11), Interval(IntervalQuality.AUGMENTED, 11),
            Interval(IntervalQuality.MINOR, 13), Interval(IntervalQuality.MAJOR, 13),
        )

        /** Intervals from the tonic to each degree of the major scale. */
        val majorScaleDegrees: List<Interval> = listOf(P1, M2, M3, P4, P5, M6, M7)
    }
}

/** The note this interval above. Spelled correctly: C + minor 6th = Ab. */
fun NoteName.up(interval: Interval): NoteName {
    val target = letter.advanced(interval.number - 1)
    val wanted = pitchClass + interval.semitones
    return NoteName(target, smallestAccidental(target.semitone, wanted))
}

/** The note this interval below. "C is the perfect 5th of what?" = C.down(P5) = F. */
fun NoteName.down(interval: Interval): NoteName {
    val target = letter.advanced(-(interval.number - 1))
    val wanted = pitchClass - interval.semitones
    return NoteName(target, smallestAccidental(target.semitone, wanted))
}

/** The simple interval from this note up to `other` (within an octave), by spelling. */
fun NoteName.intervalTo(other: NoteName): Interval? {
    val steps = ((other.letter.ordinal - letter.ordinal) % 7 + 7) % 7
    val number = steps + 1
    val semis = mod12(other.pitchClass - pitchClass)
    for (q in IntervalQuality.entries) {
        val perfectType = Interval.isPerfectType(number)
        if (perfectType && (q == IntervalQuality.MAJOR || q == IntervalQuality.MINOR)) continue
        if (!perfectType && q == IntervalQuality.PERFECT) continue
        val iv = Interval(q, number)
        if (mod12(iv.semitones) == semis) return iv
    }
    return null
}

private fun smallestAccidental(natural: Int, wanted: Int): Int {
    var diff = mod12(wanted - natural)
    if (diff > 6) diff -= 12
    return diff
}

private fun ordinal(n: Int): String {
    val suffix = when (n % 100) {
        11, 12, 13 -> "th"
        else -> when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$n$suffix"
}
