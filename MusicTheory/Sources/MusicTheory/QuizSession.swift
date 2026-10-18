import Foundation

/// Practice = untimed. Timed = as many right as possible before the clock runs out.
public enum QuizMode: Hashable, Sendable {
    case practice
    case timed(seconds: Int)
}

/// Score and clock for one round of any quiz game. Shared by every game,
/// so timed mode works the same everywhere.
public struct QuizSession: Sendable, Equatable {
    public let mode: QuizMode
    public let startedAt: Date
    public private(set) var correct = 0
    public private(set) var answered = 0

    public init(mode: QuizMode, startedAt: Date = Date()) {
        self.mode = mode
        self.startedAt = startedAt
    }

    /// Seconds left, or nil in practice mode.
    public func remaining(at now: Date = Date()) -> Int? {
        guard case let .timed(seconds) = mode else { return nil }
        let left = Double(seconds) - now.timeIntervalSince(startedAt)
        return max(0, Int(left.rounded(.up)))
    }

    public func isOver(at now: Date = Date()) -> Bool {
        remaining(at: now) == 0
    }

    /// Records an answer. Ignored once a timed round is over.
    public mutating func record(correct isCorrect: Bool, at now: Date = Date()) {
        guard !isOver(at: now) else { return }
        answered += 1
        if isCorrect { correct += 1 }
    }

    /// 0...100, or nil before the first answer.
    public var accuracy: Int? {
        answered == 0 ? nil : Int((Double(correct) / Double(answered) * 100).rounded())
    }
}
