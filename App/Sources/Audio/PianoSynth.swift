import AVFoundation

/// A small built-in piano-like synth: a few decaying harmonics per note.
/// No sound files needed. It can be swapped for a sampled piano later
/// without changing callers (noteOn / releaseAll).
final class PianoSynth {
    static let shared = PianoSynth()

    private struct Voice {
        let frequency: Double
        let gain: Double
        var time: Double = 0
        var releasedAt: Double?
    }

    private let engine = AVAudioEngine()
    private var source: AVAudioSourceNode?
    private let lock = NSLock()
    private var voices: [Voice] = []
    private var sampleRate: Double = 44_100
    private var started = false

    // Relative strength of each harmonic, roughly like a soft piano.
    private let partials: [Double] = [1.0, 0.45, 0.25, 0.12, 0.08, 0.04]

    private init() {}

    private func startIfNeeded() {
        guard !started else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)

        let hwRate = engine.outputNode.outputFormat(forBus: 0).sampleRate
        if hwRate > 0 { sampleRate = hwRate }
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return }

        let node = AVAudioSourceNode(format: format) { [weak self] _, _, frameCount, bufferList in
            let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
            guard let self, let data = buffers[0].mData else { return noErr }
            self.render(into: data.assumingMemoryBound(to: Float.self), frames: Int(frameCount))
            return noErr
        }
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        source = node
        do {
            try engine.start()
            started = true
        } catch {
            print("Audio engine failed to start: \(error)")
        }
    }

    /// Starts the given MIDI notes. Earlier notes keep ringing until released.
    func noteOn(_ notes: [Int], velocity: Double = 0.8) {
        startIfNeeded()
        let newVoices = notes.map { note in
            Voice(frequency: 440 * pow(2, Double(note - 69) / 12), gain: velocity)
        }
        lock.lock()
        voices.append(contentsOf: newVoices)
        lock.unlock()
    }

    /// Lets every sounding note fade out quickly.
    func releaseAll() {
        lock.lock()
        for i in voices.indices where voices[i].releasedAt == nil {
            voices[i].releasedAt = voices[i].time
        }
        lock.unlock()
    }

    private func render(into out: UnsafeMutablePointer<Float>, frames: Int) {
        let dt = 1 / sampleRate
        lock.lock()
        defer { lock.unlock() }
        for f in 0..<frames {
            var sample = 0.0
            for v in voices.indices {
                let t = voices[v].time
                let attack = min(1, t / 0.004)
                var env = attack * exp(-t / 1.6)
                if let r = voices[v].releasedAt { env *= exp(-(t - r) / 0.12) }
                var s = 0.0
                for (k, amp) in partials.enumerated() {
                    let n = Double(k + 1)
                    // Higher harmonics die away faster, which is what makes it sound plucked.
                    s += amp * exp(-t * n * 0.8) * sin(2 * .pi * voices[v].frequency * n * t)
                }
                sample += s * env * voices[v].gain
                voices[v].time = t + dt
            }
            out[f] = Float(tanh(sample * 0.18))   // soft limit so big chords don't clip
        }
        voices.removeAll { v in
            v.time > 6 || (v.releasedAt.map { v.time - $0 > 1 } ?? false)
        }
    }
}
