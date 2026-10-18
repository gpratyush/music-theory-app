public enum IntervalQuality: Sendable, Hashable {
    case perfect, major, minor, augmented, diminished

    public var name: String {
        switch self {
        case .perfect: return "Perfect"
        case .major: return "Major"
        case .minor: return "Minor"
        case .augmented: return "Augmented"
        case .diminished: return "Diminished"
        }
    }

    public var abbreviation: String {
        switch self {
        case .perfect: return "P"
        case .major: return "M"
        case .minor: return "m"
        case .augmented: return "A"
        case .diminished: return "d"
        }
    }
}

/// A spelled interval such as a minor 3rd or a perfect 5th.
/// `number` is the letter distance plus one (unison = 1, octave = 8, 9th = 9...).
public struct Interval: Hashable, Sendable, CustomStringConvertible {
    public let quality: IntervalQuality
    public let number: Int

    public init(_ quality: IntervalQuality, _ number: Int) {
        precondition(number >= 1, "Interval number starts at 1 (unison)")
        self.quality = quality
        self.number = number
    }

    /// 1, 4, 5, 8 (and their compounds) are perfect-type intervals.
    public static func isPerfectType(_ number: Int) -> Bool {
        [1, 4, 5].contains((number - 1) % 7 + 1)
    }

    public var semitones: Int {
        let simple = (number - 1) % 7          // 0...6 letter steps
        let octaves = (number - 1) / 7
        let base = [0, 2, 4, 5, 7, 9, 11][simple] // major / perfect size
        let offset: Int
        if Interval.isPerfectType(number) {
            switch quality {
            case .perfect: offset = 0
            case .augmented: offset = 1
            case .diminished: offset = -1
            default: offset = 0
            }
        } else {
            switch quality {
            case .major: offset = 0
            case .minor: offset = -1
            case .augmented: offset = 1
            case .diminished: offset = -2
            default: offset = 0
            }
        }
        return base + offset + 12 * octaves
    }

    public var isCompound: Bool { number > 8 }

    public var name: String {
        if number == 1 && quality == .perfect { return "Unison" }
        if number == 8 && quality == .perfect { return "Octave" }
        return "\(quality.name) \(ordinal(number))"
    }

    public var shortName: String { "\(quality.abbreviation)\(number)" }

    public var description: String { name }

    // MARK: Common sets

    /// The intervals a learner meets first, within one octave.
    public static let simple: [Interval] = [
        Interval(.minor, 2), Interval(.major, 2),
        Interval(.minor, 3), Interval(.major, 3),
        Interval(.perfect, 4), Interval(.augmented, 4), Interval(.diminished, 5),
        Interval(.perfect, 5),
        Interval(.minor, 6), Interval(.major, 6),
        Interval(.minor, 7), Interval(.major, 7),
        Interval(.perfect, 8),
    ]

    public static let compound: [Interval] = [
        Interval(.minor, 9), Interval(.major, 9),
        Interval(.perfect, 11), Interval(.augmented, 11),
        Interval(.minor, 13), Interval(.major, 13),
    ]
}

extension NoteName {
    /// The note this interval above. Spelled correctly: C + minor 6th = Ab.
    public func up(_ interval: Interval) -> NoteName {
        let target = letter.advanced(by: interval.number - 1)
        let wanted = pitchClass + interval.semitones
        return NoteName(target, smallestAccidental(from: target.semitone, to: wanted))
    }

    /// The note this interval below. "C is the perfect 5th of what?" = C.down(P5) = F.
    public func down(_ interval: Interval) -> NoteName {
        let target = letter.advanced(by: -(interval.number - 1))
        let wanted = pitchClass - interval.semitones
        return NoteName(target, smallestAccidental(from: target.semitone, to: wanted))
    }

    /// The simple interval from this note up to `other` (within an octave), by spelling.
    public func interval(to other: NoteName) -> Interval? {
        let steps = ((other.letter.rawValue - letter.rawValue) % 7 + 7) % 7
        let number = steps + 1
        let semis = mod12(other.pitchClass - pitchClass)
        let candidates: [IntervalQuality] = [.perfect, .major, .minor, .augmented, .diminished]
        for q in candidates {
            if Interval.isPerfectType(number) && (q == .major || q == .minor) { continue }
            if !Interval.isPerfectType(number) && q == .perfect { continue }
            let iv = Interval(q, number)
            if mod12(iv.semitones) == semis { return iv }
        }
        return nil
    }
}

private func smallestAccidental(from natural: Int, to wanted: Int) -> Int {
    var diff = mod12(wanted - natural)
    if diff > 6 { diff -= 12 }
    return diff
}

private func ordinal(_ n: Int) -> String {
    let suffix: String
    switch n % 100 {
    case 11, 12, 13: suffix = "th"
    default:
        switch n % 10 {
        case 1: suffix = "st"
        case 2: suffix = "nd"
        case 3: suffix = "rd"
        default: suffix = "th"
        }
    }
    return "\(n)\(suffix)"
}
