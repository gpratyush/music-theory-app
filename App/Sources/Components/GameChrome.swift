import SwiftUI
import UIKit
import MusicTheory

// Shared pieces every quiz game uses, so they all look and feel the same.

/// How an answer button looks right now.
enum AnswerState {
    case normal, selected, correct, wrong
}

/// A big, rounded answer button that turns green or red once answered.
struct AnswerButton: View {
    let title: String
    let state: AnswerState
    var minHeight: CGFloat = 60
    var font: Font = .title3.weight(.medium)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let symbol {
                    Image(systemName: symbol).imageScale(.small)
                }
                Text(title)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.7)
            }
            .font(font)
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: minHeight)
            .padding(.horizontal, 8)
            .background(background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(border, lineWidth: state == .normal ? 1 : 2)
            )
            .scaleEffect(state == .correct ? 1.03 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: state)
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(title)
        .accessibilityValue(accessibilityValue)
    }

    private var symbol: String? {
        switch state {
        case .correct: return "checkmark"
        case .wrong: return "xmark"
        default: return nil
        }
    }

    private var foreground: Color {
        switch state {
        case .normal: return Color.primary
        case .selected: return Color.accentColor
        case .correct: return Color.green
        case .wrong: return Color.red
        }
    }

    private var background: Color {
        switch state {
        case .normal: return Color(.secondarySystemBackground)
        case .selected: return Color.accentColor.opacity(0.15)
        case .correct: return Color.green.opacity(0.15)
        case .wrong: return Color.red.opacity(0.15)
        }
    }

    private var border: Color {
        switch state {
        case .normal: return Color(.separator)
        case .selected: return Color.accentColor
        case .correct: return Color.green
        case .wrong: return Color.red
        }
    }

    private var accessibilityValue: String {
        switch state {
        case .normal: return ""
        case .selected: return "Selected"
        case .correct: return "Correct answer"
        case .wrong: return "Wrong"
        }
    }
}

/// Shrinks a little while pressed.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Practice / Timed switch plus the score or countdown under it.
struct GameModeHeader: View {
    @Binding var timed: Bool
    let timedSeconds: Int
    let session: QuizSession
    let now: Date

    var body: some View {
        VStack(spacing: 12) {
            Picker("Mode", selection: $timed) {
                Text("Practice").tag(false)
                Text("Timed \(timedSeconds)s").tag(true)
            }
            .pickerStyle(.segmented)

            if let left = session.remaining(at: now) {
                VStack(spacing: 6) {
                    HStack {
                        Label("\(left)s", systemImage: "timer")
                            .foregroundStyle(left <= 10 ? Color.red : Color.primary)
                            .contentTransition(.numericText())
                        Spacer()
                        Label("\(session.correct)", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(Color.green)
                            .contentTransition(.numericText())
                    }
                    .font(.headline.monospacedDigit())
                    ProgressView(value: Double(left), total: Double(max(timedSeconds, 1)))
                        .tint(left <= 10 ? Color.red : Color.accentColor)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(left) seconds left, \(session.correct) right")
            } else {
                HStack {
                    Label("\(session.correct) / \(session.answered)", systemImage: "checkmark.circle")
                        .contentTransition(.numericText())
                    Spacer()
                    if let accuracy = session.accuracy {
                        Text("\(accuracy)%")
                    }
                }
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(.secondary)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(session.correct) right out of \(session.answered)")
            }
        }
    }
}

/// End-of-round summary for timed mode.
struct RoundResultsView: View {
    let session: QuizSession
    let onPlayAgain: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "flag.checkered")
                .font(.system(size: 48))
                .foregroundStyle(Color.accentColor)
            Text("Time's up!").font(.largeTitle.bold())
            Text("\(session.correct) right")
                .font(.system(size: 56, weight: .bold, design: .rounded).monospacedDigit())
            Text("out of \(session.answered) answered")
                .font(.title3)
                .foregroundStyle(.secondary)
            if let accuracy = session.accuracy {
                Text("\(accuracy)% accuracy")
                    .font(.headline)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color(.secondarySystemBackground), in: Capsule())
            }
            Button(action: onPlayAgain) {
                Label("Play again", systemImage: "arrow.clockwise")
                    .frame(minWidth: 180)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.top, 8)
        }
        .padding(.top, 32)
        .frame(maxWidth: .infinity)
    }
}

/// "Correct" or "It was X" line shown after answering.
struct FeedbackBanner: View {
    let isCorrect: Bool
    let text: String

    var body: some View {
        Label(text, systemImage: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
            .font(.headline)
            .foregroundStyle(isCorrect ? Color.green : Color.red)
            .symbolEffect(.bounce, value: isCorrect)
            .transition(.scale.combined(with: .opacity))
    }
}

/// The big "Next" button for practice mode.
struct NextButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label("Next", systemImage: "arrow.right").frame(minWidth: 160)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
    }
}

/// A light buzz for right and wrong answers.
enum Haptics {
    static func answer(correct: Bool) {
        UINotificationFeedbackGenerator().notificationOccurred(correct ? .success : .error)
    }
}

/// Answer state for single-pick questions: green on the right answer, red on a wrong pick.
func answerState(isRight: Bool, isPicked: Bool, revealed: Bool) -> AnswerState {
    guard revealed else { return .normal }
    if isRight { return .correct }
    if isPicked { return .wrong }
    return .normal
}
