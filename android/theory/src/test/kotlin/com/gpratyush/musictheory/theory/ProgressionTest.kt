package com.gpratyush.musictheory.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgressionTest {
    private fun chord(s: String) = ProgressionParser.parseChord(s)

    @Test fun parsesTheExampleProgression() {
        assertEquals(listOf("Cm7", "Am7", "D7", "Gmaj7"), ProgressionParser.parse("Cm7 - Am7 - D7 - Gmaj7").map { it.toString() })
    }

    @Test fun separatorsAndSpellings() {
        assertEquals(
            listOf("C", "Am", "F", "G7sus4", "Bbmaj7", "F#m7b5", "Ebdim7", "Abaug"),
            ProgressionParser.parse("C | Am, F–G7sus4  Bbmaj7 F#m7b5 Ebdim7 Abaug").map { it.toString() },
        )
    }

    @Test fun alternateSymbols() {
        assertEquals(ChordQuality.MAJOR7, chord("CΔ7").quality)
        assertEquals(ChordQuality.MAJOR7, chord("CM7").quality)
        assertEquals(ChordQuality.HALF_DIMINISHED7, chord("Cø").quality)
        assertEquals(ChordQuality.DIMINISHED7, chord("C°7").quality)
        assertEquals(ChordQuality.SUS4, chord("Csus").quality)
        assertEquals(ChordQuality.MAJOR6, chord("Cadd6").quality)
        assertEquals(NoteName(Letter.C), chord("cm").root)
    }

    @Test fun slashChords() {
        val c = chord("C/E")
        assertEquals(NoteName(Letter.E), c.bass)
        assertEquals("C/E", c.toString())
        assertEquals(36 + 4, c.voicing.first())
    }

    @Test fun errors() {
        assertEquals(ProgressionParseError.Empty, assertFailsWith<ProgressionParseError> { ProgressionParser.parse("   ") })
        assertEquals(
            ProgressionParseError.UnknownChord("Hm7"),
            assertFailsWith<ProgressionParseError> { ProgressionParser.parse("C Hm7") },
        )
        assertFailsWith<ProgressionParseError> { ProgressionParser.parse("Cxyz") }
        assertFailsWith<ProgressionParseError> { ProgressionParser.parse("C/Q") }
    }

    @Test fun voicingAndPitchClasses() {
        val c = chord("Cmaj7")
        assertEquals(listOf(36, 48, 52, 55, 59), c.voicing)
        assertEquals(setOf(0, 4, 7, 11), c.pitchClasses)
        assertEquals(listOf(38, 50, 53, 57, 60), chord("Dm7").voicing)
    }

    @Test fun everyQualityHasASpellingThatRoundTrips() {
        for (q in ChordQuality.entries) {
            val c = Chord(NoteName(Letter.E, -1), q)
            assertEquals(c, chord(c.toString()), "$q")
        }
    }

    @Test fun romanNumerals() {
        val c = NoteName(Letter.C)
        fun rn(s: String) = chord(s).romanNumeral(c)
        assertEquals("ii7", rn("Dm7"))
        assertEquals("V7", rn("G7"))
        assertEquals("Imaj7", rn("Cmaj7"))
        assertEquals("II7", rn("D7"))
        assertEquals("viiø7", rn("Bm7b5"))
        assertEquals("bVII", rn("Bb"))
        assertEquals("vi", rn("Am"))
    }

    @Test fun offsetsMatchTheSwiftTable() {
        assertEquals(listOf(0, 4, 7, 10, 14), ChordQuality.DOMINANT9.offsets)
        assertEquals(listOf(0, 3, 6, 9), ChordQuality.DIMINISHED7.offsets)
        assertEquals(listOf(0, 4, 8), ChordQuality.AUGMENTED.offsets)
    }
}
