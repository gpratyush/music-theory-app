import AVFoundation

/// Plays piano notes from recorded samples (Salamander Grand Piano, sampled every
/// minor third and pitch-shifted to the notes in between). If the samples can't be
/// loaded it falls back to a simple built-in tone, so the app is never silent.
///
/// Callers only use `noteOn` and `releaseAll`.
final class PianoSynth {
    static let shared = PianoSynth()

    private struct Sample {
        let note: Int          // MIDI note the sample was recorded at
        let frames: [Float]
        let sampleRate: Double
    }

    private struct Voice {
        let sample: Int?       // index into samples; nil = built-in tone
        let step: Double       // sample frames to advance per output frame
        let frequency: Double
        let gain: Double
        var position: Double = 0
        var time: Double = 0
        var releasedAt: Double?
    }

    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var samples: [Sample] = []
    private var voices: [Voice] = []
    private var sampleRate: Double = 44_100
    private var started = false
    private var loaded = false

    /// Built-in tone used only if samples are missing.
    private let partials: [Double] = [1.0, 0.45, 0.25, 0.12, 0.08, 0.04]

    private init() {}

    /// Loads the samples ahead of time so the first note plays straight away.
    func prepare() {
        DispatchQueue.global(qos: .userInitiated).async { self.loadSamplesIfNeeded() }
    }

    private func loadSamplesIfNeeded() {
        lock.lock()
        let done = loaded
        lock.unlock()
        guard !done else { return }

        var names: [(String, Int)] = [("A1", 33)]
        for octave in 2...6 {
            for (name, pc) in [("C", 0), ("Ds", 3), ("Fs", 6), ("A", 9)] {
                names.append(("\(name)\(octave)", 12 * (octave + 1) + pc))
            }
        }
        names.append(("C7", 96))

        var loadedSamples: [Sample] = []
        for (name, note) in names {
            guard let url = Bundle.main.url(forResource: "piano_\(name)", withExtension: "m4a"),
                  let file = try? AVAudioFile(forReading: url),
                  let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat,
                                                frameCapacity: AVAudioFrameCount(file.length)),
                  (try? file.read(into: buffer)) != nil,
                  let channel = buffer.floatChannelData?[0] else { continue }
            let frames = Array(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
            loadedSamples.append(Sample(note: note, frames: frames, sampleRate: file.processingFormat.sampleRate))
        }

        lock.lock()
        samples = loadedSamples
        loaded = true
        lock.unlock()
    }

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
        do {
            try engine.start()
            started = true
        } catch {
            print("Audio engine failed to start: \(error)")
        }
    }

    /// Starts the given MIDI notes. Earlier notes keep ringing until released.
    func noteOn(_ notes: [Int], velocity: Double = 0.8) {
        loadSamplesIfNeeded()
        startIfNeeded()
        lock.lock()
        for note in notes {
            let frequency = 440 * pow(2, Double(note - 69) / 12)
            if let nearest = samples.enumerated().min(by: { abs($0.element.note - note) < abs($1.element.note - note) }) {
                let index = nearest.offset
                let sample = nearest.element
                let shift = pow(2, Double(note - sample.note) / 12)
                voices.append(Voice(sample: index, step: shift * sample.sampleRate / sampleRate,
                                    frequency: frequency, gain: velocity))
            } else {
                voices.append(Voice(sample: nil, step: 0, frequency: frequency, gain: velocity * 0.6))
            }
        }
        lock.unlock()
    }

    /// Lets every sounding note fade out quickly, like lifting the keys.
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
            var mix = 0.0
            for v in voices.indices {
                let t = voices[v].time
                var env = 1.0
                if let r = voices[v].releasedAt { env = exp(-(t - r) / 0.15) }
                if let s = voices[v].sample {
                    let data = samples[s].frames
                    let pos = voices[v].position
                    let i = Int(pos)
                    if i + 1 < data.count {
                        let frac = Float(pos - Double(i))
                        let value = data[i] + (data[i + 1] - data[i]) * frac
                        mix += Double(value) * env * voices[v].gain
                    }
                    voices[v].position = pos + voices[v].step
                } else {
                    env *= min(1, t / 0.004) * exp(-t / 1.6)
                    var s = 0.0
                    for (k, amp) in partials.enumerated() {
                        let n = Double(k + 1)
                        s += amp * exp(-t * n * 0.8) * sin(2 * .pi * voices[v].frequency * n * t)
                    }
                    mix += s * env * voices[v].gain * 0.25
                }
                voices[v].time = t + dt
            }
            out[f] = Float(tanh(mix * 0.9))   // soft limit so big chords don't clip
        }
        voices.removeAll { v in
            if let r = v.releasedAt, v.time - r > 1 { return true }
            if let s = v.sample { return Int(v.position) + 1 >= samples[s].frames.count }
            return v.time > 6
        }
    }
}
