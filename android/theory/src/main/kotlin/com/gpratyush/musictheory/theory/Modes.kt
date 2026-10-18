package com.gpratyush.musictheory.theory

/** The seven modes of the major scale. */
enum class Mode(val displayName: String) {
    IONIAN("Ionian"), DORIAN("Dorian"), PHRYGIAN("Phrygian"), LYDIAN("Lydian"),
    MIXOLYDIAN("Mixolydian"), AEOLIAN("Aeolian"), LOCRIAN("Locrian");

    /** Semitones above the mode's own root, e.g. Dorian = 0 2 3 5 7 9 10. */
    val steps: List<Int>
        get() {
            val start = MAJOR_STEPS[ordinal]
            return (0 until 7).map { mod12(MAJOR_STEPS[(ordinal + it) % 7] - start) }
        }

    fun pitchClasses(root: NoteName): Set<Int> = steps.map { mod12(root.pitchClass + it) }.toSet()

    /** Spelled notes of this mode on `root` (one note per letter). */
    fun notes(root: NoteName): List<NoteName> = steps.mapIndexed { i, semis ->
        val letter = root.letter.advanced(i)
        var acc = mod12(root.pitchClass + semis - letter.semitone)
        if (acc > 6) acc -= 12
        NoteName(letter, acc)
    }

    /** The major key this mode comes from, e.g. D Dorian → C major. */
    fun parentKey(root: NoteName): NoteName = root.down(Interval.majorScaleDegrees[ordinal])

    private companion object {
        val MAJOR_STEPS = listOf(0, 2, 4, 5, 7, 9, 11)
    }
}

object MajorKey {
    /** The 12 major keys with their usual spellings. */
    val all: List<NoteName> = listOf(
        NoteName(Letter.C), NoteName(Letter.G), NoteName(Letter.D), NoteName(Letter.A),
        NoteName(Letter.E), NoteName(Letter.B), NoteName(Letter.F, 1), NoteName(Letter.D, -1),
        NoteName(Letter.A, -1), NoteName(Letter.E, -1), NoteName(Letter.B, -1), NoteName(Letter.F),
    )

    fun pitchClasses(key: NoteName): Set<Int> = Mode.IONIAN.pitchClasses(key)

    fun contains(key: NoteName, chord: Chord): Boolean = pitchClasses(key).containsAll(chord.pitchClasses)
}
