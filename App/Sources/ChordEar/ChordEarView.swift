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
            GameModeHeader(timed: $timed, timedSeconds: timedSeconds, session: session, now: now)

            if isOver {
                RoundResultsView(session: session, onPlayAgain: restart)
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

    private func card(_ q: ChordEarQuestion) -> some View {
        VStack(spacing: 20) {
            Text("What kind of chord is this?")
                .font(.title2.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 60)

            Button { play(q.chord) } label: {
                Label("Play again", systemImage: "play.fill").frame(minWidth: 160)
            }
            .accessibilityHint("Plays the chord again")
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(q.options, id: \.self) { option in
                    AnswerButton(title: option.displayName,
                                 state: answerState(isRight: q.isCorrect(option),
                                                    isPicked: picked == option, revealed: picked != nil)) {
                        choose(option, in: q)
                    }
                    .disabled(picked != nil)
                }
            }

            if let picked {
                let right = q.isCorrect(picked)
                FeedbackBanner(isCorrect: right, text: right ? "Correct" : "It was \(q.chord.quality.displayName)")
                Text("\(q.chord.description) = \(q.chord.spelledTones.map(\.description).joined(separator: " "))")
                    .foregroundStyle(.secondary)
                if !advancesAutomatically {
                    NextButton { nextQuestion() }
                }
            }
        }
    }

    private func choose(_ option: ChordQuality, in q: ChordEarQuestion) {
        guard picked == nil, !isOver else { return }
        picked = option
        let right = q.isCorrect(option)
        session.record(correct: right)
        Haptics.answer(correct: right)
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
