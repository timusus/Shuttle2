package au.com.simplecityapps.shuttle.imageloading

import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import au.com.simplecityapps.R
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class ArtworkDownloadService : Service(), CoroutineScope {

    private val notificationManager: NotificationManager? by lazy {
        getSystemService()
    }

    val connectivityManager: ConnectivityManager? by lazy {
        getSystemService()
    }

    @Inject
    lateinit var albumRepository: AlbumRepository

    @Inject
    lateinit var albumArtistRepository: AlbumArtistRepository

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var job = SupervisorJob()

    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, exception -> Timber.e(exception) }
    }

    override val coroutineContext: CoroutineContext
        get() = job + exceptionHandler + Dispatchers.Main

    // Lifecycle

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        if (preferenceManager.artworkWifiOnly && connectivityManager?.isActiveNetworkMetered == true) {
            Toast.makeText(this, "Failed to download artwork - WiFi only", Toast.LENGTH_LONG).show()
            stopSelf()
        }

        if (job.isCancelled) {
            job = SupervisorJob()
        }

        val imageLoader = GlideImageLoader(this)

        createNotificationChannel()

        val serviceName = ComponentName(this, ArtworkDownloadService::class.java)
        val intent = Intent(ACTION_CANCEL)
        intent.component = serviceName
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntentCompat.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading artwork")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .addAction(NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Cancel", pendingIntent))

        notificationManager?.notify(NOTIFICATION_ID, notificationBuilder.build())

        launch {
            val albumFutures = albumRepository.getAlbums(AlbumQuery.All()).first().map {
                imageLoader.requestManager
                    .downloadOnly()
                    .load(it)
                    .submit()
            }

            val artistFutures = albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()).first().map {
                imageLoader.requestManager
                    .downloadOnly()
                    .load(it)
                    .load(it)
                    .submit()
            }

            val futures = albumFutures + artistFutures

            futures
                .asFlow()
                .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { future ->
                    ensureActive()
                    try {
                        future.get(10, TimeUnit.SECONDS)
                        imageLoader.requestManager.clear(future)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to retrieve artwork")
                    }
                    null
                }
                .flowOn(Dispatchers.IO)
                .collectIndexed { index, _ ->
                    notificationBuilder.setProgress(futures.size, index, false)
                    notificationManager?.notify(NOTIFICATION_ID, notificationBuilder.build())
                }

            notificationManager?.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action != null && action == ACTION_CANCEL) {
                // Handle a notification cancel action click:
                job.cancel()
                notificationManager?.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        job.cancel()
        notificationManager?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    // Private

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.getNotificationChannel(NOTIFICATION_CHANNEL_ID) ?: run {
                val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Shuttle", NotificationManager.IMPORTANCE_LOW)
                notificationChannel.enableLights(false)
                notificationChannel.enableVibration(false)
                notificationChannel.setShowBadge(false)
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                notificationManager?.createNotificationChannel(notificationChannel)
            }
        }
    }

    // Static

    companion object {
        private const val ACTION_CANCEL = "com.simplecityapps.shuttle.artwork_cancel"
        const val NOTIFICATION_CHANNEL_ID = "1"
        const val NOTIFICATION_ID = 2
    }
}
