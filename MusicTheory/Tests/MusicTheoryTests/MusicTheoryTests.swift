import XCTest
@testable import MusicTheory

/// Deterministic RNG so quiz tests are repeatable.
struct SeededRNG: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

final class IntervalTests: XCTestCase {
    let c = NoteName(.c)

    func testSemitones() {
        XCTAssertEqual(Interval(.minor, 3).semitones, 3)
        XCTAssertEqual(Interval(.perfect, 5).semitones, 7)
        XCTAssertEqual(Interval(.augmented, 4).semitones, 6)
        XCTAssertEqual(Interval(.diminished, 5).semitones, 6)
        XCTAssertEqual(Interval(.perfect, 8).semitones, 12)
        XCTAssertEqual(Interval(.major, 9).semitones, 14)
        XCTAssertEqual(Interval(.perfect, 11).semitones, 17)
        XCTAssertEqual(Interval(.major, 13).semitones, 21)
    }

    func testNames() {
        XCTAssertEqual(Interval(.minor, 3).name, "Minor 3rd")
        XCTAssertEqual(Interval(.perfect, 8).name, "Octave")
        XCTAssertEqual(Interval(.augmented, 11).name, "Augmented 11th")
        XCTAssertEqual(Interval(.minor, 2).shortName, "m2")
    }

    func testUpIsSpelledCorrectly() {
        XCTAssertEqual(c.up(Interval(.minor, 6)).description, "Ab")
        XCTAssertEqual(c.up(Interval(.augmented, 5)).description, "G#")
        XCTAssertEqual(c.up(Interval(.major, 3)).description, "E")
        XCTAssertEqual(NoteName(.e).up(Interval(.major, 3)).description, "G#")
        XCTAssertEqual(NoteName(.b).up(Interval(.perfect, 5)).description, "F#")
        XCTAssertEqual(NoteName(.d).up(Interval(.minor, 7)).description, "C")
        XCTAssertEqual(c.up(Interval(.major, 9)).description, "D")
    }

    func testDown() {
        // "C is the perfect 5th of what?" → F
        XCTAssertEqual(c.down(Interval(.perfect, 5)).description, "F")
        XCTAssertEqual(c.down(Interval(.major, 3)).description, "Ab")
        XCTAssertEqual(NoteName(.e).down(Interval(.major, 3)).description, "C")
    }

    func testIntervalBetween() {
        XCTAssertEqual(c.interval(to: NoteName(.e)), Interval(.major, 3))
        XCTAssertEqual(c.interval(to: NoteName(.a, -1)), Interval(.minor, 6))
        XCTAssertEqual(c.interval(to: NoteName(.f, 1)), Interval(.augmented, 4))
        XCTAssertEqual(NoteName(.b).interval(to: NoteName(.f)), Interval(.diminished, 5))
    }

    func testUpThenBetweenRoundTrips() {
        for root in NoteName.commonSpellings {
            for iv in Interval.simple where iv.number < 8 {
                XCTAssertEqual(root.interval(to: root.up(iv)), iv, "\(root) + \(iv)")
            }
        }
    }

    func testSwaraLabels() {
        XCTAssertEqual(IntervalNotation.swara.label(for: Interval(.perfect, 5)), "Pa")
        XCTAssertEqual(IntervalNotation.swara.label(for: Interval(.minor, 3)), "komal Ga")
        XCTAssertEqual(IntervalNotation.swara.label(for: Interval(.perfect, 8)), "Sa'")
        XCTAssertEqual(IntervalNotation.western.label(for: Interval(.perfect, 5)), "Perfect 5th")
    }
}

final class IntervalQuizTests: XCTestCase {
    func testWhiteKeysOnlyNeverShowsAccidentals() throws {
        var rng = SeededRNG(state: 1)
        let gen = IntervalQuizGenerator(settings: IntervalQuizSettings(whiteKeysOnly: true))
        for _ in 0..<500 {
            let q = try gen.next(using: &rng)
            XCTAssertTrue(q.given.isNatural)
            if let other = q.other { XCTAssertTrue(other.isNatural) }
            for opt in q.options {
                if case let .note(n) = opt { XCTAssertTrue(n.isNatural, "\(n)") }
            }
        }
    }

    func testFourDistinctOptionsWithExactlyOneCorrect() throws {
        var rng = SeededRNG(state: 42)
        for white in [true, false] {
            let gen = IntervalQuizGenerator(settings: IntervalQuizSettings(whiteKeysOnly: white, includeCompound: true))
            for _ in 0..<500 {
                let q = try gen.next(using: &rng)
                XCTAssertEqual(q.options.count, 4)
                XCTAssertEqual(q.options.filter(q.isCorrect).count, 1, "\(q)")
                XCTAssertTrue(q.options.contains(q.correct))
            }
        }
    }

    func testAnswersAreRight() throws {
        var rng = SeededRNG(state: 7)
        let gen = IntervalQuizGenerator(settings: IntervalQuizSettings(whiteKeysOnly: false))
        for _ in 0..<300 {
            let q = try gen.next(using: &rng)
            switch (q.kind, q.correct) {
            case let (.noteAbove, .note(n)): XCTAssertEqual(q.given.up(q.interval), n)
            case let (.rootBelow, .note(n)): XCTAssertEqual(n.up(q.interval).pitchClass, q.given.pitchClass)
            case let (.nameInterval, .interval(iv)): XCTAssertEqual(q.given.interval(to: q.other!), iv)
            default: XCTFail("kind and answer type don't match")
            }
        }
    }

    func testEnharmonicAnswersAccepted() {
        let q = IntervalQuestion(
            kind: .noteAbove, given: NoteName(.c), other: nil, interval: Interval(.minor, 6),
            options: [], correct: .note(NoteName(.a, -1)))
        XCTAssertTrue(q.isCorrect(.note(NoteName(.g, 1))))
        XCTAssertFalse(q.isCorrect(.note(NoteName(.a))))
    }

    func testPrompts() {
        let q = IntervalQuestion(
            kind: .rootBelow, given: NoteName(.c), other: nil, interval: Interval(.perfect, 5),
            options: [], correct: .note(NoteName(.f)))
        XCTAssertEqual(q.prompt(label: IntervalNotation.western.label), "C is the Perfect 5th of which note?")
        XCTAssertEqual(q.prompt(label: IntervalNotation.swara.label), "C is the Pa of which note?")
    }

    func testEmptySettingsThrow() {
        let gen = IntervalQuizGenerator(settings: IntervalQuizSettings(intervals: []))
        XCTAssertThrowsError(try gen.next())
    }
}
