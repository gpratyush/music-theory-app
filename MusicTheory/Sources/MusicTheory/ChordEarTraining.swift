extension ChordQuality {
    /// Spoken name, used as the answer label in Chord Ear Training.
    public var displayName: String {
        switch self {
        case .major: return "Major"
        case .minor: return "Minor"
        case .diminished: return "Diminished"
        case .augmented: return "Augmented"
        case .sus2: return "Sus2"
        case .sus4: return "Sus4"
        case .major6: return "Major 6th"
        case .minor6: return "Minor 6th"
        case .dominant7: return "Dominant 7th"
        case .major7: return "Major 7th"
        case .minor7: return "Minor 7th"
        case .halfDiminished7: return "Half-diminished 7th"
        case .diminished7: return "Diminished 7th"
        case .minorMajor7: return "Minor-major 7th"
        case .dominant7sus4: return "7sus4"
        case .add2: return "Add2"
        case .add4: return "Add4"
        case .add9: return "Add9"
        case .minorAdd9: return "Minor add9"
        case .dominant9: return "Dominant 9th"
        case .major9: return "Major 9th"
        case .minor9: return "Minor 9th"
        }
    }
}

public struct ChordEarSettings: Sendable, Equatable {
    public var qualities: [ChordQuality]
    public init(qualities: [ChordQuality] = [.major, .minor, .diminished, .augmented]) {
        self.qualities = qualities
    }
}

/// "Listen: what kind of chord was that?"
public struct ChordEarQuestion: Sendable {
    public let chord: Chord
    public let options: [ChordQuality]
    public func isCorrect(_ q: ChordQuality) -> Bool { q == chord.quality }
}

public struct ChordEarGenerator: Sendable {
    public var settings: ChordEarSettings

    public init(settings: ChordEarSettings = ChordEarSettings()) {
        self.settings = settings
    }

    public func next() throws -> ChordEarQuestion {
        var rng = SystemRandomNumberGenerator()
        return try next(using: &rng)
    }

    public func next<R: RandomNumberGenerator>(using rng: inout R) throws -> ChordEarQuestion {
        guard let quality = settings.qualities.randomElement(using: &rng) else {
            throw IntervalQuizError.noQuestionsPossible
        }
        let root = NoteName.commonSpellings.randomElement(using: &rng)!
        let chord = Chord(root: root, quality: quality)

        // Wrong answers come from the qualities being practised first, then the closest
        // others (same number of notes), so there are always four choices.
        let practised = settings.qualities.filter { $0 != quality }.shuffled(using: &rng)
        let others = HarmonySettings.v1Qualities
            .filter { $0 != quality && !settings.qualities.contains($0) }
            .shuffled(using: &rng)
            .sorted { abs($0.offsets.count - quality.offsets.count) < abs($1.offsets.count - quality.offsets.count) }
        let wrong = Array((practised + others).prefix(3))
        return ChordEarQuestion(chord: chord, options: ([quality] + wrong).shuffled(using: &rng))
    }
}
