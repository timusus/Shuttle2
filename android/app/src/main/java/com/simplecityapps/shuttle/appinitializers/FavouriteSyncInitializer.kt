package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.localmediaprovider.local.favourites.FavouriteSender
import dev.zacsweers.metro.Inject

/** Starts sending the favourites made on Jellyfin, Emby and Plex songs to their servers (#497). */
class FavouriteSyncInitializer
@Inject
constructor(
    private val sender: FavouriteSender
) : AppInitializer {
    override fun init(application: Application) {
        sender.start()
    }
}
