package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser

/**
 * [DefaultExtractorsFactory]'s extractors, with constant-bitrate seeking for the streams [isTimeSeekable] says seek by
 * time ([com.simplecityapps.mediaprovider.TimeSeekableStream]), given the item's own URI. Such a stream is a server's
 * constant-bitrate transcode, sized by its bitrate, so an MP3 seek frame it carries without a table of contents (written
 * before the encoder knew the stream's length) shouldn't leave it unseekable. Every other MP3 (local files, other
 * servers' streams) keeps Media3's default: a seek frame with no table isn't seekable, where a bitrate guess would put a
 * VBR file's seeks in the wrong place.
 */
class TimeSeekExtractorsFactory(private val isTimeSeekable: (Uri) -> Boolean) : ExtractorsFactory {
    private val default = DefaultExtractorsFactory()

    private val constantBitrate = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

    override fun createExtractors(): Array<Extractor> = default.createExtractors()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> = (if (isTimeSeekable(uri)) constantBitrate else default).createExtractors(uri, responseHeaders)

    @Deprecated("Deprecated in Media3")
    @Suppress("DEPRECATION")
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory = forEach { experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled) }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory = forEach { setSubtitleParserFactory(subtitleParserFactory) }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory = forEach { experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies) }

    override fun setParseHagcMetadata(parseHagcMetadata: Boolean): ExtractorsFactory = forEach { setParseHagcMetadata(parseHagcMetadata) }

    private fun forEach(block: DefaultExtractorsFactory.() -> Unit): ExtractorsFactory {
        default.block()
        constantBitrate.block()
        return this
    }
}
