/// Chord qualities the app understands (harmony v1 vocabulary plus common 9ths).
public enum ChordQuality: String, CaseIterable, Sendable {
    case major, minor, diminished, augmented
    case sus2, sus4
    case major6, minor6
    case dominant7, major7, minor7, halfDiminished7, diminished7, minorMajor7, dominant7sus4
    case add2, add4, add9, minorAdd9
    case dominant9, major9, minor9

    /// Semitones above the root, in voicing order.
    public var offsets: [Int] {
        switch self {
        case .major: return [0, 4, 7]
        case .minor: return [0, 3, 7]
        case .diminished: return [0, 3, 6]
        case .augmented: return [0, 4, 8]
        case .sus2: return [0, 2, 7]
        case .sus4: return [0, 5, 7]
        case .major6: return [0, 4, 7, 9]
        case .minor6: return [0, 3, 7, 9]
        case .dominant7: return [0, 4, 7, 10]
        case .major7: return [0, 4, 7, 11]
        case .minor7: return [0, 3, 7, 10]
        case .halfDiminished7: return [0, 3, 6, 10]
        case .diminished7: return [0, 3, 6, 9]
        case .minorMajor7: return [0, 3, 7, 11]
        case .dominant7sus4: return [0, 5, 7, 10]
        case .add2: return [0, 2, 4, 7]
        case .add4: return [0, 4, 5, 7]
        case .add9: return [0, 4, 7, 14]
        case .minorAdd9: return [0, 3, 7, 14]
        case .dominant9: return [0, 4, 7, 10, 14]
        case .major9: return [0, 4, 7, 11, 14]
        case .minor9: return [0, 3, 7, 10, 14]
        }
    }

    /// How the chord is written after the root.
    public var symbol: String {
        switch self {
        case .major: return ""
        case .minor: return "m"
        case .diminished: return "dim"
        case .augmented: return "aug"
        case .sus2: return "sus2"
        case .sus4: return "sus4"
        case .major6: return "6"
        case .minor6: return "m6"
        case .dominant7: return "7"
        case .major7: return "maj7"
        case .minor7: return "m7"
        case .halfDiminished7: return "m7b5"
        case .diminished7: return "dim7"
        case .minorMajor7: return "mMaj7"
        case .dominant7sus4: return "7sus4"
        case .add2: return "add2"
        case .add4: return "add4"
        case .add9: return "add9"
        case .minorAdd9: return "madd9"
        case .dominant9: return "9"
        case .major9: return "maj9"
        case .minor9: return "m9"
        }
    }

    /// Accepted spellings for each quality (exact match, so "M7" and "m7" differ).
    static let spellings: [String: ChordQuality] = {
        var map: [String: ChordQuality] = [:]
        let table: [(ChordQuality, [String])] = [
            (.major, ["", "maj", "M", "major"]),
            (.minor, ["m", "min", "mi", "minor"]),
            (.diminished, ["dim", "°", "o"]),
            (.augmented, ["aug", "+"]),
            (.sus2, ["sus2"]),
            (.sus4, ["sus4", "sus"]),
            (.major6, ["6", "add6", "maj6"]),
            (.minor6, ["m6", "min6"]),
            (.dominant7, ["7", "dom7"]),
            (.major7, ["maj7", "M7", "Δ", "Δ7", "ma7", "Maj7"]),
            (.minor7, ["m7", "min7", "mi7"]),
            (.halfDiminished7, ["m7b5", "ø", "ø7", "min7b5", "m7♭5"]),
            (.diminished7, ["dim7", "°7", "o7"]),
            (.minorMajor7, ["mMaj7", "mM7", "mmaj7", "minMaj7"]),
            (.dominant7sus4, ["7sus4", "7sus"]),
            (.add2, ["add2", "2"]),
            (.add4, ["add4"]),
            (.add9, ["add9"]),
            (.minorAdd9, ["madd9", "madd2", "minadd9"]),
            (.dominant9, ["9"]),
            (.major9, ["maj9", "M9", "Δ9"]),
            (.minor9, ["m9", "min9"]),
        ]
        for (q, names) in table { for n in names { map[n] = q } }
        return map
    }()

    /// Major-sounding chords get an upper-case Roman numeral.
    var hasMajorThird: Bool { offsets.contains(4) || self == .sus2 || self == .sus4 || self == .dominant7sus4 }
}

public struct Chord: Hashable, Sendable, CustomStringConvertible {
    public let root: NoteName
    public let quality: ChordQuality
    /// Slash-chord bass note, e.g. the E in C/E.
    public let bass: NoteName?

    public init(root: NoteName, quality: ChordQuality, bass: NoteName? = nil) {
        self.root = root
        self.quality = quality
        self.bass = bass
    }

    public var description: String {
        "\(root)\(quality.symbol)" + (bass.map { "/\($0)" } ?? "")
    }

    /// Pitch classes in the chord (bass included).
    public var pitchClasses: Set<Int> {
        var set = Set(quality.offsets.map { mod12(root.pitchClass + $0) })
        if let bass { set.insert(bass.pitchClass) }
        return set
    }

    /// A simple piano voicing as MIDI note numbers: a bass note in octave 2
    /// and the chord in close position starting in octave 3.
    public var voicing: [Int] {
        let bassPC = (bass ?? root).pitchClass
        let bassNote = 36 + bassPC                    // C2...B2
        let chordRoot = 48 + root.pitchClass          // C3...B3
        return [bassNote] + quality.offsets.map { chordRoot + $0 }
    }

    /// Roman numeral in a major key, e.g. Dm7 in C → "ii7", D7 in C → "II7".
    public func romanNumeral(inMajorKey key: NoteName) -> String {
        let semis = mod12(root.pitchClass - key.pitchClass)
        let names = ["I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII"]
        var numeral = names[semis]
        if !quality.hasMajorThird { numeral = numeral.lowercased() }
        let suffix: String
        switch quality {
        case .major, .minor: suffix = ""
        case .diminished: suffix = "°"
        case .augmented: suffix = "+"
        case .halfDiminished7: suffix = "ø7"
        case .diminished7: suffix = "°7"
        case .minor7: suffix = "7"
        case .minor6: suffix = "6"
        case .minor9: suffix = "9"
        case .minorAdd9: suffix = "add9"
        default: suffix = quality.symbol
        }
        return numeral + suffix + (bass.map { "/\($0)" } ?? "")
    }
}
