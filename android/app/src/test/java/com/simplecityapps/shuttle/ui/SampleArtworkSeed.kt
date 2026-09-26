package com.simplecityapps.shuttle.ui

import android.graphics.BitmapFactory
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.designsystem.theme.extractSeedColor
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** The seed the app would extract from the sample album [title]'s cover, for screenshots of artwork-tinted screens. */
fun sampleSeed(title: String): ArtworkSeed {
    val bytes = SampleLibrary.albumNamed(title)?.let { SampleLibrary.coverBytes(it.id) } ?: return ArtworkSeed.None
    return extractSeedColor(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))?.let(ArtworkSeed::Available) ?: ArtworkSeed.None
}
