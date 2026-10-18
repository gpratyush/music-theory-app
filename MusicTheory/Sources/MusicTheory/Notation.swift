/// How interval names are shown to the learner.
public enum IntervalNotation: String, CaseIterable, Sendable {
    case western
    case swara

    public var displayName: String {
        switch self {
        case .western: return "Western"
        case .swara: return "Sa Re Ga"
        }
    }

    public func label(for interval: Interval) -> String {
        switch self {
        case .western: return interval.name
        case .swara: return SwaraTable.label(for: interval)
        }
    }
}

/// Maps an interval (by semitones above Sa) to its swara label.
///
/// PLACEHOLDER: these are the standard Hindustani names. Replace the values
/// with Pratyush's translation table when it arrives; nothing else needs to change.
public enum SwaraTable {
    public static let labels: [Int: String] = [
        0: "Sa",
        1: "komal Re",
        2: "Re",
        3: "komal Ga",
        4: "Ga",
        5: "Ma",
        6: "tivra Ma",
        7: "Pa",
        8: "komal Dha",
        9: "Dha",
        10: "komal Ni",
        11: "Ni",
    ]

    public static func label(for interval: Interval) -> String {
        let semis = interval.semitones
        if semis == 12 { return "Sa'" }   // upper Sa
        let base = labels[mod12(semis)] ?? interval.shortName
        return semis > 12 ? base + "'" : base  // compound intervals sit in the upper octave
    }
}
