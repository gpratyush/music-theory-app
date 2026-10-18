package com.gpratyush.musictheory.theory

import kotlin.random.Random

enum class HarmonyQuestionKind(val displayName: String) {
    /** "Which chord is D F A C?" → Dm7 */
    NAME_CHORD("Name the chord"),
    /** "Which notes make Dm7?" → D F A C */
    SPELL_CHORD("Spell the chord"),
    /** "Which major keys contain Dm7?" → C, F, Bb (pick all) */
    KEYS_CONTAINING("Which keys contain it"),
    /** "Which modes on D contain Dm7?" → Dorian, Phrygian, Aeolian (pick all) */
    MODES_ON_ROOT("Which modes contain it"),
    /** "In C major, what is V/V?" → D7 */
    SECONDARY_DOMINANT("Find the secondary dominant"),
    /** "In C major, A7 is the V of which chord?" → Dm (ii) */
    DOMINANT_TARGET("Where a secondary dominant goes"),
}

data class HarmonySettings(
    val kinds: Set<HarmonyQuestionKind> = HarmonyQuestionKind.entries.toSet(),
    val qualities: List<ChordQuality> = v1Qualities,
    /** Roots C D E F G A B only. */
    val naturalRootsOnly: Boolean = true,
) {
    companion object {
        /** Triads, 7ths, suspended and add chords within the octave. */
        val v1Qualities: List<ChordQuality> = listOf(
            ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED,
            ChordQuality.DOMINANT7, ChordQuality.MAJOR7, ChordQuality.MINOR7, ChordQuality.HALF_DIMINISHED7,
            ChordQuality.DIMINISHED7, ChordQuality.MINOR_MAJOR7,
            ChordQuality.SUS2, ChordQuality.SUS4, ChordQuality.DOMINANT7_SUS4,
            ChordQuality.ADD2, ChordQuality.ADD4, ChordQuality.MAJOR6, ChordQuality.MINOR6,
        )
    }
}

/** A harmony question. Options are labels; some questions have several right answers. */
data class HarmonyQuestion(
    val kind: HarmonyQuestionKind,
    val prompt: String,
    val options: List<String>,
    val correct: Set<Int>,
    /** Shown after answering: why the answer is what it is. */
    val explanation: String,
    /** The chord the question is about, so the app can play it. */
    val chord: Chord,
) {
    /** More than one option may be right; the learner picks all that apply. */
    val isMultiSelect: Boolean
        get() = kind == HarmonyQuestionKind.KEYS_CONTAINING || kind == HarmonyQuestionKind.MODES_ON_ROOT

    fun isCorrect(picked: Set<Int>) = picked == correct
}

class HarmonyQuizGenerator(val settings: HarmonySettings = HarmonySettings()) {
    private data class Target(val degree: Int, val numeral: String, val quality: ChordQuality)

    private val roots: List<NoteName>
        get() = if (settings.naturalRootsOnly) NoteName.naturals else NoteName.commonSpellings

    fun next(random: Random = Random.Default): HarmonyQuestion {
        if (settings.qualities.isEmpty()) throw NoQuestionsPossible()
        // Some kinds can't be asked about some chords (e.g. no major key has an augmented
        // triad), so try a few picks before giving up.
        val kinds = HarmonyQuestionKind.entries.filter { it in settings.kinds }
        if (kinds.isEmpty()) throw NoQuestionsPossible()
        repeat(50) {
            make(kinds.random(random), random)?.let { return it }
        }
        throw NoQuestionsPossible()
    }

    private fun make(kind: HarmonyQuestionKind, random: Random): HarmonyQuestion? = when (kind) {
        HarmonyQuestionKind.NAME_CHORD -> nameChord(random)
        HarmonyQuestionKind.SPELL_CHORD -> spellChord(random)
        HarmonyQuestionKind.KEYS_CONTAINING -> keysContaining(random)
        HarmonyQuestionKind.MODES_ON_ROOT -> modesOnRoot(random)
        HarmonyQuestionKind.SECONDARY_DOMINANT -> secondaryDominant(random)
        HarmonyQuestionKind.DOMINANT_TARGET -> dominantTarget(random)
    }

    private fun randomChord(random: Random) = Chord(roots.random(random), settings.qualities.random(random))

    /** Up to 3 other qualities on the same root that don't share the same notes. */
    private fun otherChords(chord: Chord, random: Random): List<Chord> {
        val seen = mutableSetOf(chord.pitchClasses)
        val out = mutableListOf<Chord>()
        for (q in HarmonySettings.v1Qualities.shuffled(random)) {
            val c = Chord(chord.root, q)
            // Prefer chords with the same number of notes: they're the believable mistakes.
            if (c.quality.offsets.size != chord.quality.offsets.size && out.size < 2) continue
            if (seen.add(c.pitchClasses)) out += c
            if (out.size == 3) break
        }
        // Not enough same-size chords: fill from any size.
        for (q in HarmonySettings.v1Qualities.shuffled(random)) {
            if (out.size >= 3) break
            val c = Chord(chord.root, q)
            if (seen.add(c.pitchClasses)) out += c
        }
        return out
    }

    private fun shuffledOptions(correct: String, wrong: List<String>, random: Random): Pair<List<String>, Set<Int>> {
        val all = (listOf(correct) + wrong).shuffled(random)
        return all to setOf(all.indexOf(correct))
    }

    private fun spell(notes: List<NoteName>) = notes.joinToString(" ")

    private fun explainChord(chord: Chord) =
        "$chord = ${spell(chord.spelledTones)} (${chord.quality.intervals.joinToString(" ") { it.shortName }})"

    private fun nameChord(random: Random): HarmonyQuestion {
        val chord = randomChord(random)
        val (options, correct) = shuffledOptions(chord.toString(), otherChords(chord, random).map { it.toString() }, random)
        return HarmonyQuestion(
            HarmonyQuestionKind.NAME_CHORD, "Which chord is ${spell(chord.spelledTones)}?",
            options, correct, explainChord(chord), chord,
        )
    }

    private fun spellChord(random: Random): HarmonyQuestion {
        val chord = randomChord(random)
        val (options, correct) = shuffledOptions(
            spell(chord.spelledTones), otherChords(chord, random).map { spell(it.spelledTones) }, random,
        )
        return HarmonyQuestion(
            HarmonyQuestionKind.SPELL_CHORD, "Which notes make $chord?",
            options, correct, explainChord(chord), chord,
        )
    }

    private fun keysContaining(random: Random): HarmonyQuestion? {
        val chord = randomChord(random)
        val rightKeys = MajorKey.all.filter { MajorKey.contains(it, chord) }
        if (rightKeys.size !in 1..4) return null
        val wrongKeys = MajorKey.all.filter { it !in rightKeys }.shuffled(random).take(6 - rightKeys.size)
        val all = (rightKeys + wrongKeys).shuffled(random)
        val correct = all.indices.filter { all[it] in rightKeys }.toSet()
        val explain = rightKeys.joinToString(", ") { "$it major (${chord.romanNumeral(it)})" }
        return HarmonyQuestion(
            HarmonyQuestionKind.KEYS_CONTAINING, "Which major keys contain $chord? Pick all that apply.",
            all.map { "$it major" }, correct,
            "$chord (${spell(chord.spelledTones)}) is in $explain.", chord,
        )
    }

    private fun modesOnRoot(random: Random): HarmonyQuestion? {
        val chord = randomChord(random)
        val root = chord.root
        val modes = Mode.entries
        val correct = modes.indices.filter { modes[it].pitchClasses(root).containsAll(chord.pitchClasses) }.toSet()
        if (correct.isEmpty()) return null
        val explain = correct.sorted().joinToString(", ") {
            val mode = modes[it]
            "$root ${mode.displayName} (from ${mode.parentKey(root)} major)"
        }
        return HarmonyQuestion(
            HarmonyQuestionKind.MODES_ON_ROOT,
            "Which modes starting on $root contain $chord? Pick all that apply.",
            modes.map { "$root ${it.displayName}" }, correct,
            "$chord (${spell(chord.spelledTones)}) fits $explain.", chord,
        )
    }

    private fun target(key: NoteName, t: Target) = Chord(key.up(Interval.majorScaleDegrees[t.degree]), t.quality)

    private fun dominantOf(chord: Chord) = Chord(chord.root.up(Interval.P5), ChordQuality.DOMINANT7)

    private fun secondaryDominant(random: Random): HarmonyQuestion {
        val key = DOMINANT_KEYS.random(random)
        val targets = TARGETS.shuffled(random)
        val t = targets[0]
        val goal = target(key, t)
        val answer = dominantOf(goal)
        val wrong = targets.drop(1).take(3).map { dominantOf(target(key, it)).toString() }
        val (options, correct) = shuffledOptions(answer.toString(), wrong, random)
        return HarmonyQuestion(
            HarmonyQuestionKind.SECONDARY_DOMINANT, "In $key major, what is V/${t.numeral}?",
            options, correct, "${t.numeral} in $key is $goal. Its V (a 5th above) is $answer.", answer,
        )
    }

    private fun dominantTarget(random: Random): HarmonyQuestion {
        val key = DOMINANT_KEYS.random(random)
        val targets = TARGETS.shuffled(random)
        val t = targets[0]
        val goal = target(key, t)
        val dom = dominantOf(goal)
        fun label(x: Target) = "${target(key, x)} (${x.numeral})"
        val (options, correct) = shuffledOptions(label(t), targets.drop(1).take(3).map(::label), random)
        return HarmonyQuestion(
            HarmonyQuestionKind.DOMINANT_TARGET, "In $key major, $dom is the V of which chord?",
            options, correct, "$dom resolves down a 5th to $goal, the ${t.numeral} of $key major.", dom,
        )
    }

    private companion object {
        /** Keys used for secondary-dominant questions (no awkward spellings). */
        val DOMINANT_KEYS = listOf(
            NoteName(Letter.C), NoteName(Letter.G), NoteName(Letter.D), NoteName(Letter.A), NoteName(Letter.E),
            NoteName(Letter.F), NoteName(Letter.B, -1), NoteName(Letter.E, -1), NoteName(Letter.A, -1),
        )

        /** Diatonic targets a secondary dominant can point at: ii, iii, IV, V, vi. */
        val TARGETS = listOf(
            Target(1, "ii", ChordQuality.MINOR), Target(2, "iii", ChordQuality.MINOR),
            Target(3, "IV", ChordQuality.MAJOR), Target(4, "V", ChordQuality.MAJOR),
            Target(5, "vi", ChordQuality.MINOR),
        )
    }
}
