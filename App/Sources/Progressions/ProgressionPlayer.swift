import Foundation
import MusicTheory

/// Plays a list of chords in time and reports which one is sounding.
@MainActor
final class ProgressionPlayer: ObservableObject {
    @Published private(set) var currentIndex: Int?
    @Published private(set) var isPlaying = false

    private var task: Task<Void, Never>?

    func play(_ chords: [Chord], bpm: Int, beatsPerChord: Int, loop: Bool) {
        stop()
        guard !chords.isEmpty else { return }
        isPlaying = true
        let chordSeconds = 60.0 / Double(bpm) * Double(beatsPerChord)
        task = Task { [weak self] in
            repeat {
                for (i, chord) in chords.enumerated() {
                    guard !Task.isCancelled else { return }
                    self?.currentIndex = i
                    PianoSynth.shared.releaseAll()
                    PianoSynth.shared.noteOn(chord.voicing)
                    try? await Task.sleep(nanoseconds: UInt64(chordSeconds * 1_000_000_000))
                }
            } while loop && !Task.isCancelled
            guard !Task.isCancelled else { return }
            PianoSynth.shared.releaseAll()
            self?.currentIndex = nil
            self?.isPlaying = false
        }
    }

    /// Plays one chord on its own (tapping a chord chip).
    func audition(_ chord: Chord) {
        PianoSynth.shared.releaseAll()
        PianoSynth.shared.noteOn(chord.voicing)
    }

    func stop() {
        task?.cancel()
        task = nil
        PianoSynth.shared.releaseAll()
        currentIndex = nil
        isPlaying = false
    }
}
