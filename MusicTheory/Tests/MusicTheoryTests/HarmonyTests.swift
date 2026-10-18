import XCTest
@testable import MusicTheory

final class HarmonyTests: XCTestCase {
    func chord(_ s: String) throws -> Chord { try ProgressionParser.parseChord(s) }

    func testSpelledTones() throws {
        XCTAssertEqual(try chord("Dm7").spelledTones.map(\.description), ["D", "F", "A", "C"])
        XCTAssertEqual(try chord("Bbm7b5").spelledTones.map(\.description), ["Bb", "Db", "Fb", "Ab"])
        XCTAssertEqual(try chord("Bdim7").spelledTones.map(\.description), ["B", "D", "F", "Ab"])
        XCTAssertEqual(try chord("Caug").spelledTones.map(\.description), ["C", "E", "G#"])
        XCTAssertEqual(try chord("Cadd4").spelledTones.map(\.description), ["C", "E", "F", "G"])
    }

    func testSpelledTonesMatchPitchClasses() {
        for q in ChordQuality.allCases {
            for root in NoteName.commonSpellings {
                let c = Chord(root: root, quality: q)
                XCTAssertEqual(Set(c.spelledTones.map(\.pitchClass)), c.pitchClasses, "\(c)")
            }
        }
    }

    func testModes() {
        let d = NoteName(.d)
        XCTAssertEqual(Mode.dorian.notes(root: d).map(\.description), ["D", "E", "F", "G", "A", "B", "C"])
        XCTAssertEqual(Mode.lydian.notes(root: NoteName(.f)).map(\.description), ["F", "G", "A", "B", "C", "D", "E"])
        XCTAssertEqual(Mode.phrygian.notes(root: NoteName(.e)).map(\.description), ["E", "F", "G", "A", "B", "C", "D"])
        XCTAssertEqual(Mode.dorian.parentKey(root: d), NoteName(.c))
        XCTAssertEqual(Mode.mixolydian.parentKey(root: NoteName(.g)), NoteName(.c))
        XCTAssertEqual(Mode.aeolian.parentKey(root: NoteName(.a)), NoteName(.c))
    }

    func testKeysContainingDm7() throws {
        let keys = MajorKey.all.filter { MajorKey.contains($0, try! chord("Dm7")) }
        XCTAssertEqual(Set(keys.map(\.description)), ["C", "F", "Bb"])
    }

    func testModesOnDContainingDm7() throws {
        var rng = SeededRNG(state: 1)
        let gen = HarmonyQuizGenerator(settings: HarmonySettings(kinds: [.modesOnRoot], qualities: [.minor7]))
        for _ in 0..<50 {
            let q = try gen.next(using: &rng)
            guard q.chord.root == NoteName(.d) else { continue }
            let picked = Set(q.correct.map { q.options[$0] })
            XCTAssertEqual(picked, ["D Dorian", "D Phrygian", "D Aeolian"])
        }
    }

    func testSecondaryDominants() throws {
        var rng = SeededRNG(state: 5)
        let gen = HarmonyQuizGenerator(settings: HarmonySettings(kinds: [.secondaryDominant]))
        var seenVofV = false
        for _ in 0..<300 {
            let q = try gen.next(using: &rng)
            XCTAssertEqual(q.correct.count, 1)
            if q.prompt == "In C major, what is V/V?" {
                XCTAssertEqual(q.options[q.correct.first!], "D7")
                seenVofV = true
            }
            if q.prompt == "In C major, what is V/ii?" {
                XCTAssertEqual(q.options[q.correct.first!], "A7")
            }
        }
        XCTAssertTrue(seenVofV)
    }

    func testEveryKindProducesValidQuestions() throws {
        var rng = SeededRNG(state: 9)
        for natural in [true, false] {
            for kind in HarmonyQuestionKind.allCases {
                let gen = HarmonyQuizGenerator(settings: HarmonySettings(kinds: [kind], naturalRootsOnly: natural))
                for _ in 0..<100 {
                    let q = try gen.next(using: &rng)
                    XCTAssertEqual(q.kind, kind)
                    XCTAssertFalse(q.correct.isEmpty)
                    XCTAssertEqual(Set(q.options).count, q.options.count, "duplicate options: \(q.options)")
                    XCTAssertTrue(q.correct.allSatisfy { q.options.indices.contains($0) })
                    if !q.isMultiSelect {
                        XCTAssertEqual(q.options.count, 4, "\(kind)")
                        XCTAssertEqual(q.correct.count, 1)
                    }
                    if kind == .keysContaining {
                        XCTAssertEqual(q.options.count, 6)
                    }
                }
            }
        }
    }

    func testImpossibleSettingsThrow() {
        let gen = HarmonyQuizGenerator(settings: HarmonySettings(kinds: [.keysContaining], qualities: [.augmented]))
        XCTAssertThrowsError(try gen.next())
    }
}
