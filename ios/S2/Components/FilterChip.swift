import SwiftUI

/// A capsule chip that's on or off: filled with the artwork tint when selected, a tertiary fill otherwise. The
/// Library's category rail picks one; Search's type filters toggle several.
struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let identifier: String
    let action: () -> Void

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var ink

    var body: some View {
        Button(action: action) {
            Text(title)
                .lineLimit(1)
                .padding(.horizontal, Spacing.smallMedium + Spacing.xsmall)
                .padding(.vertical, Spacing.small)
                .foregroundStyle(isSelected ? ink : .primary)
                .background(Capsule().fill(isSelected ? AnyShapeStyle(tint) : AnyShapeStyle(Color(.tertiarySystemFill))))
                .contentShape(Capsule())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier(identifier)
    }
}
