package com.gpratyush.musictheory.theory

import com.gpratyush.musictheory.theory.IntervalQuality.AUGMENTED
import com.gpratyush.musictheory.theory.IntervalQuality.DIMINISHED
import com.gpratyush.musictheory.theory.IntervalQuality.MAJOR
import com.gpratyush.musictheory.theory.IntervalQuality.MINOR
import com.gpratyush.musictheory.theory.IntervalQuality.PERFECT
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntervalTest {
    private val c = NoteName(Letter.C)

    @Test fun semitones() {
        assertEquals(3, Interval(MINOR, 3).semitones)
        assertEquals(7, Interval(PERFECT, 5).semitones)
        assertEquals(6, Interval(AUGMENTED, 4).semitones)
        assertEquals(6, Interval(DIMINISHED, 5).semitones)
        assertEquals(12, Interval(PERFECT, 8).semitones)
        assertEquals(14, Interval(MAJOR, 9).semitones)
        assertEquals(17, Interval(PERFECT, 11).semitones)
        assertEquals(21, Interval(MAJOR, 13).semitones)
    }

    @Test fun names() {
        assertEquals("Minor 3rd", Interval(MINOR, 3).name)
        assertEquals("Octave", Interval(PERFECT, 8).name)
        assertEquals("Augmented 11th", Interval(AUGMENTED, 11).name)
        assertEquals("m2", Interval(MINOR, 2).shortName)
    }

    @Test fun upIsSpelledCorrectly() {
        assertEquals("Ab", c.up(Interval(MINOR, 6)).toString())
        assertEquals("G#", c.up(Interval(AUGMENTED, 5)).toString())
        assertEquals("E", c.up(Interval(MAJOR, 3)).toString())
        assertEquals("G#", NoteName(Letter.E).up(Interval(MAJOR, 3)).toString())
        assertEquals("F#", NoteName(Letter.B).up(Interval(PERFECT, 5)).toString())
        assertEquals("C", NoteName(Letter.D).up(Interval(MINOR, 7)).toString())
        assertEquals("D", c.up(Interval(MAJOR, 9)).toString())
    }

    @Test fun down() {
        assertEquals("F", c.down(Interval(PERFECT, 5)).toString())
        assertEquals("Ab", c.down(Interval(MAJOR, 3)).toString())
        assertEquals("C", NoteName(Letter.E).down(Interval(MAJOR, 3)).toString())
    }

    @Test fun intervalBetween() {
        assertEquals(Interval(MAJOR, 3), c.intervalTo(NoteName(Letter.E)))
        assertEquals(Interval(MINOR, 6), c.intervalTo(NoteName(Letter.A, -1)))
        assertEquals(Interval(AUGMENTED, 4), c.intervalTo(NoteName(Letter.F, 1)))
        assertEquals(Interval(DIMINISHED, 5), NoteName(Letter.B).intervalTo(NoteName(Letter.F)))
    }

    @Test fun upThenBetweenRoundTrips() {
        for (root in NoteName.commonSpellings) for (iv in Interval.simple.filter { it.number < 8 }) {
            assertEquals(iv, root.intervalTo(root.up(iv)), "$root + $iv")
        }
    }

    @Test fun swaraLabels() {
        assertEquals("Pa", IntervalNotation.SWARA.label(Interval(PERFECT, 5)))
        assertEquals("komal Ga", IntervalNotation.SWARA.label(Interval(MINOR, 3)))
        assertEquals("Sa'", IntervalNotation.SWARA.label(Interval(PERFECT, 8)))
        assertEquals("Perfect 5th", IntervalNotation.WESTERN.label(Interval(PERFECT, 5)))
    }
}

class IntervalQuizTest {
    @Test fun whiteKeysOnlyNeverShowsAccidentals() {
        val rng = Random(1)
        val gen = IntervalQuizGenerator(IntervalQuizSettings(whiteKeysOnly = true))
        repeat(500) {
            val q = gen.next(rng)
            assertTrue(q.given.isNatural)
            q.other?.let { assertTrue(it.isNatural) }
            for (opt in q.options) if (opt is QuizAnswer.Note) assertTrue(opt.note.isNatural, "${opt.note}")
        }
    }

    @Test fun fourDistinctOptionsWithExactlyOneCorrect() {
        val rng = Random(42)
        for (white in listOf(true, false)) {
            val gen = IntervalQuizGenerator(IntervalQuizSettings(whiteKeysOnly = white, includeCompound = true))
            repeat(500) {
                val q = gen.next(rng)
                assertEquals(4, q.options.size)
                assertEquals(1, q.options.count(q::isCorrect), "$q")
                assertTrue(q.correct in q.options)
            }
        }
    }

    @Test fun answersAreRight() {
        val rng = Random(7)
        val gen = IntervalQuizGenerator(IntervalQuizSettings(whiteKeysOnly = false))
        repeat(300) {
            val q = gen.next(rng)
            val ans = q.correct
            when (q.kind) {
                IntervalQuestionKind.NOTE_ABOVE -> assertEquals(q.given.up(q.interval), (ans as QuizAnswer.Note).note)
                IntervalQuestionKind.ROOT_BELOW ->
                    assertEquals(q.given.pitchClass, (ans as QuizAnswer.Note).note.up(q.interval).pitchClass)
                IntervalQuestionKind.NAME_INTERVAL -> assertEquals(q.given.intervalTo(q.other!!), (ans as QuizAnswer.Iv).interval)
            }
        }
    }

    @Test fun enharmonicAnswersAccepted() {
        val q = IntervalQuestion(
            IntervalQuestionKind.NOTE_ABOVE, NoteName(Letter.C), null, Interval(MINOR, 6),
            emptyList(), QuizAnswer.Note(NoteName(Letter.A, -1)),
        )
        assertTrue(q.isCorrect(QuizAnswer.Note(NoteName(Letter.G, 1))))
        assertFalse(q.isCorrect(QuizAnswer.Note(NoteName(Letter.A))))
    }

    @Test fun prompts() {
        val q = IntervalQuestion(
            IntervalQuestionKind.ROOT_BELOW, NoteName(Letter.C), null, Interval(PERFECT, 5),
            emptyList(), QuizAnswer.Note(NoteName(Letter.F)),
        )
        assertEquals("C is the Perfect 5th of which note?", q.prompt(IntervalNotation.WESTERN::label))
        assertEquals("C is the Pa of which note?", q.prompt(IntervalNotation.SWARA::label))
    }

    @Test fun emptySettingsThrow() {
        assertFailsWith<NoQuestionsPossible> { IntervalQuizGenerator(IntervalQuizSettings(intervals = emptyList())).next() }
    }
}

class QuizSessionTest {
    private val t0 = 1_000_000_000L

    @Test fun practiceHasNoClock() {
        val s = QuizSession(QuizMode.Practice, t0)
        assertEquals(null, s.remaining(t0 + 9_999_000))
        assertFalse(s.isOver(t0 + 9_999_000))
    }

    @Test fun timedCountsDown() {
        val s = QuizSession(QuizMode.Timed(60), t0)
        assertEquals(60, s.remaining(t0))
        assertEquals(60, s.remaining(t0 + 500))
        assertEquals(1, s.remaining(t0 + 59_200))
        assertEquals(0, s.remaining(t0 + 61_000))
        assertTrue(s.isOver(t0 + 60_000))
    }

    @Test fun scoringStopsWhenTimeIsUp() {
        val s = QuizSession(QuizMode.Timed(60), t0)
            .record(true, t0 + 10_000)
            .record(false, t0 + 20_000)
            .record(true, t0 + 61_000)
        assertEquals(1, s.correct)
        assertEquals(2, s.answered)
        assertEquals(50, s.accuracy)
    }

    @Test fun accuracyBeforeAnswers() {
        assertEquals(null, QuizSession(QuizMode.Practice).accuracy)
    }
}
