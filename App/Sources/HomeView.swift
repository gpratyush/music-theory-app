import SwiftUI
import MusicTheory

struct Game: Identifiable {
    let id: String
    let title: String
    let symbol: String
    let isAvailable: Bool
}

private let games: [Game] = [
    Game(id: "flashcards", title: "Interval Flashcards", symbol: "rectangle.on.rectangle", isAvailable: true),
    Game(id: "ear", title: "Ear Training", symbol: "ear", isAvailable: true),
    Game(id: "harmony", title: "Harmony", symbol: "pianokeys", isAvailable: true),
    Game(id: "chordEar", title: "Chord Ear Training", symbol: "waveform", isAvailable: false),
    Game(id: "progressions", title: "Progressions", symbol: "music.note.list", isAvailable: true),
]

struct HomeView: View {
    @State private var showSettings = false

    private let columns = [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(games) { game in
                        if game.isAvailable {
                            NavigationLink(value: game.id) { GameTile(game: game) }
                                .buttonStyle(.plain)
                        } else {
                            GameTile(game: game)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Learn")
            .navigationDestination(for: String.self) { id in
                switch id {
                case "flashcards": FlashcardsView()
                case "progressions": ProgressionsView()
                case "ear": EarTrainingView()
                case "harmony": HarmonyView()
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
        VStack(spacing: 12) {
            Image(systemName: game.symbol)
                .font(.largeTitle)
            Text(game.title)
                .font(.headline)
                .multilineTextAlignment(.center)
            if !game.isAvailable {
                Text("Coming soon")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 140)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .opacity(game.isAvailable ? 1 : 0.5)
        .accessibilityElement(children: .combine)
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
            }
            .navigationTitle("Settings")
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}
