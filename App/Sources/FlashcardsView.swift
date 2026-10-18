import SwiftUI
import MusicTheory

struct FlashcardsView: View {
    @AppStorage(IntervalNotation.storageKey) private var notation: IntervalNotation = .western
    @AppStorage("autoNext") private var autoNext = false
    @AppStorage("timedSeconds") private var timedSeconds = 60

    @State private var settings = IntervalQuizSettings()
    @State private var timed = false
    @State private var session = QuizSession(mode: .practice)
    @State private var question: IntervalQuestion?
    @State private var picked: QuizAnswer?
    @State private var questionID = 0
    @State private var now = Date()
    @State private var showSettings = false
    @State private var errorText: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    /// Timed rounds always move on by themselves.
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
                questionCard(question)
            } else if let errorText {
                Text(errorText).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding()
        .navigationTitle("Interval Flashcards")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                .accessibilityLabel("Game settings")
        }
        .sheet(isPresented: $showSettings, onDismiss: restart) {
            FlashcardSettingsView(settings: $settings, autoNext: $autoNext, timedSeconds: $timedSeconds)
        }
        .onChange(of: timed) { restart() }
        .onAppear { if question == nil { restart() } }
        // Ticks the countdown while a timed round runs.
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

    private func questionCard(_ question: IntervalQuestion) -> some View {
        VStack(spacing: 20) {
            Text(question.prompt(label: notation.label))
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 100)

            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(question.options, id: \.self) { option in
                    Button { choose(option, in: question) } label: {
                        Text(label(for: option))
                            .font(.title3)
                            .frame(maxWidth: .infinity, minHeight: 64)
                    }
                    .buttonStyle(.bordered)
                    .tint(tint(for: option, in: question))
                    .disabled(picked != nil)
                }
            }

            if let picked {
                feedback(picked: picked, question: question)
                if !advancesAutomatically {
                    Button("Next") { nextQuestion() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
            }
        }
    }

    @ViewBuilder
    private func feedback(picked: QuizAnswer, question: IntervalQuestion) -> some View {
        if question.isCorrect(picked) {
            Label("Correct", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
                .font(.headline)
        } else {
            Label("Answer: \(label(for: question.correct))", systemImage: "xmark.circle.fill")
                .foregroundStyle(.red)
                .font(.headline)
        }
    }

    private var results: some View {
        VStack(spacing: 16) {
            Text("Time's up!").font(.largeTitle.bold())
            Text("\(session.correct) right out of \(session.answered)")
                .font(.title2)
            if let accuracy = session.accuracy {
                Text("\(accuracy)% accuracy").foregroundStyle(.secondary)
            }
            Button("Play again") { restart() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
        }
        .padding(.top, 40)
    }

    private func label(for option: QuizAnswer) -> String {
        switch option {
        case let .note(n): return n.description
        case let .interval(iv): return notation.label(for: iv)
        }
    }

    private func tint(for option: QuizAnswer, in question: IntervalQuestion) -> Color? {
        guard let picked else { return nil }
        if question.isCorrect(option) { return .green }
        if option == picked { return .red }
        return nil
    }

    private func choose(_ option: QuizAnswer, in question: IntervalQuestion) {
        guard picked == nil, !isOver else { return }
        picked = option
        let right = question.isCorrect(option)
        session.record(correct: right)
        guard advancesAutomatically else { return }
        // Leave a wrong answer on screen longer so the right one can be read.
        let delay: UInt64 = right ? 600_000_000 : (timed ? 1_200_000_000 : 1_800_000_000)
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
            question = try IntervalQuizGenerator(settings: settings).next()
            errorText = nil
        } catch {
            question = nil
            errorText = "No questions match these settings. Try turning more options on."
        }
    }
}

struct FlashcardSettingsView: View {
    @Binding var settings: IntervalQuizSettings
    @Binding var autoNext: Bool
    @Binding var timedSeconds: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Notes") {
                    Toggle("White keys only", isOn: $settings.whiteKeysOnly)
                    Toggle("Include 9ths, 11ths, 13ths", isOn: $settings.includeCompound)
                }
                Section("Question types") {
                    kindToggle(.noteAbove, "Note above (C + M3 = ?)")
                    kindToggle(.rootBelow, "Root below (C is the 5th of ?)")
                    kindToggle(.nameInterval, "Name the interval (C → E = ?)")
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
            .navigationTitle("Flashcard settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }

    private func kindToggle(_ kind: IntervalQuestionKind, _ title: String) -> some View {
        Toggle(title, isOn: Binding(
            get: { settings.kinds.contains(kind) },
            set: { on in
                if on { settings.kinds.insert(kind) } else { settings.kinds.remove(kind) }
            }
        ))
    }
}
