/// The seven modes of the major scale.
public enum Mode: Int, CaseIterable, Sendable {
    case ionian, dorian, phrygian, lydian, mixolydian, aeolian, locrian

    public var name: String {
        switch self {
        case .ionian: return "Ionian"
        case .dorian: return "Dorian"
        case .phrygian: return "Phrygian"
        case .lydian: return "Lydian"
        case .mixolydian: return "Mixolydian"
        case .aeolian: return "Aeolian"
        case .locrian: return "Locrian"
        }
    }

    static let majorSteps = [0, 2, 4, 5, 7, 9, 11]

    /// Semitones above the mode's own root, e.g. Dorian = 0 2 3 5 7 9 10.
    public var steps: [Int] {
        let start = Mode.majorSteps[rawValue]
        return (0..<7).map { mod12(Mode.majorSteps[(rawValue + $0) % 7] - start) }
    }

    public func pitchClasses(root: NoteName) -> Set<Int> {
        Set(steps.map { mod12(root.pitchClass + $0) })
    }

    /// Spelled notes of this mode on `root` (one note per letter).
    public func notes(root: NoteName) -> [NoteName] {
        steps.enumerated().map { i, semis in
            let letter = root.letter.advanced(by: i)
            var acc = mod12(root.pitchClass + semis - letter.semitone)
            if acc > 6 { acc -= 12 }
            return NoteName(letter, acc)
        }
    }

    /// The major key this mode comes from, e.g. D Dorian → C major.
    public func parentKey(root: NoteName) -> NoteName {
        let degree = Interval.majorScaleDegrees[rawValue]
        return root.down(degree)
    }
}

extension Interval {
    /// Intervals from the tonic to each degree of the major scale.
    static let majorScaleDegrees: [Interval] = [
        Interval(.perfect, 1), Interval(.major, 2), Interval(.major, 3), Interval(.perfect, 4),
        Interval(.perfect, 5), Interval(.major, 6), Interval(.major, 7),
    ]
}

public enum MajorKey {
    /// The 12 major keys with their usual spellings.
    public static let all: [NoteName] = [
        NoteName(.c), NoteName(.g), NoteName(.d), NoteName(.a), NoteName(.e), NoteName(.b),
        NoteName(.f, 1), NoteName(.d, -1), NoteName(.a, -1), NoteName(.e, -1), NoteName(.b, -1), NoteName(.f),
    ]

    public static func pitchClasses(_ key: NoteName) -> Set<Int> {
        Mode.ionian.pitchClasses(root: key)
    }

    public static func contains(_ key: NoteName, _ chord: Chord) -> Bool {
        chord.pitchClasses.isSubset(of: pitchClasses(key))
    }
}

extension ChordQuality {
    /// Spelled intervals above the root, so chord tones come out with the right letters
    /// (Bbm7b5 = Bb Db Fb Ab, not Bb C# E G#).
    public var intervals: [Interval] {
        switch self {
        case .major: return [.p1, .M3, .p5]
        case .minor: return [.p1, .m3, .p5]
        case .diminished: return [.p1, .m3, .d5]
        case .augmented: return [.p1, .M3, .A5]
        case .sus2: return [.p1, .M2, .p5]
        case .sus4: return [.p1, .p4, .p5]
        case .major6: return [.p1, .M3, .p5, .M6]
        case .minor6: return [.p1, .m3, .p5, .M6]
        case .dominant7: return [.p1, .M3, .p5, .m7]
        case .major7: return [.p1, .M3, .p5, .M7]
        case .minor7: return [.p1, .m3, .p5, .m7]
        case .halfDiminished7: return [.p1, .m3, .d5, .m7]
        case .diminished7: return [.p1, .m3, .d5, .d7]
        case .minorMajor7: return [.p1, .m3, .p5, .M7]
        case .dominant7sus4: return [.p1, .p4, .p5, .m7]
        case .add2: return [.p1, .M2, .M3, .p5]
        case .add4: return [.p1, .M3, .p4, .p5]
        case .add9: return [.p1, .M3, .p5, Interval(.major, 9)]
        case .minorAdd9: return [.p1, .m3, .p5, Interval(.major, 9)]
        case .dominant9: return [.p1, .M3, .p5, .m7, Interval(.major, 9)]
        case .major9: return [.p1, .M3, .p5, .M7, Interval(.major, 9)]
        case .minor9: return [.p1, .m3, .p5, .m7, Interval(.major, 9)]
        }
    }
}

extension Interval {
    static let p1 = Interval(.perfect, 1)
    static let m2 = Interval(.minor, 2)
    static let M2 = Interval(.major, 2)
    static let m3 = Interval(.minor, 3)
    static let M3 = Interval(.major, 3)
    static let p4 = Interval(.perfect, 4)
    static let d5 = Interval(.diminished, 5)
    static let p5 = Interval(.perfect, 5)
    static let A5 = Interval(.augmented, 5)
    static let M6 = Interval(.major, 6)
    static let d7 = Interval(.diminished, 7)
    static let m7 = Interval(.minor, 7)
    static let M7 = Interval(.major, 7)
}

extension Chord {
    /// Chord tones spelled from the root, in order (no slash bass).
    public var spelledTones: [NoteName] {
        quality.intervals.map { root.up($0) }
    }
}
