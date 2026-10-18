import SwiftUI
import MusicTheory

@main
struct MusicTheoryApp: App {
    init() {
        PianoSynth.shared.prepare()
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
        }
    }
}

/// App-wide setting: how interval names are shown.
extension IntervalNotation {
    static let storageKey = "intervalNotation"
}
