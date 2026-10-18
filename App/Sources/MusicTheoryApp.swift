import SwiftUI
import MusicTheory

@main
struct MusicTheoryApp: App {
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
