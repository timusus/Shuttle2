import SwiftUI

/// Placeholder mini player: the bar every tab's screens inset at the bottom. Tapping it opens Now
/// Playing. Real state arrives with the iOS player controller (#588).
struct MiniPlayerView: View {
    @Binding var showNowPlaying: Bool

    var body: some View {
        Button {
            showNowPlaying = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "music.note")
                    .frame(width: 40, height: 40)
                    .background(.quaternary, in: RoundedRectangle(cornerRadius: 6))
                Text("Not Playing")
                    .foregroundStyle(.secondary)
                Spacer()
                Image(systemName: "play.fill")
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(.bar)
        .accessibilityIdentifier("miniPlayer")
    }
}
