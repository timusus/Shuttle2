import CarPlay
import UIKit

/// Turns `CarPlayCatalog`'s section models into CarPlay's own objects (#692, from Shuttle Podcasts).
///
/// The only file that knows both halves, and it holds no judgement of its own: what a row says, and what tapping it
/// does, was decided in `CarPlayCatalog`, where it can be tested.
@MainActor
enum CarPlayListRenderer {
    static func sections(
        _ models: [CarPlaySectionModel],
        perform: @escaping (CarPlayRowAction) -> Void
    ) -> [CPListSection] {
        models.map { model in
            CPListSection(items: model.rows.map { item(for: $0, perform: perform) }, header: model.header, sectionIndexTitle: nil)
        }
    }

    static func item(
        for row: CarPlayRow,
        perform: @escaping (CarPlayRowAction) -> Void
    ) -> CPListItem {
        let item = CPListItem(
            text: row.title,
            detailText: row.subtitle,
            image: row.symbol.flatMap { UIImage(systemName: $0) },
            accessoryImage: nil,
            accessoryType: row.accessory == .disclosure ? .disclosureIndicator : .none
        )
        if let progress = row.progress {
            item.playbackProgress = CGFloat(progress)
        }
        item.isPlaying = row.isPlaying
        item.isEnabled = row.isSelectable
        if row.isSelectable {
            let action = row.action
            item.handler = { _, completion in
                perform(action)
                completion()
            }
        }
        if let artwork = row.artwork {
            CarPlayArtworkLoader.shared.attach(artwork, to: item)
        }
        return item
    }
}
