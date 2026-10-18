import SwiftUI
import MusicTheory

struct Game: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let symbol: String
    let color: Color
}

private let games: [Game] = [
    Game(id: "flashcards", title: "Interval Flashcards", subtitle: "Find notes and name intervals",
         symbol: "rectangle.on.rectangle.angled", color: .blue),
    Game(id: "ear", title: "Ear Training", subtitle: "Hear two notes, name them",
         symbol: "ear", color: .purple),
    Game(id: "harmony", title: "Harmony", subtitle: "Chords, keys and modes",
         symbol: "pianokeys", color: .orange),
    Game(id: "chordEar", title: "Chord Ear Training", subtitle: "Hear a chord, name its type",
         symbol: "waveform", color: .pink),
    Game(id: "progressions", title: "Progressions", subtitle: "Type chords, hear them played",
         symbol: "music.note.list", color: .teal),
]

struct HomeView: View {
    @State private var showSettings = false

    private let columns = [GridItem(.adaptive(minimum: 160), spacing: 16)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(games) { game in
                        NavigationLink(value: game.id) { GameTile(game: game) }
                            .buttonStyle(PressableStyle())
                    }
                }
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Learn")
            .navigationDestination(for: String.self) { id in
                switch id {
                case "flashcards": FlashcardsView()
                case "progressions": ProgressionsView()
                case "ear": EarTrainingView()
                case "harmony": HarmonyView()
                case "chordEar": ChordEarView()
                default: Text("Coming soon")
                }
            }
            .toolbar {
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Settings")
            }
            .sheet(isPresented: $showSettings) { AppSettingsView() }
        }
    }
}

struct GameTile: View {
    let game: Game

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: game.symbol)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(game.color.gradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            Spacer(minLength: 0)
            Text(game.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .multilineTextAlignment(.leading)
            Text(game.subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
        .padding()
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 8, y: 4)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens the game")
    }
}

struct AppSettingsView: View {
    @AppStorage(IntervalNotation.storageKey) private var notation: IntervalNotation = .western
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Interval names", selection: $notation) {
                        ForEach(IntervalNotation.allCases, id: \.self) { n in
                            Text(n.displayName).tag(n)
                        }
                    }
                } footer: {
                    Text("Example: \(notation.label(for: Interval(.perfect, 5)))")
                }
                Section("Credits") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Piano sound")
                        Text("Salamander Grand Piano by Alexander Holm, licensed CC BY 3.0.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Link("License details", destination: URL(string: "https://creativecommons.org/licenses/by/3.0/")!)
                }
            }
            .navigationTitle("Settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
