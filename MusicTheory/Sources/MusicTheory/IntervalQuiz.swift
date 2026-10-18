/// The three flashcard question styles.
public enum IntervalQuestionKind: String, CaseIterable, Sendable {
    /// "What is the major 3rd above C?" → E
    case noteAbove
    /// "C is the perfect 5th of which note?" → F
    case rootBelow
    /// "What interval is C up to E?" → Major 3rd
    case nameInterval
}

public struct IntervalQuizSettings: Sendable, Equatable {
    /// Keep every note in the question and answer on the white keys.
    public var whiteKeysOnly: Bool
    /// Allow 9ths, 11ths and 13ths.
    public var includeCompound: Bool
    /// Which intervals can be asked about.
    public var intervals: [Interval]
    public var kinds: Set<IntervalQuestionKind>

    public init(
        whiteKeysOnly: Bool = true,
        includeCompound: Bool = false,
        intervals: [Interval] = Interval.simple,
        kinds: Set<IntervalQuestionKind> = Set(IntervalQuestionKind.allCases)
    ) {
        self.whiteKeysOnly = whiteKeysOnly
        self.includeCompound = includeCompound
        self.intervals = intervals
        self.kinds = kinds
    }

    var activeIntervals: [Interval] {
        includeCompound ? intervals + Interval.compound : intervals.filter { !$0.isCompound }
    }
}

public enum QuizAnswer: Hashable, Sendable {
    case note(NoteName)
    case interval(Interval)
}

public struct IntervalQuestion: Sendable {
    public let kind: IntervalQuestionKind
    /// The note shown in the question.
    public let given: NoteName
    /// For `nameInterval`, the second note shown.
    public let other: NoteName?
    public let interval: Interval
    public let options: [QuizAnswer]
    public let correct: QuizAnswer

    /// Enharmonic answers count: Ab and G# are both right, and so are
    /// an augmented 4th and a diminished 5th.
    public func isCorrect(_ answer: QuizAnswer) -> Bool {
        switch (answer, correct) {
        case let (.note(a), .note(b)): return a.isEnharmonic(to: b)
        case let (.interval(a), .interval(b)): return a.semitones == b.semitones
        default: return false
        }
    }

    public func prompt(label: (Interval) -> String) -> String {
        switch kind {
        case .noteAbove: return "What is the \(label(interval)) above \(given)?"
        case .rootBelow: return "\(given) is the \(label(interval)) of which note?"
        case .nameInterval: return "What interval is \(given) up to \(other!)?"
        }
    }
}

public enum IntervalQuizError: Error, Equatable {
    /// The settings leave nothing to ask (e.g. no intervals picked).
    case noQuestionsPossible
}

public struct IntervalQuizGenerator: Sendable {
    public var settings: IntervalQuizSettings

    public init(settings: IntervalQuizSettings = IntervalQuizSettings()) {
        self.settings = settings
    }

    private var notePool: [NoteName] {
        settings.whiteKeysOnly ? NoteName.naturals : NoteName.commonSpellings
    }

    private func allowed(_ note: NoteName) -> Bool {
        notePool.contains(note)
    }

    /// Every (kind, given note, interval) combination the settings allow.
    func candidates() -> [(IntervalQuestionKind, NoteName, Interval, NoteName)] {
        var out: [(IntervalQuestionKind, NoteName, Interval, NoteName)] = []
        for kind in IntervalQuestionKind.allCases where settings.kinds.contains(kind) {
            for given in notePool {
                for iv in settings.activeIntervals {
                    switch kind {
                    case .noteAbove:
                        let ans = given.up(iv)
                        if allowed(ans) { out.append((kind, given, iv, ans)) }
                    case .rootBelow:
                        let ans = given.down(iv)
                        if allowed(ans) { out.append((kind, given, iv, ans)) }
                    case .nameInterval:
                        // Without octaves, only intervals inside one octave are unambiguous.
                        guard !iv.isCompound, iv.number < 8 else { continue }
                        let top = given.up(iv)
                        if allowed(top) { out.append((kind, given, iv, top)) }
                    }
                }
            }
        }
        return out
    }

    public func next() throws -> IntervalQuestion {
        var rng = SystemRandomNumberGenerator()
        return try next(using: &rng)
    }

    public func next<R: RandomNumberGenerator>(using rng: inout R) throws -> IntervalQuestion {
        guard let pick = candidates().randomElement(using: &rng) else {
            throw IntervalQuizError.noQuestionsPossible
        }
        let (kind, given, iv, note) = pick
        switch kind {
        case .noteAbove, .rootBelow:
            let correct = QuizAnswer.note(note)
            let distractors = noteDistractors(for: note, using: &rng).map(QuizAnswer.note)
            return IntervalQuestion(
                kind: kind, given: given, other: nil, interval: iv,
                options: ([correct] + distractors).shuffled(using: &rng), correct: correct)
        case .nameInterval:
            let correct = QuizAnswer.interval(iv)
            let distractors = intervalDistractors(for: iv, using: &rng).map(QuizAnswer.interval)
            return IntervalQuestion(
                kind: kind, given: given, other: note, interval: iv,
                options: ([correct] + distractors).shuffled(using: &rng), correct: correct)
        }
    }

    /// Three nearby notes, each a different pitch from the answer and from each other,
    /// so two buttons are never both right.
    func noteDistractors<R: RandomNumberGenerator>(for answer: NoteName, using rng: inout R) -> [NoteName] {
        var seen: Set<Int> = [answer.pitchClass]
        var pool: [NoteName] = []
        for n in notePool.shuffled(using: &rng).sorted(by: {
            circularDistance($0.pitchClass, answer.pitchClass) < circularDistance($1.pitchClass, answer.pitchClass)
        }) where !seen.contains(n.pitchClass) {
            seen.insert(n.pitchClass)
            pool.append(n)
        }
        return Array(pool.prefix(5).shuffled(using: &rng).prefix(3))
    }

    /// Three nearby intervals with different sizes from the answer and from each other.
    func intervalDistractors<R: RandomNumberGenerator>(for answer: Interval, using rng: inout R) -> [Interval] {
        let source = Interval.simple.filter { $0.number < 8 }
        var seen: Set<Int> = [answer.semitones]
        var pool: [Interval] = []
        for iv in source.shuffled(using: &rng).sorted(by: {
            abs($0.semitones - answer.semitones) < abs($1.semitones - answer.semitones)
        }) where !seen.contains(iv.semitones) {
            seen.insert(iv.semitones)
            pool.append(iv)
        }
        return Array(pool.prefix(5).shuffled(using: &rng).prefix(3))
    }
}
