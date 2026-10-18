public enum HarmonyQuestionKind: String, CaseIterable, Sendable {
    /// "Which chord is D F A C?" → Dm7
    case nameChord
    /// "Which notes make Dm7?" → D F A C
    case spellChord
    /// "Which major keys contain Dm7?" → C, F, Bb (pick all)
    case keysContaining
    /// "Which modes on D contain Dm7?" → Dorian, Phrygian, Aeolian (pick all)
    case modesOnRoot
    /// "In C major, what is V/V?" → D7
    case secondaryDominant
    /// "In C major, A7 is the V of which chord?" → Dm (ii)
    case dominantTarget

    public var displayName: String {
        switch self {
        case .nameChord: return "Name the chord"
        case .spellChord: return "Spell the chord"
        case .keysContaining: return "Which keys contain it"
        case .modesOnRoot: return "Which modes contain it"
        case .secondaryDominant: return "Find the secondary dominant"
        case .dominantTarget: return "Where a secondary dominant goes"
        }
    }
}

public struct HarmonySettings: Sendable, Equatable {
    public var kinds: Set<HarmonyQuestionKind>
    public var qualities: [ChordQuality]
    /// Roots C D E F G A B only.
    public var naturalRootsOnly: Bool

    public init(
        kinds: Set<HarmonyQuestionKind> = Set(HarmonyQuestionKind.allCases),
        qualities: [ChordQuality] = HarmonySettings.v1Qualities,
        naturalRootsOnly: Bool = true
    ) {
        self.kinds = kinds
        self.qualities = qualities
        self.naturalRootsOnly = naturalRootsOnly
    }

    /// Triads, 7ths, suspended and add chords within the octave.
    public static let v1Qualities: [ChordQuality] = [
        .major, .minor, .diminished, .augmented,
        .dominant7, .major7, .minor7, .halfDiminished7, .diminished7, .minorMajor7,
        .sus2, .sus4, .dominant7sus4,
        .add2, .add4, .major6, .minor6,
    ]
}

/// A harmony question. Options are labels; some questions have several right answers.
public struct HarmonyQuestion: Sendable {
    public let kind: HarmonyQuestionKind
    public let prompt: String
    public let options: [String]
    public let correct: Set<Int>
    /// More than one option may be right; the learner picks all that apply.
    public var isMultiSelect: Bool { kind == .keysContaining || kind == .modesOnRoot }
    /// Shown after answering: why the answer is what it is.
    public let explanation: String
    /// The chord the question is about, so the app can play it.
    public let chord: Chord

    public func isCorrect(_ picked: Set<Int>) -> Bool { picked == correct }
}

public struct HarmonyQuizGenerator: Sendable {
    public var settings: HarmonySettings

    public init(settings: HarmonySettings = HarmonySettings()) {
        self.settings = settings
    }

    private var roots: [NoteName] {
        settings.naturalRootsOnly ? NoteName.naturals : NoteName.commonSpellings
    }

    /// Keys used for secondary-dominant questions (no awkward spellings).
    static let dominantKeys: [NoteName] = [
        NoteName(.c), NoteName(.g), NoteName(.d), NoteName(.a), NoteName(.e),
        NoteName(.f), NoteName(.b, -1), NoteName(.e, -1), NoteName(.a, -1),
    ]

    public func next() throws -> HarmonyQuestion {
        var rng = SystemRandomNumberGenerator()
        return try next(using: &rng)
    }

    public func next<R: RandomNumberGenerator>(using rng: inout R) throws -> HarmonyQuestion {
        guard !settings.qualities.isEmpty else { throw IntervalQuizError.noQuestionsPossible }
        // Some kinds can't be asked about some chords (e.g. no major key has an augmented
        // triad), so try a few picks before giving up.
        let kinds = HarmonyQuestionKind.allCases.filter(settings.kinds.contains)
        for _ in 0..<50 {
            guard let kind = kinds.randomElement(using: &rng) else { break }
            if let q = make(kind, using: &rng) { return q }
        }
        throw IntervalQuizError.noQuestionsPossible
    }

    func make<R: RandomNumberGenerator>(_ kind: HarmonyQuestionKind, using rng: inout R) -> HarmonyQuestion? {
        switch kind {
        case .nameChord: return nameChord(using: &rng)
        case .spellChord: return spellChord(using: &rng)
        case .keysContaining: return keysContaining(using: &rng)
        case .modesOnRoot: return modesOnRoot(using: &rng)
        case .secondaryDominant: return secondaryDominant(using: &rng)
        case .dominantTarget: return dominantTarget(using: &rng)
        }
    }

    private func randomChord<R: RandomNumberGenerator>(using rng: inout R) -> Chord {
        Chord(root: roots.randomElement(using: &rng)!, quality: settings.qualities.randomElement(using: &rng)!)
    }

    /// Up to 3 other qualities on the same root that don't share the same notes.
    private func otherChords<R: RandomNumberGenerator>(than chord: Chord, using rng: inout R) -> [Chord] {
        var seen: Set<Set<Int>> = [chord.pitchClasses]
        var out: [Chord] = []
        for q in HarmonySettings.v1Qualities.shuffled(using: &rng) {
            let c = Chord(root: chord.root, quality: q)
            // Prefer chords with the same number of notes: they're the believable mistakes.
            guard c.quality.offsets.count == chord.quality.offsets.count || out.count >= 2 else { continue }
            if seen.insert(c.pitchClasses).inserted { out.append(c) }
            if out.count == 3 { break }
        }
        // Not enough same-size chords: fill from any size.
        for q in HarmonySettings.v1Qualities.shuffled(using: &rng) where out.count < 3 {
            let c = Chord(root: chord.root, quality: q)
            if seen.insert(c.pitchClasses).inserted { out.append(c) }
        }
        return out
    }

    private func shuffledOptions<R: RandomNumberGenerator>(
        correct: String, wrong: [String], using rng: inout R
    ) -> ([String], Set<Int>) {
        let all = ([correct] + wrong).shuffled(using: &rng)
        return (all, [all.firstIndex(of: correct)!])
    }

    private func spell(_ notes: [NoteName]) -> String {
        notes.map(\.description).joined(separator: " ")
    }

    func nameChord<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let chord = randomChord(using: &rng)
        let (options, correct) = shuffledOptions(
            correct: chord.description,
            wrong: otherChords(than: chord, using: &rng).map(\.description),
            using: &rng)
        return HarmonyQuestion(
            kind: .nameChord,
            prompt: "Which chord is \(spell(chord.spelledTones))?",
            options: options, correct: correct,
            explanation: "\(chord) = \(spell(chord.spelledTones)) (\(chord.quality.intervals.map(\.shortName).joined(separator: " ")))",
            chord: chord)
    }

    func spellChord<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let chord = randomChord(using: &rng)
        let (options, correct) = shuffledOptions(
            correct: spell(chord.spelledTones),
            wrong: otherChords(than: chord, using: &rng).map { spell($0.spelledTones) },
            using: &rng)
        return HarmonyQuestion(
            kind: .spellChord,
            prompt: "Which notes make \(chord)?",
            options: options, correct: correct,
            explanation: "\(chord) = \(spell(chord.spelledTones)) (\(chord.quality.intervals.map(\.shortName).joined(separator: " ")))",
            chord: chord)
    }

    func keysContaining<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let chord = randomChord(using: &rng)
        let rightKeys = MajorKey.all.filter { MajorKey.contains($0, chord) }
        guard (1...4).contains(rightKeys.count) else { return nil }
        let wrongKeys = MajorKey.all.filter { !rightKeys.contains($0) }.shuffled(using: &rng)
            .prefix(6 - rightKeys.count)
        let all = (rightKeys + wrongKeys).shuffled(using: &rng)
        let correct = Set(all.indices.filter { rightKeys.contains(all[$0]) })
        let explain = rightKeys.map { key -> String in
            let degree = chord.romanNumeral(inMajorKey: key)
            return "\(key) major (\(degree))"
        }.joined(separator: ", ")
        return HarmonyQuestion(
            kind: .keysContaining,
            prompt: "Which major keys contain \(chord)? Pick all that apply.",
            options: all.map { "\($0) major" }, correct: correct,
            explanation: "\(chord) (\(spell(chord.spelledTones))) is in \(explain).",
            chord: chord)
    }

    func modesOnRoot<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let chord = randomChord(using: &rng)
        let root = chord.root
        let correct = Set(Mode.allCases.indices.filter {
            chord.pitchClasses.isSubset(of: Mode.allCases[$0].pitchClasses(root: root))
        })
        guard !correct.isEmpty else { return nil }
        let explain = correct.sorted().map { i -> String in
            let mode = Mode.allCases[i]
            return "\(root) \(mode.name) (from \(mode.parentKey(root: root)) major)"
        }.joined(separator: ", ")
        return HarmonyQuestion(
            kind: .modesOnRoot,
            prompt: "Which modes starting on \(root) contain \(chord)? Pick all that apply.",
            options: Mode.allCases.map { "\(root) \($0.name)" }, correct: correct,
            explanation: "\(chord) (\(spell(chord.spelledTones))) fits \(explain).",
            chord: chord)
    }

    /// Diatonic targets a secondary dominant can point at: ii, iii, IV, V, vi.
    private static let targets: [(degree: Int, numeral: String, quality: ChordQuality)] = [
        (1, "ii", .minor), (2, "iii", .minor), (3, "IV", .major), (4, "V", .major), (5, "vi", .minor),
    ]

    private func target(in key: NoteName, _ t: (degree: Int, numeral: String, quality: ChordQuality)) -> Chord {
        Chord(root: key.up(Interval.majorScaleDegrees[t.degree]), quality: t.quality)
    }

    private func dominant(of chord: Chord) -> Chord {
        Chord(root: chord.root.up(.p5), quality: .dominant7)
    }

    func secondaryDominant<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let key = Self.dominantKeys.randomElement(using: &rng)!
        let targets = Self.targets.shuffled(using: &rng)
        let t = targets[0]
        let goal = target(in: key, t)
        let answer = dominant(of: goal)
        let wrong = targets.dropFirst().prefix(3).map { dominant(of: target(in: key, $0)).description }
        let (options, correct) = shuffledOptions(correct: answer.description, wrong: wrong, using: &rng)
        return HarmonyQuestion(
            kind: .secondaryDominant,
            prompt: "In \(key) major, what is V/\(t.numeral)?",
            options: options, correct: correct,
            explanation: "\(t.numeral) in \(key) is \(goal). Its V (a 5th above) is \(answer).",
            chord: answer)
    }

    func dominantTarget<R: RandomNumberGenerator>(using rng: inout R) -> HarmonyQuestion? {
        let key = Self.dominantKeys.randomElement(using: &rng)!
        let targets = Self.targets.shuffled(using: &rng)
        let t = targets[0]
        let goal = target(in: key, t)
        let dom = dominant(of: goal)
        func label(_ x: (degree: Int, numeral: String, quality: ChordQuality)) -> String {
            "\(target(in: key, x)) (\(x.numeral))"
        }
        let wrong = targets.dropFirst().prefix(3).map(label)
        let (options, correct) = shuffledOptions(correct: label(t), wrong: wrong, using: &rng)
        return HarmonyQuestion(
            kind: .dominantTarget,
            prompt: "In \(key) major, \(dom) is the V of which chord?",
            options: options, correct: correct,
            explanation: "\(dom) resolves down a 5th to \(goal), the \(t.numeral) of \(key) major.",
            chord: dom)
    }
}
