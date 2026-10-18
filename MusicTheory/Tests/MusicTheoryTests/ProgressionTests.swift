import XCTest
@testable import MusicTheory

final class ProgressionTests: XCTestCase {
    func testParsesTheExampleProgression() throws {
        let chords = try ProgressionParser.parse("Cm7 - Am7 - D7 - Gmaj7")
        XCTAssertEqual(chords.map(\.description), ["Cm7", "Am7", "D7", "Gmaj7"])
    }

    func testSeparatorsAndSpellings() throws {
        let chords = try ProgressionParser.parse("C | Am, F–G7sus4  Bbmaj7 F#m7b5 Ebdim7 Abaug")
        XCTAssertEqual(chords.map(\.description),
                       ["C", "Am", "F", "G7sus4", "Bbmaj7", "F#m7b5", "Ebdim7", "Abaug"])
    }

    func testAlternateSymbols() throws {
        XCTAssertEqual(try ProgressionParser.parseChord("CΔ7").quality, .major7)
        XCTAssertEqual(try ProgressionParser.parseChord("CM7").quality, .major7)
        XCTAssertEqual(try ProgressionParser.parseChord("Cø").quality, .halfDiminished7)
        XCTAssertEqual(try ProgressionParser.parseChord("C°7").quality, .diminished7)
        XCTAssertEqual(try ProgressionParser.parseChord("Csus").quality, .sus4)
        XCTAssertEqual(try ProgressionParser.parseChord("Cadd6").quality, .major6)
        XCTAssertEqual(try ProgressionParser.parseChord("cm").root, NoteName(.c))
    }

    func testSlashChords() throws {
        let c = try ProgressionParser.parseChord("C/E")
        XCTAssertEqual(c.bass, NoteName(.e))
        XCTAssertEqual(c.description, "C/E")
        XCTAssertEqual(c.voicing.first, 36 + 4)
    }

    func testErrors() {
        XCTAssertThrowsError(try ProgressionParser.parse("   ")) { e in
            XCTAssertEqual(e as? ProgressionParseError, .empty)
        }
        XCTAssertThrowsError(try ProgressionParser.parse("C Hm7")) { e in
            XCTAssertEqual(e as? ProgressionParseError, .unknownChord("Hm7"))
        }
        XCTAssertThrowsError(try ProgressionParser.parse("Cxyz"))
        XCTAssertThrowsError(try ProgressionParser.parse("C/Q"))
    }

    func testVoicingAndPitchClasses() throws {
        let c = try ProgressionParser.parseChord("Cmaj7")
        XCTAssertEqual(c.voicing, [36, 48, 52, 55, 59])
        XCTAssertEqual(c.pitchClasses, [0, 4, 7, 11])
        let d = try ProgressionParser.parseChord("Dm7")
        XCTAssertEqual(d.voicing, [38, 50, 53, 57, 60])
    }

    func testEveryQualityHasASpellingThatRoundTrips() throws {
        for q in ChordQuality.allCases {
            let chord = Chord(root: NoteName(.e, -1), quality: q)
            XCTAssertEqual(try ProgressionParser.parseChord(chord.description), chord, "\(q)")
        }
    }

    func testRomanNumerals() throws {
        let c = NoteName(.c)
        func rn(_ s: String) throws -> String { try ProgressionParser.parseChord(s).romanNumeral(inMajorKey: c) }
        XCTAssertEqual(try rn("Dm7"), "ii7")
        XCTAssertEqual(try rn("G7"), "V7")
        XCTAssertEqual(try rn("Cmaj7"), "Imaj7")
        XCTAssertEqual(try rn("D7"), "II7")      // secondary dominant V/V
        XCTAssertEqual(try rn("Bm7b5"), "viiø7")
        XCTAssertEqual(try rn("Bb"), "bVII")
        XCTAssertEqual(try rn("Am"), "vi")
    }
}
