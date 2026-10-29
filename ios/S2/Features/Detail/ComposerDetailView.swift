import Shared
import SwiftUI

struct ComposerDetailView: View {
    let name: String

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.composer(name: name)
        let models = services.viewModels.viewModel(route.cacheKey) {
            ComposerDetailModels(graph: services.graph, composer: name)
        }
        Observing(models.composer.uiState, models.actions.uiState) { state, actions in
            ComposerDetailContent(
                state: state,
                onOpenAlbum: { navigator.openAsserting(.album(albumKey: $0.key, albumArtistKey: $0.albumArtistGroupKey?.key, albumIdentity: $0.identity)) },
                albumActions: DetailAlbumActions(
                    onPlay: { models.actions.send(MediaActionPlay(selection: MediaSelectionAlbums(album: $0), position: 0), intent: services.playIntent) },
                    onPlayNext: { models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) },
                    onAddToQueue: { models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) }
                )
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
    }
}

/// The Composer screen's ViewModel, cached under its route's key.
final class ComposerDetailModels: ViewModelGroup {
    let composer: ComposerViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, composer: String) {
        self.composer = graph.composerViewModelFactory.create(composer: composer)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [composer, actions] }
}

/// A composer's page from a `ComposerUiState`, as Android's `ComposerDetailScreen`: their classical works, one row per
/// work across every recording, then the albums holding them. A work with one recording opens its album, one with
/// several asks which recording first.
struct ComposerDetailContent: View {
    let state: ComposerUiState
    var onOpenAlbum: (AlbumGroupKey) -> Void = { _ in }
    var albumActions = DetailAlbumActions()

    @State private var recordingsOf: ComposerWork?

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let composer = state.composer {
            DetailScaffold(title: composer.name, tintSource: nil) { _ in
                ComposerHero(name: composer.name, subtitle: eyebrow(localizedPlural("composer_works", composer.works.count), localizedPlural("composer_albums", composer.albums.count)))
            } rows: {
                Section {
                    SectionHeader(localizedString("composer_works_header"))
                        .rowSeparator(.none)
                    ForEach(composer.works, id: \.key) { work in
                        Button {
                            if work.recordings.count == 1, let only = work.recordings.first {
                                onOpenAlbum(only.groupKey)
                            } else {
                                recordingsOf = work
                            }
                        } label: {
                            ComposerWorkRow(work: work)
                        }
                        .buttonStyle(.plain)
                    }
                }
                if !state.albums.isEmpty {
                    DetailAlbumShelf(title: localizedString("composer_albums_header"), albums: state.albums, subtitle: { $0.albumArtist }, onAlbumTap: { album in
                        if let key = album.groupKey { onOpenAlbum(key) }
                    }, albumActions: albumActions)
                }
            }
            .sheet(item: $recordingsOf) { work in
                ComposerRecordingsSheet(work: work) { key in
                    recordingsOf = nil
                    onOpenAlbum(key)
                }
            }
        } else {
            EmptyState(localizedString("library_item_not_found"), systemImage: "music.quarternote.3")
        }
    }
}

extension ComposerWork: @retroactive Identifiable {
    public var id: String { key }
}

private struct ComposerHero: View {
    let name: String
    let subtitle: String

    @Environment(\.artworkTint) private var tint

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            Text(localizedString("composer_eyebrow").uppercased())
                .font(.s2Eyebrow)
                .tracking(1.5)
                .foregroundStyle(tint)
            Text(name)
                .font(.s2HeroTitle)
                .accessibilityAddTraits(.isHeader)
                .detailTitleProbe()
            Text(subtitle)
                .font(.s2Eyebrow)
                .foregroundStyle(.s2TextSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ComposerWorkRow: View {
    let work: ComposerWork

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(work.title)
                    .textRole(.rowTitle)
                    .lineLimit(2)
                Text(eyebrow(work.catalogue, localizedPlural("composer_recordings", work.recordings.count)))
                    .textRole(.rowSubtitle)
                    .foregroundStyle(.s2TextSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: Spacing.small)
            Image(systemName: "chevron.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.s2TextSecondary)
                .accessibilityHidden(true)
        }
        .contentShape(Rectangle())
    }
}

/// The recordings of one work, each opening its album: the album, then the lead performer and year.
struct ComposerRecordingsSheet: View {
    let work: ComposerWork
    let onOpen: (AlbumGroupKey) -> Void

    var body: some View {
        NavigationStack {
            List(Array(work.recordings.enumerated()), id: \.offset) { _, recording in
                Button { onOpen(recording.groupKey) } label: {
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(recording.album ?? "Unknown Album")
                            .textRole(.rowTitle)
                            .lineLimit(1)
                        let credit = eyebrow(recording.leadPerformer, recording.year.map { String($0.intValue) })
                        if !credit.isEmpty {
                            Text(credit)
                                .textRole(.rowSubtitle)
                                .foregroundStyle(.s2TextSecondary)
                                .lineLimit(1)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .listStyle(.plain)
            .navigationTitle(work.title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
