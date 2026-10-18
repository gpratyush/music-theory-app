/// How the two notes are played.
public enum EarDirection: String, CaseIterable, Sendable {
    case ascending, descending, harmonic

    public var displayName: String {
        switch self {
        case .ascending: return "Up"
        case .descending: return "Down"
        case .harmonic: return "Together"
        }
    }
}

/// What the learner is asked after hearing the notes.
public enum EarQuestionStyle: String, CaseIterable, Sendable {
    /// Two steps: name the first note, then the second.
    case nameNotes
    /// One step: name the interval between them.
    case nameInterval
}

public struct EarTrainingSettings: Sendable, Equatable {
    public var whiteKeysOnly: Bool
    public var intervals: [Interval]
    public var directions: Set<EarDirection>
    public var style: EarQuestionStyle

    public init(
        whiteKeysOnly: Bool = true,
        intervals: [Interval] = Interval.simple,
        directions: Set<EarDirection> = [.ascending],
        style: EarQuestionStyle = .nameNotes
    ) {
        self.whiteKeysOnly = whiteKeysOnly
        self.intervals = intervals
        self.directions = directions
        self.style = style
    }
}

/// One played note: its spelled name and MIDI number.
public struct PlayedNote: Hashable, Sendable {
    public let name: NoteName
    public let midi: Int
}

/// A reference note (named to the learner), then two notes to identify.
public struct EarQuestion: Sendable {
    public let reference: PlayedNote
    public let first: PlayedNote
    public let second: PlayedNote
    public let interval: Interval
    public let direction: EarDirection
    public let firstOptions: [NoteName]
    public let secondOptions: [NoteName]
    public let intervalOptions: [Interval]

    public func isCorrectFirst(_ n: NoteName) -> Bool { n.isEnharmonic(to: first.name) }
    public func isCorrectSecond(_ n: NoteName) -> Bool { n.isEnharmonic(to: second.name) }
    public func isCorrectInterval(_ iv: Interval) -> Bool { iv.semitones == interval.semitones }
}

public struct EarTrainingGenerator: Sendable {
    public var settings: EarTrainingSettings

    public init(settings: EarTrainingSettings = EarTrainingSettings()) {
        self.settings = settings
    }

    private var pool: [NoteName] {
        settings.whiteKeysOnly ? NoteName.naturals : NoteName.commonSpellings
    }

    public func next() throws -> EarQuestion {
        var rng = SystemRandomNumberGenerator()
        return try next(using: &rng)
    }

    public func next<R: RandomNumberGenerator>(using rng: inout R) throws -> EarQuestion {
        let intervals = settings.intervals.filter { !$0.isCompound }
        // Every (first note, interval, direction) whose second note stays in the allowed set.
        var combos: [(NoteName, Interval, EarDirection, NoteName)] = []
        for first in pool {
            for iv in intervals {
                for dir in EarDirection.allCases where settings.directions.contains(dir) {
                    let second = dir == .descending ? first.down(iv) : first.up(iv)
                    if pool.contains(second) { combos.append((first, iv, dir, second)) }
                }
            }
        }
        guard let pick = combos.randomElement(using: &rng) else {
            throw IntervalQuizError.noQuestionsPossible
        }
        let (firstName, iv, dir, secondName) = pick

        // Reference: a natural note near the first note, C4...B4.
        let refName = NoteName.naturals.randomElement(using: &rng)!
        let reference = PlayedNote(name: refName, midi: 60 + refName.pitchClass)

        // First note within a fifth or so of the reference, so it's easy to relate.
        var firstMidi = reference.midi + (firstName.pitchClass - refName.pitchClass)
        if firstMidi - reference.midi > 6 { firstMidi -= 12 }
        if reference.midi - firstMidi > 6 { firstMidi += 12 }
        let secondMidi = dir == .descending ? firstMidi - iv.semitones : firstMidi + iv.semitones

        return EarQuestion(
            reference: reference,
            first: PlayedNote(name: firstName, midi: firstMidi),
            second: PlayedNote(name: secondName, midi: secondMidi),
            interval: iv,
            direction: dir,
            firstOptions: QuizOptions.notes(correct: firstName, pool: pool, using: &rng),
            secondOptions: QuizOptions.notes(correct: secondName, pool: pool, using: &rng),
            intervalOptions: QuizOptions.intervals(correct: iv, using: &rng)
        )
    }
}

/// Builds 4-option answer sets shared by the games: the right answer plus three
/// nearby wrong ones, never two that sound the same.
public enum QuizOptions {
    public static func notes<R: RandomNumberGenerator>(
        correct: NoteName, pool: [NoteName], using rng: inout R
    ) -> [NoteName] {
        var seen: Set<Int> = [correct.pitchClass]
        var near: [NoteName] = []
        for n in pool.shuffled(using: &rng).sorted(by: {
            circularDistance($0.pitchClass, correct.pitchClass) < circularDistance($1.pitchClass, correct.pitchClass)
        }) where !seen.contains(n.pitchClass) {
            seen.insert(n.pitchClass)
            near.append(n)
        }
        let wrong = near.prefix(5).shuffled(using: &rng).prefix(3)
        return ([correct] + wrong).shuffled(using: &rng)
    }

    public static func intervals<R: RandomNumberGenerator>(
        correct: Interval, using rng: inout R
    ) -> [Interval] {
        var seen: Set<Int> = [correct.semitones]
        var near: [Interval] = []
        for iv in Interval.simple.shuffled(using: &rng).sorted(by: {
            abs($0.semitones - correct.semitones) < abs($1.semitones - correct.semitones)
        }) where !seen.contains(iv.semitones) {
            seen.insert(iv.semitones)
            near.append(iv)
        }
        let wrong = near.prefix(5).shuffled(using: &rng).prefix(3)
        return ([correct] + wrong).shuffled(using: &rng)
    }
}

func circularDistance(_ a: Int, _ b: Int) -> Int {
    let d = mod12(a - b)
    return min(d, 12 - d)
}
