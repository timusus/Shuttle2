package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.Loader
import androidx.media3.exoplayer.util.ReleasableExecutor
import androidx.media3.extractor.text.SubtitleParser
import com.google.common.base.Supplier
import java.io.IOException
import timber.log.Timber

/**
 * A [MediaSource.Factory] that plays extensionless HLS streams, such as a Jellyfin or Emby track
 * the server transcodes (see [StreamTypeProbe]).
 *
 * Items that [StreamTypeProbe.needsProbe] get a [StreamSniffingMediaSource], which probes the
 * stream when the player prepares it and then delegates to an [HlsMediaSource] or to
 * [defaultFactory]'s progressive source, probing on the [Loader] that [newProbeLoader] makes (a
 * thread of its own by default). Everything else (local files, content URIs, known MIME types,
 * URLs with an extension) goes straight to [defaultFactory] with no extra request.
 */
class StreamSniffingMediaSourceFactory(
    private val dataSourceFactory: DataSource.Factory,
    private val defaultFactory: MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory),
    private val hlsFactory: MediaSource.Factory = HlsMediaSource.Factory(dataSourceFactory),
    private val newProbeLoader: () -> Loader = { Loader("S2:StreamTypeProbe") }
) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val localConfiguration = mediaItem.localConfiguration
        return if (localConfiguration != null && StreamTypeProbe.needsProbe(localConfiguration.uri, localConfiguration.mimeType)) {
            StreamSniffingMediaSource(mediaItem, dataSourceFactory, hlsFactory, defaultFactory, newProbeLoader)
        } else {
            defaultFactory.createMediaSource(mediaItem)
        }
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = forEachFactory { setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = forEachFactory { setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }

    override fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory): MediaSource.Factory = forEachFactory { setCmcdConfigurationFactory(cmcdConfigurationFactory) }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): MediaSource.Factory = forEachFactory { setSubtitleParserFactory(subtitleParserFactory) }

    @Deprecated("Deprecated in Media3")
    @Suppress("DEPRECATION")
    override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): MediaSource.Factory = forEachFactory { experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction) }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecFlags: Int): MediaSource.Factory = forEachFactory { experimentalSetCodecsToParseWithinGopSampleDependencies(codecFlags) }

    override fun setDownloadExecutor(downloadExecutor: Supplier<ReleasableExecutor>): MediaSource.Factory = forEachFactory { setDownloadExecutor(downloadExecutor) }

    private fun forEachFactory(block: MediaSource.Factory.() -> Unit): MediaSource.Factory {
        defaultFactory.block()
        hlsFactory.block()
        return this
    }
}

/**
 * Probes [mediaItem]'s stream once prepared, off the playback thread, then prepares the matching
 * child source and forwards its timeline and periods. A failed probe falls back to the
 * progressive source, so the player reports the same error it would have without probing.
 */
class StreamSniffingMediaSource(
    private val mediaItem: MediaItem,
    private val dataSourceFactory: DataSource.Factory,
    private val hlsFactory: MediaSource.Factory,
    private val progressiveFactory: MediaSource.Factory,
    private val newProbeLoader: () -> Loader
) : CompositeMediaSource<Unit>() {
    private var loader: Loader? = null
    private var streamType: StreamType? = null

    /** The source this item resolved to, once the probe has finished. */
    var childSource: MediaSource? = null
        private set

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        // A re-prepare after release reuses the earlier answer rather than probing again.
        streamType?.let { prepareChild(it) } ?: startProbe()
    }

    private fun startProbe() {
        val uri = checkNotNull(mediaItem.localConfiguration).uri
        val loader = newProbeLoader().also { loader = it }
        loader.startLoading(
            ProbeLoadable(dataSourceFactory.createDataSource(), uri),
            object : Loader.Callback<ProbeLoadable> {
                override fun onLoadCompleted(
                    loadable: ProbeLoadable,
                    elapsedRealtimeMs: Long,
                    loadDurationMs: Long
                ) {
                    resolve(checkNotNull(loadable.result), "probed in ${loadDurationMs}ms")
                }

                override fun onLoadCanceled(
                    loadable: ProbeLoadable,
                    elapsedRealtimeMs: Long,
                    loadDurationMs: Long,
                    released: Boolean
                ) {
                }

                override fun onLoadError(
                    loadable: ProbeLoadable,
                    elapsedRealtimeMs: Long,
                    loadDurationMs: Long,
                    error: IOException,
                    errorCount: Int
                ): Loader.LoadErrorAction {
                    resolve(StreamType.Progressive, "probe failed: ${error.javaClass.simpleName}")
                    return Loader.DONT_RETRY
                }
            },
            0
        )
    }

    private fun resolve(
        type: StreamType,
        reason: String
    ) {
        streamType = type
        val source = prepareChild(type)
        // Never log the URL: remote stream URLs carry the server's access token.
        Timber.i("Stream type $type ($reason), playing with ${source.javaClass.simpleName}")
    }

    private fun prepareChild(type: StreamType): MediaSource {
        val source = when (type) {
            StreamType.Hls -> hlsFactory.createMediaSource(mediaItem)
            StreamType.Progressive -> progressiveFactory.createMediaSource(mediaItem)
        }
        childSource = source
        prepareChildSource(Unit, source)
        return source
    }

    override fun onChildSourceInfoRefreshed(
        childSourceId: Unit,
        mediaSource: MediaSource,
        newTimeline: Timeline
    ) {
        refreshSourceInfo(newTimeline)
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod = checkNotNull(childSource) { "createPeriod before the stream type is known" }.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        checkNotNull(childSource).releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        loader?.release()
        loader = null
        childSource = null
        super.releaseSourceInternal()
    }

    private class ProbeLoadable(
        private val dataSource: DataSource,
        private val uri: Uri
    ) : Loader.Loadable {
        @Volatile
        var result: StreamType? = null

        // Loader only interrupts the load thread, which doesn't unblock a java.net read; closing the
        // source disconnects it, so a skipped or released probe can't hold a socket until timeout.
        override fun cancelLoad() {
            DataSourceUtil.closeQuietly(dataSource)
        }

        override fun load() {
            result = StreamTypeProbe.probe(dataSource, uri)
        }
    }
}
