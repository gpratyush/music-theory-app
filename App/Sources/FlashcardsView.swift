import SwiftUI
import MusicTheory

struct FlashcardsView: View {
    @AppStorage(IntervalNotation.storageKey) private var notation: IntervalNotation = .western

    @State private var settings = IntervalQuizSettings()
    @State private var question: IntervalQuestion?
    @State private var picked: QuizAnswer?
    @State private var score = 0
    @State private var asked = 0
    @State private var showSettings = false
    @State private var errorText: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        VStack(spacing: 24) {
            Text("\(score) / \(asked)")
                .font(.headline)
                .foregroundStyle(.secondary)

            if let question {
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

                if picked != nil {
                    Button("Next") { nextQuestion() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
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
        .sheet(isPresented: $showSettings, onDismiss: nextQuestion) {
            FlashcardSettingsView(settings: $settings)
        }
        .onAppear { if question == nil { nextQuestion() } }
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
        picked = option
        asked += 1
        if question.isCorrect(option) { score += 1 }
    }

    private func nextQuestion() {
        picked = nil
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
