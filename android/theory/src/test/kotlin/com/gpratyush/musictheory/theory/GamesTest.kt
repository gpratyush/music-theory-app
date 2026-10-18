package com.gpratyush.musictheory.theory

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarTrainingTest {
    @Test fun notesMatchTheInterval() {
        val rng = Random(3)
        val gen = EarTrainingGenerator(EarTrainingSettings(whiteKeysOnly = false, directions = EarDirection.entries.toSet()))
        repeat(500) {
            val q = gen.next(rng)
            val diff = q.second.midi - q.first.midi
            assertEquals(q.interval.semitones, abs(diff))
            assertEquals(q.direction == EarDirection.DESCENDING, diff < 0)
            assertEquals(q.first.name.pitchClass, mod12(q.first.midi))
            assertEquals(q.second.name.pitchClass, mod12(q.second.midi))
            assertTrue(abs(q.first.midi - q.reference.midi) <= 6)
            assertTrue(q.reference.name.isNatural)
        }
    }

    @Test fun optionsHaveExactlyOneRightAnswer() {
        val rng = Random(11)
        for (white in listOf(true, false)) {
            val gen = EarTrainingGenerator(EarTrainingSettings(whiteKeysOnly = white))
            repeat(300) {
                val q = gen.next(rng)
                assertEquals(4, q.firstOptions.size)
                assertEquals(1, q.firstOptions.count(q::isCorrectFirst))
                assertEquals(1, q.secondOptions.count(q::isCorrectSecond))
                assertEquals(4, q.intervalOptions.size)
                assertEquals(1, q.intervalOptions.count(q::isCorrectInterval))
                if (white) {
                    assertTrue(q.first.name.isNatural && q.second.name.isNatural)
                    assertTrue(q.firstOptions.all { it.isNatural })
                }
            }
        }
    }

    @Test fun noDirectionsThrows() {
        assertFailsWith<NoQuestionsPossible> { EarTrainingGenerator(EarTrainingSettings(directions = emptySet())).next() }
    }
}

class HarmonyTest {
    private fun chord(s: String) = ProgressionParser.parseChord(s)
    private fun spelled(s: String) = chord(s).spelledTones.map { it.toString() }

    @Test fun spelledTones() {
        assertEquals(listOf("D", "F", "A", "C"), spelled("Dm7"))
        assertEquals(listOf("Bb", "Db", "Fb", "Ab"), spelled("Bbm7b5"))
        assertEquals(listOf("B", "D", "F", "Ab"), spelled("Bdim7"))
        assertEquals(listOf("C", "E", "G#"), spelled("Caug"))
        assertEquals(listOf("C", "E", "F", "G"), spelled("Cadd4"))
    }

    @Test fun spelledTonesMatchPitchClasses() {
        for (q in ChordQuality.entries) for (root in NoteName.commonSpellings) {
            val c = Chord(root, q)
            assertEquals(c.pitchClasses, c.spelledTones.map { it.pitchClass }.toSet(), "$c")
        }
    }

    @Test fun modes() {
        val d = NoteName(Letter.D)
        assertEquals(listOf("D", "E", "F", "G", "A", "B", "C"), Mode.DORIAN.notes(d).map { it.toString() })
        assertEquals(listOf("F", "G", "A", "B", "C", "D", "E"), Mode.LYDIAN.notes(NoteName(Letter.F)).map { it.toString() })
        assertEquals(listOf("E", "F", "G", "A", "B", "C", "D"), Mode.PHRYGIAN.notes(NoteName(Letter.E)).map { it.toString() })
        assertEquals(NoteName(Letter.C), Mode.DORIAN.parentKey(d))
        assertEquals(NoteName(Letter.C), Mode.MIXOLYDIAN.parentKey(NoteName(Letter.G)))
        assertEquals(NoteName(Letter.C), Mode.AEOLIAN.parentKey(NoteName(Letter.A)))
    }

    @Test fun keysContainingDm7() {
        val keys = MajorKey.all.filter { MajorKey.contains(it, chord("Dm7")) }
        assertEquals(setOf("C", "F", "Bb"), keys.map { it.toString() }.toSet())
    }

    @Test fun modesOnDContainingDm7() {
        val rng = Random(1)
        val gen = HarmonyQuizGenerator(HarmonySettings(setOf(HarmonyQuestionKind.MODES_ON_ROOT), listOf(ChordQuality.MINOR7)))
        repeat(50) {
            val q = gen.next(rng)
            if (q.chord.root == NoteName(Letter.D)) {
                assertEquals(setOf("D Dorian", "D Phrygian", "D Aeolian"), q.correct.map { q.options[it] }.toSet())
            }
        }
    }

    @Test fun secondaryDominants() {
        val rng = Random(5)
        val gen = HarmonyQuizGenerator(HarmonySettings(setOf(HarmonyQuestionKind.SECONDARY_DOMINANT)))
        var seenVofV = false
        repeat(300) {
            val q = gen.next(rng)
            assertEquals(1, q.correct.size)
            if (q.prompt == "In C major, what is V/V?") {
                assertEquals("D7", q.options[q.correct.first()])
                seenVofV = true
            }
            if (q.prompt == "In C major, what is V/ii?") assertEquals("A7", q.options[q.correct.first()])
        }
        assertTrue(seenVofV)
    }

    @Test fun everyKindProducesValidQuestions() {
        val rng = Random(9)
        for (natural in listOf(true, false)) for (kind in HarmonyQuestionKind.entries) {
            val gen = HarmonyQuizGenerator(HarmonySettings(setOf(kind), naturalRootsOnly = natural))
            repeat(100) {
                val q = gen.next(rng)
                assertEquals(kind, q.kind)
                assertFalse(q.correct.isEmpty())
                assertEquals(q.options.size, q.options.toSet().size, "duplicate options: ${q.options}")
                assertTrue(q.correct.all { it in q.options.indices })
                if (!q.isMultiSelect) {
                    assertEquals(4, q.options.size, "$kind")
                    assertEquals(1, q.correct.size)
                }
                if (kind == HarmonyQuestionKind.KEYS_CONTAINING) assertEquals(6, q.options.size)
            }
        }
    }

    @Test fun impossibleSettingsThrow() {
        val gen = HarmonyQuizGenerator(HarmonySettings(setOf(HarmonyQuestionKind.KEYS_CONTAINING), listOf(ChordQuality.AUGMENTED)))
        assertFailsWith<NoQuestionsPossible> { gen.next() }
    }
}

class ChordEarTest {
    @Test fun fourDistinctOptionsIncludingTheAnswer() {
        val rng = Random(21)
        val sets = listOf(
            listOf(ChordQuality.MAJOR, ChordQuality.MINOR),
            listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED),
            HarmonySettings.v1Qualities,
        )
        for (qualities in sets) {
            val gen = ChordEarGenerator(ChordEarSettings(qualities))
            repeat(200) {
                val q = gen.next(rng)
                assertEquals(4, q.options.size)
                assertEquals(4, q.options.toSet().size)
                assertEquals(1, q.options.count(q::isCorrect))
                assertTrue(q.chord.quality in qualities)
            }
        }
    }

    @Test fun practisedQualitiesAreOfferedFirst() {
        val rng = Random(4)
        val four = listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED)
        val gen = ChordEarGenerator(ChordEarSettings(four))
        repeat(100) { assertEquals(four.toSet(), gen.next(rng).options.toSet()) }
    }

    @Test fun emptySettingsThrow() {
        assertFailsWith<NoQuestionsPossible> { ChordEarGenerator(ChordEarSettings(emptyList())).next() }
    }

    @Test fun everyQualityHasADisplayName() {
        assertEquals(ChordQuality.entries.size, ChordQuality.entries.map { it.displayName }.toSet().size)
    }
}
