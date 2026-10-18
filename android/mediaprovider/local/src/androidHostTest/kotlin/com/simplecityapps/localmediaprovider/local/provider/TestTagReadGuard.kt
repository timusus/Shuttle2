package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.ProcessExit
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.mediaprovider.TagReadLimiter
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceStateStore
import java.io.File
import java.nio.file.Files

/** A guard over a fresh marker folder, as the process [pid], where Android records [processExits]. */
fun testTagReadGuard(
    preferences: SourceStateStore = SourceStateStore(InMemoryKeyValueStore()),
    markerDir: File = Files.createTempDirectory("tag-reads").toFile(),
    processExits: () -> List<ProcessExit>? = { null },
    pid: Int = 1,
    permits: Int = 3
) = TagReadGuard(markerDir, preferences, processExits, TagReadLimiter(permits), pid)
