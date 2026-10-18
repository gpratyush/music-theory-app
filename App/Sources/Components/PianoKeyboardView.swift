import SwiftUI

/// A read-only piano keyboard that lights up the given MIDI notes.
struct PianoKeyboardView: View {
    var range: ClosedRange<Int> = 36...83   // C2 to B5
    var highlighted: Set<Int> = []
    var whiteKeyWidth: CGFloat = 22
    var height: CGFloat = 110

    private static let blackPitchClasses: Set<Int> = [1, 3, 6, 8, 10]

    private var whiteKeys: [Int] { range.filter { !Self.blackPitchClasses.contains($0 % 12) } }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            ZStack(alignment: .topLeading) {
                HStack(spacing: 0) {
                    ForEach(whiteKeys, id: \.self) { note in
                        Rectangle()
                            .fill(highlighted.contains(note) ? Color.accentColor : Color.white)
                            .overlay(Rectangle().stroke(Color.black.opacity(0.6), lineWidth: 0.5))
                            .overlay(alignment: .bottom) {
                                if note % 12 == 0 {
                                    Text("C\(note / 12 - 1)")
                                        .font(.system(size: 9))
                                        .foregroundStyle(.black.opacity(0.6))
                                        .padding(.bottom, 4)
                                }
                            }
                            .frame(width: whiteKeyWidth, height: height)
                    }
                }
                ForEach(blackKeyPositions, id: \.note) { key in
                    Rectangle()
                        .fill(highlighted.contains(key.note) ? Color.accentColor : Color.black)
                        .overlay(Rectangle().stroke(Color.black, lineWidth: 0.5))
                        .frame(width: whiteKeyWidth * 0.6, height: height * 0.62)
                        .offset(x: key.x)
                }
            }
            .padding(.horizontal, 4)
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private struct BlackKey { let note: Int; let x: CGFloat }

    private var blackKeyPositions: [BlackKey] {
        var result: [BlackKey] = []
        var whiteIndex = 0
        for note in range {
            if Self.blackPitchClasses.contains(note % 12) {
                // Sits across the line between the previous white key and the next.
                result.append(BlackKey(note: note, x: CGFloat(whiteIndex) * whiteKeyWidth - whiteKeyWidth * 0.3))
            } else {
                whiteIndex += 1
            }
        }
        return result.filter { $0.x >= 0 }
    }
}
