import SwiftUI
import MusicTheory

struct EarTrainingView: View {
    @AppStorage(IntervalNotation.storageKey) private var notation: IntervalNotation = .western
    @AppStorage("autoNext") private var autoNext = false
    @AppStorage("timedSeconds") private var timedSeconds = 60

    private enum Step { case first, second, interval, done }

    @State private var settings = EarTrainingSettings()
    @State private var timed = false
    @State private var session = QuizSession(mode: .practice)
    @State private var question: EarQuestion?
    @State private var step: Step = .first
    @State private var pickedFirst: NoteName?
    @State private var pickedSecond: NoteName?
    @State private var pickedInterval: Interval?
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
        .navigationTitle("Ear Training")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                .accessibilityLabel("Game settings")
        }
        .sheet(isPresented: $showSettings, onDismiss: restart) {
            EarTrainingSettingsView(settings: $settings, autoNext: $autoNext, timedSeconds: $timedSeconds)
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

    private func card(_ q: EarQuestion) -> some View {
        VStack(spacing: 16) {
            Text("This is \(q.reference.name.description). Listen:")
                .font(.headline)
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Button { play(q) } label: { Label("Replay", systemImage: "play.fill") }
                    .buttonStyle(.borderedProminent)
                Button { playReference(q) } label: { Label("Reference", systemImage: "tuningfork") }
                    .buttonStyle(.bordered)
            }

            Text(prompt(for: q))
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 60)

            optionGrid(q)

            stepResults(q)

            if step == .done && !advancesAutomatically {
                Button("Next") { nextQuestion() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            }
        }
    }

    private func prompt(for q: EarQuestion) -> String {
        switch step {
        case .first: return q.direction == .harmonic ? "What was the lower note?" : "What was the first note?"
        case .second: return q.direction == .harmonic ? "And the upper note?" : "And the second note?"
        case .interval: return "What interval was that?"
        case .done: return isAllCorrect(q) ? "Correct!" : "Not quite"
        }
    }

    @ViewBuilder private func optionGrid(_ q: EarQuestion) -> some View {
        if settings.style == .nameInterval {
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(q.intervalOptions, id: \.self) { iv in
                    answerButton(notation.label(for: iv),
                                 tint: tint(isRight: q.isCorrectInterval(iv), isPicked: pickedInterval == iv,
                                            revealed: pickedInterval != nil),
                                 disabled: pickedInterval != nil) { pickInterval(iv, q) }
                }
            }
        } else if step == .first {
            noteGrid(q.firstOptions, picked: pickedFirst, isCorrect: q.isCorrectFirst) { pickFirst($0, q) }
        } else {
            noteGrid(q.secondOptions, picked: pickedSecond, isCorrect: q.isCorrectSecond) { pickSecond($0, q) }
        }
    }

    private func noteGrid(_ options: [NoteName], picked: NoteName?, isCorrect: @escaping (NoteName) -> Bool,
                          action: @escaping (NoteName) -> Void) -> some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(options, id: \.self) { n in
                answerButton(n.description,
                             tint: tint(isRight: isCorrect(n), isPicked: picked == n, revealed: picked != nil),
                             disabled: picked != nil) { action(n) }
            }
        }
    }

    private func answerButton(_ title: String, tint: Color?, disabled: Bool,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.title3).frame(maxWidth: .infinity, minHeight: 56)
        }
        .buttonStyle(.bordered)
        .tint(tint)
        .disabled(disabled)
    }

    private func tint(isRight: Bool, isPicked: Bool, revealed: Bool) -> Color? {
        guard revealed else { return nil }
        if isRight { return .green }
        if isPicked { return .red }
        return nil
    }

    /// One line per answered step, with the right answer when it was missed.
    @ViewBuilder private func stepResults(_ q: EarQuestion) -> some View {
        VStack(spacing: 4) {
            if let f = pickedFirst {
                resultLine(label: q.direction == .harmonic ? "Lower" : "First",
                           right: q.isCorrectFirst(f), answer: q.first.name.description)
            }
            if let s = pickedSecond {
                resultLine(label: q.direction == .harmonic ? "Upper" : "Second",
                           right: q.isCorrectSecond(s), answer: q.second.name.description)
            }
            if step == .done {
                Text("Interval: \(notation.label(for: q.interval))")
                    .foregroundStyle(.secondary)
            }
        }
        .font(.headline)
    }

    private func resultLine(label: String, right: Bool, answer: String) -> some View {
        Label("\(label): \(answer)", systemImage: right ? "checkmark.circle.fill" : "xmark.circle.fill")
            .foregroundStyle(right ? Color.green : Color.red)
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

    private func isAllCorrect(_ q: EarQuestion) -> Bool {
        switch settings.style {
        case .nameNotes:
            return pickedFirst.map(q.isCorrectFirst) == true && pickedSecond.map(q.isCorrectSecond) == true
        case .nameInterval:
            return pickedInterval.map(q.isCorrectInterval) == true
        }
    }

    // MARK: Answers

    private func pickFirst(_ n: NoteName, _ q: EarQuestion) {
        guard step == .first, !isOver else { return }
        pickedFirst = n
        session.record(correct: q.isCorrectFirst(n))
        step = .second
    }

    private func pickSecond(_ n: NoteName, _ q: EarQuestion) {
        guard step == .second, !isOver else { return }
        pickedSecond = n
        session.record(correct: q.isCorrectSecond(n))
        finish(q)
    }

    private func pickInterval(_ iv: Interval, _ q: EarQuestion) {
        guard step == .interval, !isOver else { return }
        pickedInterval = iv
        session.record(correct: q.isCorrectInterval(iv))
        finish(q)
    }

    private func finish(_ q: EarQuestion) {
        step = .done
        guard advancesAutomatically else { return }
        let delay: UInt64 = isAllCorrect(q) ? 900_000_000 : 2_000_000_000
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
        pickedFirst = nil
        pickedSecond = nil
        pickedInterval = nil
        step = settings.style == .nameNotes ? .first : .interval
        questionID += 1
        do {
            let q = try EarTrainingGenerator(settings: settings).next()
            question = q
            errorText = nil
            play(q)
        } catch {
            question = nil
            errorText = "No questions match these settings. Try turning more options on."
        }
    }

    // MARK: Sound

    private func stopSound() {
        playback?.cancel()
        PianoSynth.shared.releaseAll()
    }

    private func playReference(_ q: EarQuestion) {
        stopSound()
        PianoSynth.shared.noteOn([q.reference.midi])
    }

    /// Reference note, a short gap, then the two notes.
    private func play(_ q: EarQuestion) {
        stopSound()
        playback = Task {
            func hold(_ seconds: Double) async -> Bool {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return !Task.isCancelled
            }
            PianoSynth.shared.noteOn([q.reference.midi])
            guard await hold(0.9) else { return }
            PianoSynth.shared.releaseAll()
            guard await hold(0.5) else { return }
            if q.direction == .harmonic {
                PianoSynth.shared.noteOn([q.first.midi, q.second.midi])
                guard await hold(1.6) else { return }
            } else {
                PianoSynth.shared.noteOn([q.first.midi])
                guard await hold(0.8) else { return }
                PianoSynth.shared.releaseAll()
                PianoSynth.shared.noteOn([q.second.midi])
                guard await hold(1.0) else { return }
            }
            PianoSynth.shared.releaseAll()
        }
    }
}

struct EarTrainingSettingsView: View {
    @Binding var settings: EarTrainingSettings
    @Binding var autoNext: Bool
    @Binding var timedSeconds: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Question") {
                    Picker("Ask for", selection: $settings.style) {
                        Text("The two notes").tag(EarQuestionStyle.nameNotes)
                        Text("The interval").tag(EarQuestionStyle.nameInterval)
                    }
                    Toggle("White keys only", isOn: $settings.whiteKeysOnly)
                }
                Section("How the notes are played") {
                    ForEach(EarDirection.allCases, id: \.self) { dir in
                        Toggle(dir.displayName, isOn: Binding(
                            get: { settings.directions.contains(dir) },
                            set: { on in
                                if on { settings.directions.insert(dir) } else { settings.directions.remove(dir) }
                            }
                        ))
                    }
                }
                Section {
                    Toggle("Go to next question automatically", isOn: $autoNext)
                    Picker("Timed round length", selection: $timedSeconds) {
                        Text("30 seconds").tag(30)
                        Text("60 seconds").tag(60)
                        Text("2 minutes").tag(120)
                    }
                } header: {
                    Text("Play")
                } footer: {
                    Text("Timed rounds always move on automatically.")
                }
            }
            .navigationTitle("Ear training settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
