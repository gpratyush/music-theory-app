package com.gpratyush.musictheory.theory

/** How interval names are shown to the learner. */
enum class IntervalNotation(val displayName: String) {
    WESTERN("Western"),
    SWARA("Sa Re Ga");

    fun label(interval: Interval): String = when (this) {
        WESTERN -> interval.name
        SWARA -> SwaraTable.label(interval)
    }
}

/**
 * Maps an interval (by semitones above Sa) to its swara label.
 *
 * PLACEHOLDER: these are the standard Hindustani names. Replace the values
 * with Pratyush's translation table when it arrives; nothing else needs to change.
 */
object SwaraTable {
    val labels: Map<Int, String> = mapOf(
        0 to "Sa", 1 to "komal Re", 2 to "Re", 3 to "komal Ga", 4 to "Ga", 5 to "Ma",
        6 to "tivra Ma", 7 to "Pa", 8 to "komal Dha", 9 to "Dha", 10 to "komal Ni", 11 to "Ni",
    )

    fun label(interval: Interval): String {
        val semis = interval.semitones
        if (semis == 12) return "Sa'" // upper Sa
        val base = labels[mod12(semis)] ?: interval.shortName
        return if (semis > 12) "$base'" else base // compound intervals sit in the upper octave
    }
}
