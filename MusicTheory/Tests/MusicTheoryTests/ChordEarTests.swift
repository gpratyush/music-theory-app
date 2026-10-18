import XCTest
@testable import MusicTheory

final class ChordEarTests: XCTestCase {
    func testFourDistinctOptionsIncludingTheAnswer() throws {
        var rng = SeededRNG(state: 21)
        for qualities in [[ChordQuality.major, .minor], [.major, .minor, .diminished, .augmented],
                          HarmonySettings.v1Qualities] {
            let gen = ChordEarGenerator(settings: ChordEarSettings(qualities: qualities))
            for _ in 0..<200 {
                let q = try gen.next(using: &rng)
                XCTAssertEqual(q.options.count, 4)
                XCTAssertEqual(Set(q.options).count, 4)
                XCTAssertEqual(q.options.filter(q.isCorrect).count, 1)
                XCTAssertTrue(qualities.contains(q.chord.quality))
            }
        }
    }

    func testPractisedQualitiesAreOfferedFirst() throws {
        var rng = SeededRNG(state: 4)
        let gen = ChordEarGenerator(settings: ChordEarSettings(qualities: [.major, .minor, .diminished, .augmented]))
        for _ in 0..<100 {
            let q = try gen.next(using: &rng)
            XCTAssertEqual(Set(q.options), [.major, .minor, .diminished, .augmented])
        }
    }

    func testEmptySettingsThrow() {
        XCTAssertThrowsError(try ChordEarGenerator(settings: ChordEarSettings(qualities: [])).next())
    }

    func testEveryQualityHasADisplayName() {
        XCTAssertEqual(Set(ChordQuality.allCases.map(\.displayName)).count, ChordQuality.allCases.count)
    }
}
