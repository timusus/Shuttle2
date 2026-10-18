package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.shared.downloads.OfflineDownloads
import com.simplecityapps.shuttle.shared.downloads.UrlSessionDownloads
import com.simplecityapps.shuttle.shared.network.ServerRequestPolicy
import com.simplecityapps.shuttle.ui.actions.ObservePlayablePlaylists
import com.simplecityapps.shuttle.ui.screens.sources.servers.PrepareServerConnection
import com.simplecityapps.shuttle.ui.screens.sources.servers.ReadServerLogin

/** Streaming servers' connections and offline downloads; part of [IosAppGraph]. */
interface IosServerAccessors {
    /** Offline downloads' state, which detail headers show and whose actions they offer. Swift builds it at launch. */
    val offlineDownloads: OfflineDownloads

    /** Which playlists have a song that plays offline, given the completed downloads' paths; CarPlay's offline Home (#925). */
    val observePlayablePlaylists: ObservePlayablePlaylists

    /** Offline downloads' background `URLSession`, which the app delegate hands its background events to. */
    val urlSessionDownloads: UrlSessionDownloads

    /** A server's custom headers and pinned certificate, for the sessions Swift makes itself (streaming, artwork; #921). */
    val serverRequestPolicy: ServerRequestPolicy

    /** Saves a server's custom headers, and trusts a certificate for it: the sign-in's, and what tests seed [serverRequestPolicy] with. */
    val prepareServerConnection: PrepareServerConnection
    val serverConnections: ServerConnectionStore

    /** A signed-in Jellyfin, Emby or Plex server's saved address and user, for its row and detail in Sources. */
    val readServerLogin: ReadServerLogin
}
