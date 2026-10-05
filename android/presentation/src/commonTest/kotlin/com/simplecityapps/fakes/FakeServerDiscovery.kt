package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.server.DiscoveredServer
import com.simplecityapps.mediaprovider.server.ServerDiscovery
import com.simplecityapps.shuttle.model.MediaProviderType

/** Finds [servers] for any type it's asked about, and records each search. */
class FakeServerDiscovery : ServerDiscovery {
    var servers = emptyList<DiscoveredServer>()
    val searched = mutableListOf<MediaProviderType>()

    override suspend fun discover(type: MediaProviderType): List<DiscoveredServer> {
        searched += type
        return servers
    }
}
