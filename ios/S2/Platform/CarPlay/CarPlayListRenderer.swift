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
            CPListSection(items: model.rows.map { templateItem(for: $0, perform: perform) }, header: model.header, sectionIndexTitle: nil)
        }
    }

    /// A row with images is an image row (a Home shelf); any other a list item.
    static func templateItem(
        for row: CarPlayRow,
        perform: @escaping (CarPlayRowAction) -> Void
    ) -> any CPListTemplateItem {
        row.images.isEmpty ? item(for: row, perform: perform) : imageRow(for: row, perform: perform)
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

    /// A row of artwork under the row's title, each image titled: tapping an image performs its action, tapping the
    /// row the row's. Each image is drawn as its symbol until its artwork arrives.
    static func imageRow(
        for row: CarPlayRow,
        perform: @escaping (CarPlayRowAction) -> Void
    ) -> CPListImageRowItem {
        let loader = CarPlayArtworkLoader.shared
        let images = row.images.map { loader.cached($0.artwork, pixels: CarPlayArtworkLoader.imageRowPixels) ?? CarPlayArtworkLoader.placeholder($0.symbol) }
        let titles = row.images.map(\.title)
        let item: CPListImageRowItem
        if #available(iOS 26.0, *) {
            let elements = zip(images, titles).map { CPListImageRowItemRowElement(image: $0, title: $1, subtitle: nil) }
            item = CPListImageRowItem(text: row.title, elements: elements, allowsMultipleLines: false)
        } else if #available(iOS 17.4, *) {
            item = CPListImageRowItem(text: row.title, images: images, imageTitles: titles)
        } else {
            item = CPListImageRowItem(text: row.title, images: images)
        }
        let action = row.action
        item.handler = { _, completion in
            perform(action)
            completion()
        }
        let actions = row.images.map(\.action)
        item.listImageRowHandler = { _, index, completion in
            if actions.indices.contains(index) { perform(actions[index]) }
            completion()
        }
        loader.attach(row.images.map(\.artwork), shown: images, to: item)
        return item
    }
}
