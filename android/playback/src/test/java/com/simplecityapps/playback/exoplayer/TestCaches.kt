package com.simplecityapps.playback.exoplayer

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.nio.file.Files

/** A download cache with nothing downloaded in it, in a fresh temp directory. */
fun emptyDownloadCache(context: Context): Cache = SimpleCache(Files.createTempDirectory("downloads").toFile(), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
