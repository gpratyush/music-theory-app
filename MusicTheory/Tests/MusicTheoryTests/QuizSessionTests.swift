import XCTest
import Foundation
@testable import MusicTheory

final class QuizSessionTests: XCTestCase {
    let t0 = Date(timeIntervalSince1970: 1_000_000)

    func testPracticeHasNoClock() {
        let s = QuizSession(mode: .practice, startedAt: t0)
        XCTAssertNil(s.remaining(at: t0.addingTimeInterval(9999)))
        XCTAssertFalse(s.isOver(at: t0.addingTimeInterval(9999)))
    }

    func testTimedCountsDown() {
        let s = QuizSession(mode: .timed(seconds: 60), startedAt: t0)
        XCTAssertEqual(s.remaining(at: t0), 60)
        XCTAssertEqual(s.remaining(at: t0.addingTimeInterval(0.5)), 60)
        XCTAssertEqual(s.remaining(at: t0.addingTimeInterval(59.2)), 1)
        XCTAssertEqual(s.remaining(at: t0.addingTimeInterval(61)), 0)
        XCTAssertTrue(s.isOver(at: t0.addingTimeInterval(60)))
    }

    func testScoringStopsWhenTimeIsUp() {
        var s = QuizSession(mode: .timed(seconds: 60), startedAt: t0)
        s.record(correct: true, at: t0.addingTimeInterval(10))
        s.record(correct: false, at: t0.addingTimeInterval(20))
        s.record(correct: true, at: t0.addingTimeInterval(61))
        XCTAssertEqual(s.correct, 1)
        XCTAssertEqual(s.answered, 2)
        XCTAssertEqual(s.accuracy, 50)
    }

    func testAccuracyBeforeAnswers() {
        XCTAssertNil(QuizSession(mode: .practice).accuracy)
    }
}
