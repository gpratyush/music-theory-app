import SwiftUI
import MusicTheory

private let presets: [(name: String, text: String)] = [
    ("ii – V – I in C", "Dm7 - G7 - Cmaj7"),
    ("Pop (I–V–vi–IV)", "C - G - Am - F"),
    ("Jazz turnaround", "Cmaj7 - Am7 - Dm7 - G7"),
    ("Minor ii – V – i", "Dm7b5 - G7 - Cm7"),
    ("Secondary dominants", "C - A7 - Dm - D7 - G7 - C"),
    ("Your example", "Cm7 - Am7 - D7 - Gmaj7"),
]

struct ProgressionsView: View {
    @StateObject private var player = ProgressionPlayer()

    @AppStorage("progression.text") private var text = "Cm7 - Am7 - D7 - Gmaj7"
    @AppStorage("progression.bpm") private var bpm = 90
    @AppStorage("progression.beats") private var beatsPerChord = 4
    @AppStorage("progression.loop") private var loop = true
    @AppStorage("progression.key") private var keyIndex = -1     // -1 = no key, else index into keys
    @AppStorage("progression.saved") private var savedJSON = "[]"

    @State private var selected: Int?
    @FocusState private var editing: Bool

    private static let keys: [NoteName] = [
        NoteName(.c), NoteName(.d, -1), NoteName(.d), NoteName(.e, -1), NoteName(.e), NoteName(.f),
        NoteName(.f, 1), NoteName(.g), NoteName(.a, -1), NoteName(.a), NoteName(.b, -1), NoteName(.b),
    ]

    private var parsed: Result<[Chord], ProgressionParseError> {
        do { return .success(try ProgressionParser.parse(text)) }
        catch let e as ProgressionParseError { return .failure(e) }
        catch { return .failure(.empty) }
    }

    private var chords: [Chord] { (try? parsed.get()) ?? [] }

    private var saved: [String] {
        get { (try? JSONDecoder().decode([String].self, from: Data(savedJSON.utf8))) ?? [] }
    }

    private func setSaved(_ list: [String]) {
        if let data = try? JSONEncoder().encode(list) { savedJSON = String(decoding: data, as: UTF8.self) }
    }

    /// The chord lit up on the keyboard: the playing one, else the one last tapped.
    private var shownChord: Chord? {
        let i = player.currentIndex ?? selected
        guard let i, chords.indices.contains(i) else { return nil }
        return chords[i]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                editor
                chordChips
                PianoKeyboardView(highlighted: Set(shownChord?.voicing ?? []))
                transport
                options
                savedList
            }
            .padding()
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Progressions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { editing = false }
            }
        }
        .onDisappear { player.stop() }
    }

    private var editor: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Chords").font(.headline)
                Spacer()
                Menu("Examples") {
                    ForEach(presets, id: \.name) { p in
                        Button(p.name) { text = p.text; player.stop() }
                    }
                }
            }
            TextField("e.g. Cm7 - Am7 - D7 - Gmaj7", text: $text, axis: .vertical)
                .font(.title3.monospaced())
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($editing)
                .padding(10)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                .onChange(of: text) { player.stop() }
            if case let .failure(error) = parsed {
                Text(error.description).font(.footnote).foregroundStyle(.red)
            }
        }
    }

    private var chordChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(chords.enumerated()), id: \.offset) { i, chord in
                    let active = (player.currentIndex ?? selected) == i
                    Button {
                        selected = i
                        player.audition(chord)
                    } label: {
                        VStack(spacing: 2) {
                            Text(chord.description).font(.headline)
                            if keyIndex >= 0 {
                                Text(chord.romanNumeral(inMajorKey: Self.keys[keyIndex]))
                                    .font(.caption)
                                    .foregroundStyle(active ? Color.white : Color.secondary)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(active ? Color.accentColor : Color.secondary.opacity(0.15),
                                    in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(active ? Color.white : Color.primary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var transport: some View {
        HStack {
            Button {
                if player.isPlaying { player.stop() }
                else { player.play(chords, bpm: bpm, beatsPerChord: beatsPerChord, loop: loop) }
            } label: {
                Label(player.isPlaying ? "Stop" : "Play",
                      systemImage: player.isPlaying ? "stop.fill" : "play.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(chords.isEmpty)

            Button {
                var list = saved
                if !list.contains(text) { list.insert(text, at: 0) }
                setSaved(list)
            } label: {
                Image(systemName: "bookmark")
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .disabled(chords.isEmpty)
            .accessibilityLabel("Save progression")
        }
    }

    private var options: some View {
        VStack(spacing: 10) {
            Stepper("Tempo: \(bpm) BPM", value: $bpm, in: 40...200, step: 5)
            Picker("Beats per chord", selection: $beatsPerChord) {
                Text("1 beat").tag(1)
                Text("2 beats").tag(2)
                Text("4 beats").tag(4)
            }
            .pickerStyle(.segmented)
            Toggle("Loop", isOn: $loop)
            Picker("Show Roman numerals in", selection: $keyIndex) {
                Text("Off").tag(-1)
                ForEach(Self.keys.indices, id: \.self) { i in
                    Text("\(Self.keys[i].description) major").tag(i)
                }
            }
        }
        .onChange(of: bpm) { player.stop() }
        .onChange(of: beatsPerChord) { player.stop() }
    }

    @ViewBuilder private var savedList: some View {
        if !saved.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Saved").font(.headline)
                ForEach(saved, id: \.self) { item in
                    HStack {
                        Button(item) { text = item }
                            .font(.body.monospaced())
                            .lineLimit(1)
                        Spacer()
                        Button(role: .destructive) {
                            setSaved(saved.filter { $0 != item })
                        } label: {
                            Image(systemName: "trash")
                        }
                        .accessibilityLabel("Delete \(item)")
                    }
                }
            }
        }
    }
}
