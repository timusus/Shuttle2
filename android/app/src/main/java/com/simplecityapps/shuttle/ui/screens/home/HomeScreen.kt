package com.simplecityapps.shuttle.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SectionHeaderStyle
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.text.resId

class HomeCallbacks(
    val onOpenSettings: () -> Unit = {},
    val onShuffleAll: () -> Unit = {},
    val onOpenWhatsNew: () -> Unit = {},
    val onDismissWhatsNew: () -> Unit = {},
    /** Pull to refresh: reloads the sections. */
    val onRefresh: () -> Unit = {},
    /** Opens an item's detail: an album, artist, playlist or genre. */
    val onOpenItem: (HomeItem) -> Unit = {},
    /** Plays, shuffles or queues an item: a play button, a genre tile's tap, or a TalkBack action. */
    val onAction: (MediaAction) -> Unit = {},
    val onShowActions: (MediaActionsTarget) -> Unit = {},
    /** A section's "See all", shown only for a section with somewhere to go ([HomeSectionId.hasSeeAll]). */
    val onSeeAll: (HomeSectionId) -> Unit = {},
)

/**
 * Home (#633): Jump back in as a grid, then the library's shelves; at cold start, Shuffle all and a line on how Home
 * fills in; or the empty state when there's no music yet. There's no resume hero: the mini player is that (#646).
 * The bar is Library's and Settings' large title bar, with Shuffle all and the Settings gear (#660). The first section
 * leads with a headline; every shelf after it takes the one title header. Search is its own tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    /** Shown in place of the generic empty state while the library has no songs (#422), so it can offer access. */
    emptyContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier,
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            S2LargeTopBar(
                title = stringResource(R.string.shell_tab_home),
                actions = {
                    // Cold start has its own, larger Shuffle all.
                    if (uiState is HomeUiState.Content && !uiState.coldStart) {
                        S2IconButton(icon = Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.home_shuffle_all), onClick = callbacks.onShuffleAll)
                    }
                    S2IconButton(icon = Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_menu_settings), onClick = callbacks.onOpenSettings)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection)
        when (uiState) {
            HomeUiState.Loading -> LoadingState(contentModifier)

            HomeUiState.Empty -> if (emptyContent != null) {
                emptyContent(contentModifier)
            } else {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    modifier = contentModifier,
                )
            }

            // The bar's scroll behaviour sits inside the pull, so pulling down expands the bar before it refreshes.
            is HomeUiState.Content -> PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = callbacks.onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                HomeContent(uiState, callbacks, Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection))
            }
        }
    }
}

/** Cold start: nothing played yet, so Home shows Recently added, Genre picks and a prominent Shuffle all. */
val HomeUiState.Content.coldStart: Boolean
    get() = sections.any { it.id == HomeSectionId.ShuffleAll }

/** Whether a section's header offers "See all": only where there's a page of the whole list to open. */
val HomeSectionId.hasSeeAll: Boolean
    get() = this == HomeSectionId.RecentlyAdded

/** At this font scale and above, text needs the width: the grid is one column and shelf tiles wrap their titles. */
private const val LARGE_TEXT_FONT_SCALE = 1.5f

/** Shelf tiles: about two and a half fit a phone, so the cut-off one says the row scrolls (#490); larger when wider. */
private val ShelfTileWidthCompact = 150.dp
private val ShelfTileWidthWide = 180.dp

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    callbacks: HomeCallbacks,
    modifier: Modifier,
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val largeText = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE
    val columns = jumpBackInColumns(widthAtLeastMedium = wide, largeText = largeText)
    val shelfTileWidth = if (wide) ShelfTileWidthWide else ShelfTileWidthCompact
    // The lead section's header is a headline; the rest are shelf titles, set apart by a wider gap.
    val leadSection = content.sections.firstOrNull { it.id != HomeSectionId.ShuffleAll && it.items.isNotEmpty() }?.id
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = S2Spacing.large)) {
        if (content.showWhatsNew) {
            item(key = "whats-new") { WhatsNewCard(callbacks, Modifier.animateItem()) }
        }
        // Cold start's Shuffle all leads, above the shelves: the one sure thing to do with a new library.
        if (content.coldStart) {
            item(key = HomeSectionId.ShuffleAll.name) { ColdStartCard(callbacks, Modifier.animateItem()) }
        }
        content.sections.forEach { section ->
            when {
                section.id == HomeSectionId.ShuffleAll || section.items.isEmpty() -> Unit

                section.id == HomeSectionId.JumpBackIn -> {
                    header(section, lead = section.id == leadSection, callbacks)
                    item(key = section.id.name) {
                        JumpBackInGrid(
                            items = section.items,
                            progress = section.progress,
                            covers = content.covers,
                            columns = columns,
                            showPlayButton = wide || largeText,
                            callbacks = callbacks,
                            modifier = Modifier.animateItem().padding(horizontal = S2Spacing.medium),
                        )
                    }
                }

                else -> shelf(section, lead = section.id == leadSection, content.covers, shelfTileWidth, largeText, callbacks)
            }
        }
    }
}

private fun LazyListScope.header(
    section: HomeSection,
    lead: Boolean,
    callbacks: HomeCallbacks,
) {
    item(key = "${section.id.name}:header") {
        SectionHeader(
            title = stringResource(section.title.stringRes),
            subtitle = section.subtitle?.let { stringResource(it.resId) },
            style = if (lead) SectionHeaderStyle.Headline else SectionHeaderStyle.Title,
            action = if (section.id.hasSeeAll) stringResource(R.string.home_see_all) else null,
            onAction = { callbacks.onSeeAll(section.id) },
            modifier = Modifier.animateItem().padding(top = if (lead) S2Spacing.xsmall else S2Spacing.medium),
        )
    }
}

/**
 * A horizontally scrolling row of [HomeShelfTile]s, each title and subtitle below its cover rather than over it, where
 * they'd clash with text printed on the art (#404). A shelf of more than one kind of item says which each is.
 */
private fun LazyListScope.shelf(
    section: HomeSection,
    lead: Boolean,
    covers: Map<String, List<Song>>,
    tileWidth: Dp,
    largeText: Boolean,
    callbacks: HomeCallbacks,
) {
    header(section, lead, callbacks)
    val mixed = section.items.map { it.kind }.distinct().size > 1
    item(key = section.id.name) {
        LazyRow(
            modifier = Modifier.animateItem(),
            contentPadding = PaddingValues(horizontal = S2Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
        ) {
            items(section.items, key = { it.key }) { item ->
                HomeShelfTile(
                    item = item,
                    covers = covers[item.key].orEmpty(),
                    mixed = mixed,
                    width = tileWidth,
                    largeText = largeText,
                    callbacks = callbacks,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val HomeSectionTitle.stringRes: Int
    @StringRes get() = when (this) {
        HomeSectionTitle.JumpBackIn -> R.string.home_jump_back_in
        HomeSectionTitle.ThisMorning -> R.string.home_this_morning
        HomeSectionTitle.ThisAfternoon -> R.string.home_this_afternoon
        HomeSectionTitle.Tonight -> R.string.home_tonight
        HomeSectionTitle.HeavyRotation -> R.string.home_heavy_rotation
        HomeSectionTitle.Rediscover -> R.string.home_rediscover
        HomeSectionTitle.RecentlyAdded -> R.string.home_recently_added
        HomeSectionTitle.GenrePicks -> R.string.home_genre_picks
        HomeSectionTitle.ShuffleAll -> R.string.home_shuffle_all
    }

/** Cold start: nothing played yet, so nothing to suggest from. A full-width Shuffle all and a line on how Home fills in. */
@Composable
private fun ColdStartCard(
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
        verticalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
    ) {
        S2Button(
            text = stringResource(R.string.home_shuffle_all),
            onClick = callbacks.onShuffleAll,
            icon = Icons.Rounded.Shuffle,
            size = S2ButtonSize.Medium,
            modifier = Modifier.fillMaxWidth().testTag(HOME_SHUFFLE_ALL_TAG),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.home_cold_start_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(HOME_COLD_START_HINT_TAG),
            )
        }
    }
}

const val HOME_SHUFFLE_ALL_TAG = "home.shuffleAll"
const val HOME_COLD_START_HINT_TAG = "home.coldStartHint"

@Composable
private fun WhatsNewCard(
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small)) {
        Row(modifier = Modifier.padding(start = S2Spacing.medium, top = S2Spacing.smallMedium), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.home_whats_new_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = S2Spacing.smallMedium),
            )
            S2IconButton(icon = Icons.Rounded.Close, contentDescription = stringResource(R.string.home_whats_new_dismiss), onClick = callbacks.onDismissWhatsNew)
        }
        Text(
            text = stringResource(R.string.home_whats_new_message, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = S2Spacing.medium),
        )
        S2Button(
            text = stringResource(R.string.home_whats_new_open),
            onClick = callbacks.onOpenWhatsNew,
            style = S2ButtonStyle.Text,
            modifier = Modifier.padding(S2Spacing.xsmall),
        )
    }
}
