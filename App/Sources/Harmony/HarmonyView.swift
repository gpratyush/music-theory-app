import SwiftUI
import MusicTheory

struct HarmonyView: View {
    @AppStorage("autoNext") private var autoNext = false
    @AppStorage("timedSeconds") private var timedSeconds = 60

    @State private var settings = HarmonySettings()
    @State private var timed = false
    @State private var session = QuizSession(mode: .practice)
    @State private var question: HarmonyQuestion?
    @State private var selection: Set<Int> = []
    @State private var revealed = false
    @State private var questionID = 0
    @State private var now = Date()
    @State private var showSettings = false
    @State private var errorText: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
    private var advancesAutomatically: Bool { timed || autoNext }
    private var isOver: Bool { session.isOver(at: now) }

    var body: some View {
        ScrollView {
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
            }
            .padding()
        }
        .navigationTitle("Harmony")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                .accessibilityLabel("Game settings")
        }
        .sheet(isPresented: $showSettings, onDismiss: restart) {
            HarmonySettingsView(settings: $settings, autoNext: $autoNext, timedSeconds: $timedSeconds)
        }
        .onChange(of: timed) { restart() }
        .onAppear { if question == nil { restart() } }
        .onDisappear { PianoSynth.shared.releaseAll() }
        .task(id: session.startedAt) {
            guard timed else { return }
            while !Task.isCancelled && !session.isOver(at: Date()) {
                now = Date()
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
            now = Date()
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

    private func card(_ q: HarmonyQuestion) -> some View {
        VStack(spacing: 16) {
            Text(q.prompt)
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 70)

            Button { play(q.chord) } label: { Label("Hear \(q.chord.description)", systemImage: "speaker.wave.2.fill") }
                .buttonStyle(.bordered)
                .opacity(q.kind == .nameChord && !revealed ? 0 : 1)   // don't give the name away
                .disabled(q.kind == .nameChord && !revealed)

            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(q.options.indices, id: \.self) { i in
                    Button { tap(i, in: q) } label: {
                        Text(q.options[i])
                            .font(.body.weight(.medium))
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity, minHeight: 56)
                    }
                    .buttonStyle(.bordered)
                    .tint(tint(i, in: q))
                    .disabled(revealed)
                }
            }

            if q.isMultiSelect && !revealed {
                Button("Check") { reveal(q) }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(selection.isEmpty)
            }

            if revealed {
                Label(q.isCorrect(selection) ? "Correct" : "Not quite",
                      systemImage: q.isCorrect(selection) ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.headline)
                    .foregroundStyle(q.isCorrect(selection) ? Color.green : Color.red)
                Text(q.explanation)
                    .font(.callout)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                if !advancesAutomatically {
                    Button("Next") { nextQuestion() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
            }
        }
    }

    private func tint(_ i: Int, in q: HarmonyQuestion) -> Color? {
        if !revealed { return selection.contains(i) ? .accentColor : nil }
        if q.correct.contains(i) { return .green }
        if selection.contains(i) { return .red }
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

    private func tap(_ i: Int, in q: HarmonyQuestion) {
        guard !revealed, !isOver else { return }
        if q.isMultiSelect {
            if selection.contains(i) { selection.remove(i) } else { selection.insert(i) }
        } else {
            selection = [i]
            reveal(q)
        }
    }

    private func reveal(_ q: HarmonyQuestion) {
        revealed = true
        let right = q.isCorrect(selection)
        session.record(correct: right)
        play(q.chord)
        guard advancesAutomatically else { return }
        // Wrong answers stay up longer so the explanation can be read.
        let delay: UInt64 = right ? 1_000_000_000 : 3_000_000_000
        let id = questionID
        Task {
            try? await Task.sleep(nanoseconds: delay)
            if id == questionID { nextQuestion() }
        }
    }

    private func play(_ chord: Chord) {
        PianoSynth.shared.releaseAll()
        PianoSynth.shared.noteOn(chord.voicing)
    }

    private func restart() {
        session = QuizSession(mode: timed ? .timed(seconds: timedSeconds) : .practice)
        now = Date()
        nextQuestion()
    }

    private func nextQuestion() {
        selection = []
        revealed = false
        questionID += 1
        do {
            question = try HarmonyQuizGenerator(settings: settings).next()
            errorText = nil
        } catch {
            question = nil
            errorText = "No questions match these settings. Try turning more options on."
        }
    }
}

struct HarmonySettingsView: View {
    @Binding var settings: HarmonySettings
    @Binding var autoNext: Bool
    @Binding var timedSeconds: Int
    @Environment(\.dismiss) private var dismiss

    private struct Group: Identifiable {
        let id: String
        let qualities: [ChordQuality]
    }

    private static let groups: [Group] = [
        Group(id: "Triads", qualities: [.major, .minor, .diminished, .augmented]),
        Group(id: "7th chords", qualities: [.dominant7, .major7, .minor7, .halfDiminished7, .diminished7, .minorMajor7]),
        Group(id: "Suspended", qualities: [.sus2, .sus4, .dominant7sus4]),
        Group(id: "Add chords", qualities: [.add2, .add4, .major6, .minor6]),
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("Question types") {
                    ForEach(HarmonyQuestionKind.allCases, id: \.self) { kind in
                        Toggle(kind.displayName, isOn: Binding(
                            get: { settings.kinds.contains(kind) },
                            set: { on in
                                if on { settings.kinds.insert(kind) } else { settings.kinds.remove(kind) }
                            }
                        ))
                    }
                }
                Section {
                    ForEach(Self.groups) { group in
                        Toggle(group.id, isOn: Binding(
                            get: { group.qualities.allSatisfy { settings.qualities.contains($0) } },
                            set: { on in
                                settings.qualities.removeAll { group.qualities.contains($0) }
                                if on { settings.qualities += group.qualities }
                            }
                        ))
                    }
                    Toggle("Roots on white keys only", isOn: $settings.naturalRootsOnly)
                } header: {
                    Text("Chords")
                } footer: {
                    Text("Secondary-dominant questions always use dominant 7th chords.")
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
                }
            }
            .navigationTitle("Harmony settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
