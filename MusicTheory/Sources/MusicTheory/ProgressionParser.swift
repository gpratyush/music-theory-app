public enum ProgressionParseError: Error, Equatable, CustomStringConvertible {
    case empty
    case unknownChord(String)

    public var description: String {
        switch self {
        case .empty: return "Type some chords, e.g. Cm7 - Am7 - D7 - Gmaj7"
        case let .unknownChord(token): return "Couldn't read \"\(token)\""
        }
    }
}

/// Reads progressions written like "Cm7 - Am7 - D7 - Gmaj7" or "C | Am | F | G".
/// Chords can be separated by spaces, dashes, bars or commas.
public enum ProgressionParser {
    public static func parse(_ text: String) throws -> [Chord] {
        let separators: Set<Character> = [" ", "\n", "\t", "-", "–", "—", "|", ","]
        let tokens = text.split(whereSeparator: { separators.contains($0) }).map(String.init)
        guard !tokens.isEmpty else { throw ProgressionParseError.empty }
        return try tokens.map(parseChord)
    }

    public static func parseChord(_ token: String) throws -> Chord {
        var main = Substring(token)
        var bass: NoteName?
        if let slash = token.lastIndex(of: "/") {
            let bassText = token[token.index(after: slash)...]
            guard let parsedBass = parseNote(bassText), parsedBass.1.isEmpty else {
                throw ProgressionParseError.unknownChord(token)
            }
            bass = parsedBass.0
            main = token[..<slash]
        }
        guard let parsed = parseNote(main),
              let quality = ChordQuality.spellings[String(parsed.1)] else {
            throw ProgressionParseError.unknownChord(token)
        }
        return Chord(root: parsed.0, quality: quality, bass: bass)
    }

    /// Reads a note name from the front: a letter plus up to two sharps or flats.
    static func parseNote(_ text: Substring) -> (NoteName, Substring)? {
        guard let first = text.first,
              let letter = Letter.allCases.first(where: { $0.symbol == first.uppercased() }) else {
            return nil
        }
        var rest = text.dropFirst()
        var accidental = 0
        while let c = rest.first, accidental.magnitude < 2 {
            if c == "#" || c == "♯" { accidental += 1 }
            else if c == "b" || c == "♭" { accidental -= 1 }
            else { break }
            rest = rest.dropFirst()
        }
        return (NoteName(letter, accidental), rest)
    }
}
