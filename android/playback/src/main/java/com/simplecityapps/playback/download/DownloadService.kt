package com.simplecityapps.playback.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.R
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service that manages background downloads of songs for offline playback
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var songRepository: SongRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationManager: NotificationManager? by lazy { getSystemService() }
    private val connectivityManager: ConnectivityManager? by lazy { getSystemService() }

    private var downloadJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("DownloadService created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Monitor network changes
        setupNetworkMonitoring()

        // Start monitoring downloads
        startDownloadMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val songIds = intent.getLongArrayExtra(EXTRA_SONG_IDS)
                if (songIds != null) {
                    serviceScope.launch {
                        val songs = songIds.mapNotNull { id ->
                            // Get songs from repository
                            // TODO: This needs proper implementation
                            null
                        }
                        if (songs.isNotEmpty()) {
                            downloadManager.queueDownloads(songs)
                        }
                    }
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1L)
                if (songId != -1L) {
                    serviceScope.launch {
                        // TODO: Get song and pause
                    }
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val songId = intent.getLongExtra(EXTRA_SONG_ID, -1L)
                if (songId != -1L) {
                    serviceScope.launch {
                        // TODO: Get song and cancel
                    }
                }
            }
            ACTION_CANCEL_ALL -> {
                serviceScope.launch {
                    val activeDownloads = downloadRepository.getActiveDownloads()
                    // TODO: Cancel all
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun setupNetworkMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    // Network available - resume downloads if needed
                    serviceScope.launch {
                        val pausedDownloads = downloadRepository.getDownloadsByState(DownloadState.PAUSED)
                        // TODO: Resume paused downloads if appropriate
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    // Network lost - pause downloads if WiFi-only mode
                    // TODO: Check settings and pause if needed
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        }
    }

    private fun startDownloadMonitoring() {
        downloadJob = serviceScope.launch {
            downloadManager.downloadingCount.collectLatest { count ->
                updateNotification(count)

                // Stop service when no active downloads
                if (count == 0) {
                    serviceScope.launch {
                        val queuedDownloads = downloadRepository.getDownloadsByState(DownloadState.QUEUED)
                        if (queuedDownloads.isEmpty()) {
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(activeDownloads: Int) {
        val notification = buildNotification(activeDownloads)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(activeDownloads: Int = 0): Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntentCompat.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = when {
            activeDownloads == 0 -> "Preparing downloads..."
            activeDownloads == 1 -> "Downloading 1 song"
            else -> "Downloading $activeDownloads songs"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading songs")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_baseline_close_24,
                    "Cancel All",
                    cancelPendingIntent
                )
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Offline download progress"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.simplecityapps.shuttle.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.simplecityapps.shuttle.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.simplecityapps.shuttle.CANCEL_DOWNLOAD"
        const val ACTION_CANCEL_ALL = "com.simplecityapps.shuttle.CANCEL_ALL"

        const val EXTRA_SONG_ID = "song_id"
        const val EXTRA_SONG_IDS = "song_ids"

        fun startDownload(context: Context, songs: List<Song>) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_SONG_IDS, songs.map { it.id }.toLongArray())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, song: Song) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_SONG_ID, song.id)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, song: Song) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_SONG_ID, song.id)
            }
            context.startService(intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            context.startService(intent)
        }
    }
}
