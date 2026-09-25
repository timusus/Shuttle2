package com.simplecityapps.shuttle.ui.screens.sources.servers

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.emby.EmbyConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.sources.servers.jellyfin.JellyfinConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.sources.servers.plex.PlexConfigurationFragment

/** The fragment result a server's sign-in dialog sets once it has signed in, carrying the server's type. */
const val SERVER_CONNECTED_REQUEST = "server_connected"
private const val SERVER_TYPE = "server_type"

internal fun serverConnectedResult(type: MediaProviderType): Bundle = bundleOf(SERVER_TYPE to type.name)

fun Bundle.connectedServerType(): MediaProviderType? = getString(SERVER_TYPE)?.let { name -> MediaProviderType.entries.firstOrNull { it.name == name } }

/** Shows the existing sign-in dialog for a Jellyfin, Emby or Plex server. */
fun FragmentManager.showServerSignIn(type: MediaProviderType) {
    when (type) {
        MediaProviderType.Jellyfin -> JellyfinConfigurationFragment.newInstance().show(this)
        MediaProviderType.Emby -> EmbyConfigurationFragment.newInstance().show(this)
        MediaProviderType.Plex -> PlexConfigurationFragment.newInstance().show(this)
        MediaProviderType.Shuttle, MediaProviderType.MediaStore -> error("$type has no sign-in")
    }
}
