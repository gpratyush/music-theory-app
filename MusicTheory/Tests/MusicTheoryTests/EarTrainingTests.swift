import XCTest
@testable import MusicTheory

final class EarTrainingTests: XCTestCase {
    func testNotesMatchTheInterval() throws {
        var rng = SeededRNG(state: 3)
        let gen = EarTrainingGenerator(settings: EarTrainingSettings(
            whiteKeysOnly: false, directions: Set(EarDirection.allCases)))
        for _ in 0..<500 {
            let q = try gen.next(using: &rng)
            let diff = q.second.midi - q.first.midi
            XCTAssertEqual(abs(diff), q.interval.semitones)
            XCTAssertEqual(diff < 0, q.direction == .descending)
            XCTAssertEqual(mod12(q.first.midi), q.first.name.pitchClass)
            XCTAssertEqual(mod12(q.second.midi), q.second.name.pitchClass)
            XCTAssertLessThanOrEqual(abs(q.first.midi - q.reference.midi), 6)
            XCTAssertTrue(q.reference.name.isNatural)
        }
    }

    func testOptionsHaveExactlyOneRightAnswer() throws {
        var rng = SeededRNG(state: 11)
        for white in [true, false] {
            let gen = EarTrainingGenerator(settings: EarTrainingSettings(whiteKeysOnly: white))
            for _ in 0..<300 {
                let q = try gen.next(using: &rng)
                XCTAssertEqual(q.firstOptions.count, 4)
                XCTAssertEqual(q.firstOptions.filter(q.isCorrectFirst).count, 1)
                XCTAssertEqual(q.secondOptions.filter(q.isCorrectSecond).count, 1)
                XCTAssertEqual(q.intervalOptions.count, 4)
                XCTAssertEqual(q.intervalOptions.filter(q.isCorrectInterval).count, 1)
                if white {
                    XCTAssertTrue(q.first.name.isNatural && q.second.name.isNatural)
                    XCTAssertTrue(q.firstOptions.allSatisfy(\.isNatural))
                }
            }
        }
    }

    func testNoDirectionsThrows() {
        let gen = EarTrainingGenerator(settings: EarTrainingSettings(directions: []))
        XCTAssertThrowsError(try gen.next())
    }
}
