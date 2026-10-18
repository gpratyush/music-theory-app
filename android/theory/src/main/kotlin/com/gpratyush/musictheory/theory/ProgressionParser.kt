package com.gpratyush.musictheory.theory

sealed class ProgressionParseError(message: String) : Exception(message) {
    data object Empty : ProgressionParseError("Type some chords, e.g. Cm7 - Am7 - D7 - Gmaj7")
    data class UnknownChord(val token: String) : ProgressionParseError("Couldn't read \"$token\"")
}

/**
 * Reads progressions written like "Cm7 - Am7 - D7 - Gmaj7" or "C | Am | F | G".
 * Chords can be separated by spaces, dashes, bars or commas.
 */
object ProgressionParser {
    private val separators = setOf(' ', '\n', '\t', '-', '–', '—', '|', ',')

    fun parse(text: String): List<Chord> {
        val tokens = text.split { it in separators }
        if (tokens.isEmpty()) throw ProgressionParseError.Empty
        return tokens.map(::parseChord)
    }

    fun parseChord(token: String): Chord {
        var main = token
        var bass: NoteName? = null
        val slash = token.lastIndexOf('/')
        if (slash >= 0) {
            val parsedBass = parseNote(token.substring(slash + 1))
            if (parsedBass == null || parsedBass.second.isNotEmpty()) throw ProgressionParseError.UnknownChord(token)
            bass = parsedBass.first
            main = token.substring(0, slash)
        }
        val parsed = parseNote(main) ?: throw ProgressionParseError.UnknownChord(token)
        val quality = ChordQuality.spellings[parsed.second] ?: throw ProgressionParseError.UnknownChord(token)
        return Chord(parsed.first, quality, bass)
    }

    /** Reads a note name from the front: a letter plus up to two sharps or flats. */
    internal fun parseNote(text: String): Pair<NoteName, String>? {
        val first = text.firstOrNull() ?: return null
        val letter = Letter.entries.firstOrNull { it.symbol == first.uppercase() } ?: return null
        var i = 1
        var accidental = 0
        while (i < text.length && kotlin.math.abs(accidental) < 2) {
            when (text[i]) {
                '#', '♯' -> accidental += 1
                'b', '♭' -> accidental -= 1
                else -> break
            }
            i++
        }
        return NoteName(letter, accidental) to text.substring(i)
    }

    private fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in this) {
            if (isSeparator(c)) {
                if (current.isNotEmpty()) out += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
