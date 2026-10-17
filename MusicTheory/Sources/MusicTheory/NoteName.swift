/// A letter name with no accidental: C D E F G A B.
public enum Letter: Int, CaseIterable, Sendable {
    case c, d, e, f, g, a, b

    /// Semitones above C for the natural note.
    public var semitone: Int {
        switch self {
        case .c: return 0
        case .d: return 2
        case .e: return 4
        case .f: return 5
        case .g: return 7
        case .a: return 9
        case .b: return 11
        }
    }

    public var symbol: String {
        switch self {
        case .c: return "C"
        case .d: return "D"
        case .e: return "E"
        case .f: return "F"
        case .g: return "G"
        case .a: return "A"
        case .b: return "B"
        }
    }

    /// The letter `steps` letters away (wraps around).
    public func advanced(by steps: Int) -> Letter {
        Letter(rawValue: ((rawValue + steps) % 7 + 7) % 7)!
    }
}

/// A spelled note without octave, e.g. C, F#, Bb.
public struct NoteName: Hashable, Sendable, CustomStringConvertible {
    public let letter: Letter
    /// -2 = double flat, -1 = flat, 0 = natural, 1 = sharp, 2 = double sharp.
    public let accidental: Int

    public init(_ letter: Letter, _ accidental: Int = 0) {
        self.letter = letter
        self.accidental = accidental
    }

    /// 0...11, C = 0.
    public var pitchClass: Int {
        mod12(letter.semitone + accidental)
    }

    public var isNatural: Bool { accidental == 0 }

    public var description: String {
        let acc: String
        switch accidental {
        case -2: acc = "bb"
        case -1: acc = "b"
        case 1: acc = "#"
        case 2: acc = "##"
        default: acc = ""
        }
        return letter.symbol + acc
    }

    /// Same sound, regardless of spelling (Ab == G#).
    public func isEnharmonic(to other: NoteName) -> Bool {
        pitchClass == other.pitchClass
    }

    public static let naturals: [NoteName] = Letter.allCases.map { NoteName($0) }

    /// Common spellings for the 12 pitch classes, used for roots and options.
    public static let commonSpellings: [NoteName] = [
        NoteName(.c), NoteName(.c, 1), NoteName(.d, -1), NoteName(.d), NoteName(.d, 1),
        NoteName(.e, -1), NoteName(.e), NoteName(.f), NoteName(.f, 1), NoteName(.g, -1),
        NoteName(.g), NoteName(.g, 1), NoteName(.a, -1), NoteName(.a), NoteName(.a, 1),
        NoteName(.b, -1), NoteName(.b),
    ]
}

func mod12(_ x: Int) -> Int { ((x % 12) + 12) % 12 }
