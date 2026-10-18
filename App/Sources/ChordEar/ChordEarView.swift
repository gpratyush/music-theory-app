import SwiftUI
import MusicTheory

enum ChordPlayStyle: String, CaseIterable {
    case block, broken, both

    var displayName: String {
        switch self {
        case .block: return "Together"
        case .broken: return "One note at a time"
        case .both: return "Broken, then together"
        }
    }
}

struct ChordEarView: View {
    @AppStorage("autoNext") private var autoNext = false
    @AppStorage("timedSeconds") private var timedSeconds = 60
    @AppStorage("chordEar.playStyle") private var playStyle: ChordPlayStyle = .both

    @State private var settings = ChordEarSettings()
    @State private var timed = false
    @State private var session = QuizSession(mode: .practice)
    @State private var question: ChordEarQuestion?
    @State private var picked: ChordQuality?
    @State private var questionID = 0
    @State private var now = Date()
    @State private var showSettings = false
    @State private var errorText: String?
    @State private var playback: Task<Void, Never>?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
    private var advancesAutomatically: Bool { timed || autoNext }
    private var isOver: Bool { session.isOver(at: now) }

    var body: some View {
        VStack(spacing: 20) {
            Picker("Mode", selection: $timed) {
                Text("Practice").tag(false)
                Text("Timed \(timedSeconds)s").tag(true)
            }
            .pickerStyle(.segmented)

            header

            if isOver {
                results
            } else if let question {
                card(question)
            } else if let errorText {
                Text(errorText).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding()
        .navigationTitle("Chord Ear Training")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                .accessibilityLabel("Game settings")
        }
        .sheet(isPresented: $showSettings, onDismiss: restart) {
            ChordEarSettingsView(settings: $settings, playStyle: $playStyle,
                                 autoNext: $autoNext, timedSeconds: $timedSeconds)
        }
        .onChange(of: timed) { restart() }
        .onAppear { if question == nil { restart() } }
        .onDisappear { stopSound() }
        .task(id: session.startedAt) {
            guard timed else { return }
            while !Task.isCancelled && !session.isOver(at: Date()) {
                now = Date()
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
            now = Date()
            stopSound()
        }
    }

    @ViewBuilder private var header: some View {
        if let left = session.remaining(at: now) {
            HStack {
                Label("\(left)s", systemImage: "timer")
                    .foregroundStyle(left <= 10 ? Color.red : Color.primary)
                Spacer()
                Text("\(session.correct) right")
            }
            .font(.headline.monospacedDigit())
        } else {
            Text("\(session.correct) / \(session.answered)")
                .font(.headline)
                .foregroundStyle(.secondary)
        }
    }

    private func card(_ q: ChordEarQuestion) -> some View {
        VStack(spacing: 20) {
            Text("What kind of chord is this?")
                .font(.title2.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 60)

            Button { play(q.chord) } label: {
                Label("Play again", systemImage: "play.fill").frame(minWidth: 160)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(q.options, id: \.self) { option in
                    Button { choose(option, in: q) } label: {
                        Text(option.displayName)
                            .font(.title3)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity, minHeight: 60)
                    }
                    .buttonStyle(.bordered)
                    .tint(tint(option, in: q))
                    .disabled(picked != nil)
                }
            }

            if let picked {
                let right = q.isCorrect(picked)
                Label(right ? "Correct" : "It was \(q.chord.quality.displayName)",
                      systemImage: right ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.headline)
                    .foregroundStyle(right ? Color.green : Color.red)
                Text("\(q.chord.description) = \(q.chord.spelledTones.map(\.description).joined(separator: " "))")
                    .foregroundStyle(.secondary)
                if !advancesAutomatically {
                    Button("Next") { nextQuestion() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
            }
        }
    }

    private func tint(_ option: ChordQuality, in q: ChordEarQuestion) -> Color? {
        guard let picked else { return nil }
        if q.isCorrect(option) { return .green }
        if option == picked { return .red }
        return nil
    }

    private var results: some View {
        VStack(spacing: 16) {
            Text("Time's up!").font(.largeTitle.bold())
            Text("\(session.correct) right out of \(session.answered)").font(.title2)
            if let accuracy = session.accuracy {
                Text("\(accuracy)% accuracy").foregroundStyle(.secondary)
            }
            Button("Play again") { restart() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
        }
        .padding(.top, 40)
    }

    private func choose(_ option: ChordQuality, in q: ChordEarQuestion) {
        guard picked == nil, !isOver else { return }
        picked = option
        let right = q.isCorrect(option)
        session.record(correct: right)
        guard advancesAutomatically else { return }
        let delay: UInt64 = right ? 900_000_000 : 2_000_000_000
        let id = questionID
        Task {
            try? await Task.sleep(nanoseconds: delay)
            if id == questionID { nextQuestion() }
        }
    }

    private func restart() {
        session = QuizSession(mode: timed ? .timed(seconds: timedSeconds) : .practice)
        now = Date()
        nextQuestion()
    }

    private func nextQuestion() {
        picked = nil
        questionID += 1
        do {
            let q = try ChordEarGenerator(settings: settings).next()
            question = q
            errorText = nil
            play(q.chord)
        } catch {
            question = nil
            errorText = "Pick at least one chord type in settings."
        }
    }

    // MARK: Sound

    private func stopSound() {
        playback?.cancel()
        PianoSynth.shared.releaseAll()
    }

    private func play(_ chord: Chord) {
        stopSound()
        let notes = chord.voicing
        let style = playStyle
        playback = Task {
            func hold(_ seconds: Double) async -> Bool {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return !Task.isCancelled
            }
            if style != .block {
                for note in notes {
                    PianoSynth.shared.noteOn([note], velocity: 0.7)
                    guard await hold(0.35) else { return }
                }
                guard await hold(0.6) else { return }
                PianoSynth.shared.releaseAll()
            }
            if style != .broken {
                guard await hold(0.1) else { return }
                PianoSynth.shared.noteOn(notes)
                guard await hold(2.0) else { return }
                PianoSynth.shared.releaseAll()
            }
        }
    }
}

struct ChordEarSettingsView: View {
    @Binding var settings: ChordEarSettings
    @Binding var playStyle: ChordPlayStyle
    @Binding var autoNext: Bool
    @Binding var timedSeconds: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(HarmonySettings.v1Qualities, id: \.self) { quality in
                        Toggle(quality.displayName, isOn: Binding(
                            get: { settings.qualities.contains(quality) },
                            set: { on in
                                settings.qualities.removeAll { $0 == quality }
                                if on { settings.qualities.append(quality) }
                            }
                        ))
                    }
                } header: {
                    Text("Chord types to practise")
                } footer: {
                    Text("Start with major and minor, then add more as they get easy.")
                }
                Section("Sound") {
                    Picker("Play chords", selection: $playStyle) {
                        ForEach(ChordPlayStyle.allCases, id: \.self) { Text($0.displayName).tag($0) }
                    }
                }
                Section("Play") {
                    Toggle("Go to next question automatically", isOn: $autoNext)
                    Picker("Timed round length", selection: $timedSeconds) {
                        Text("30 seconds").tag(30)
                        Text("60 seconds").tag(60)
                        Text("2 minutes").tag(120)
                    }
                }
            }
            .navigationTitle("Chord ear settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
