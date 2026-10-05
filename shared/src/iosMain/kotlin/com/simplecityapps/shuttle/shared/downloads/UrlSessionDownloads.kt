package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.shuttle.shared.network.ServerRequestPolicy
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDomainMask
import platform.Foundation.setAllHTTPHeaderFields
import platform.Foundation.valueForHTTPHeaderField
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObject

/**
 * [DownloadTransport] over a background `URLSession`: iOS runs the downloads out of process, so they carry on while the
 * app is suspended, and at the next launch the session's still-running tasks are found again ([restore]). Each task
 * carries its song's path and the file's extension in its `taskDescription`. A completed file is moved into [directory]
 * (Application Support/Downloads, excluded from iCloud backup) under its [DownloadFileNames] name, replacing any earlier
 * one only once it's there, and the directory's listing is the record of what's downloaded.
 *
 * Every delegate call arrives on the main queue, where [start] and [remove] are called too, so [wanted] is only touched
 * there. A path's wanted download is the one [wanted] holds, a task or a start still finding its address: a removal
 * cancels it there and then, and a task the session still runs for a removed or restarted path is cancelled and ignored,
 * never mistaken for the one that replaced it.
 *
 * A server with custom headers (#921) has its download's address found first (#933). A background session follows a
 * task's redirects itself (Apple documents `willPerformHTTPRedirection` as called for default and ephemeral sessions
 * only) and carries the request's headers along, so a task that sent them could take them to another host. So each hop
 * is probed on an ordinary session ([probeSession]) with the download's own request (same URL, headers and network
 * rules, no Range, cancelled at its response), and the task starts where the redirects leave the server, with the headers
 * only if that's still the server. The risk left: a server that redirects the download differently from the probe a
 * moment earlier (a redirect that depends on the time, or on something iOS adds to a background request) still takes
 * the task, headers and all, off the server. A probe that can't resolve hands over unprobed: see [start].
 *
 * iOS relaunches the app in the background when the session's downloads finish while it isn't running; the app delegate
 * hands that over with [handleBackgroundEvents]. The session must already exist by then, so the app builds this at launch.
 *
 * [isolatedName] (a test graph) gives a plain session and its own directory under tmp, so tests never touch the app's
 * downloads or its background session.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class UrlSessionDownloads(
    isolatedName: String?,
    private val serverRequestPolicy: ServerRequestPolicy
) : DownloadTransport {
    override var listener: DownloadTransport.Listener? = null

    private val fileManager = NSFileManager.defaultManager

    private val directory: NSURL = run {
        val base = if (isolatedName == null) {
            fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)!!
        } else {
            NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(isolatedName)!!
        }
        val directory = base.URLByAppendingPathComponent("Downloads", isDirectory = true)!!
        fileManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
        directory.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        directory
    }

    /** The background session's identifier, which iOS names when it relaunches the app for its events; null when isolated. */
    private val backgroundIdentifier: String? =
        if (isolatedName == null) (NSBundle.mainBundle.bundleIdentifier ?: "com.simplecityapps.shuttle") + ".downloads" else null

    private val delegate = Delegate()

    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        backgroundIdentifier?.let(NSURLSessionConfiguration::backgroundSessionConfigurationWithIdentifier)
            ?: NSURLSessionConfiguration.defaultSessionConfiguration,
        delegate = delegate,
        delegateQueue = NSOperationQueue.mainQueue
    )

    /**
     * Each path's wanted download: the task [start] made, or one [restore] found still running from an earlier launch, or
     * the [Resolution] still finding its address.
     */
    private val wanted = WantedDownloads<NSURLSessionTask, Resolution>(
        sameTask = { a, b -> a.taskIdentifier == b.taskIdentifier },
        cancelTask = { it.cancel() },
        cancelPending = { it.close() }
    )

    /** What each probe request does with its answer, by task: called once, with null if it failed. */
    private val probes = mutableMapOf<ULong, (ProbeAnswer?) -> Unit>()

    /**
     * An ordinary session for the probes, which a background session can't make (it only runs downloads). A probe waits
     * for a network its request allows, as the background session's tasks do, rather than failing offline or on mobile
     * data under Wi-Fi only.
     */
    private val probeSession: NSURLSession = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration.apply { waitsForConnectivity = true },
        delegate = ProbeDelegate(),
        delegateQueue = NSOperationQueue.mainQueue
    )

    /** Keeps the app running while a probe is out, up to the background time iOS gives it; tests never run out. */
    private val grace: BackgroundGrace = if (isolatedName == null) AppBackgroundGrace else BackgroundGrace { {} }

    /** The app delegate's completion handler for the session's background events, until they've all been delivered. */
    private var backgroundEventsCompletion: (() -> Unit)? = null

    override fun restore(): Set<String> {
        // A move into place that the app didn't live to finish
        directoryContents().filter { it.lastPathComponent?.startsWith(STAGING_PREFIX) == true }.forEach { fileManager.removeItemAtURL(it, null) }
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                downloadTasks.orEmpty().filterIsInstance<NSURLSessionDownloadTask>().forEach { task ->
                    val path = task.path() ?: return@forEach
                    // One started again since launch, or still finding its address, replaces (and cancels) this one
                    if (wanted.found(path, task)) listener?.onRunning(path)
                }
            }
        }
        return files().keys
    }

    override fun start(
        path: String,
        source: DownloadSource,
        wifiOnly: Boolean
    ) {
        val url = NSURL.URLWithString(source.url) ?: return listener?.onFailed(path) ?: Unit
        // A server with custom headers (#921) has its redirects followed here first, with the headers (#933). While the
        // probe can't be answered for want of an allowed network, it waits, as the background session would. One that
        // can't resolve (it failed, or the app went to the background and its time ran out first) hands the download to
        // the background session at the original address with the headers, as before the probes: that session keeps
        // it until a network is allowed and the app is gone, where failing would lose it, and without the headers the
        // proxy they're for refuses it, or answers with its sign-in page, saved as the song. Only these unprobed
        // starts can still take the headers along a redirect off the server.
        if (serverRequestPolicy.headers(source.url).isEmpty()) return begin(path, source, url, wifiOnly, withHeaders = false)
        val resolution = Resolution(path, source, url, wifiOnly)
        wanted.pend(path, resolution)
        resolution.ask(url, hops = 0)
    }

    /** Starts [path]'s download task for [url], which carries [source]'s server's custom headers only if [withHeaders]. */
    private fun begin(
        path: String,
        source: DownloadSource,
        url: NSURL,
        wifiOnly: Boolean,
        withHeaders: Boolean
    ) {
        // Per request, so a download started under one setting keeps it when the setting changes. A background session
        // holds a task that's refused a network until an allowed one is back, rather than failing it.
        val request = downloadRequest(url, wifiOnly, if (withHeaders) serverRequestPolicy.headers(source.url) else emptyMap())
        val task = session.downloadTaskWithRequest(request)
        // The MIME type names the file; the server's suggested name is the fallback for one it doesn't know
        task.taskDescription = source.mimeType + "\n" + path
        wanted.run(path, task)
        task.resume()
    }

    /**
     * [path]'s download for a server with custom headers, finding its address from [origin]: it follows the redirects
     * as far as they stay at the server, with the server's headers ([nextDownloadStep]), then starts the task. It holds
     * background time while it does, and hands over unprobed when that runs out ([start]).
     */
    private inner class Resolution(
        private val path: String,
        private val source: DownloadSource,
        private val origin: NSURL,
        private val wifiOnly: Boolean
    ) {
        private var probe: NSURLSessionTask? = null
        private var closed = false
        private val release = grace.hold { step(origin, hops = 0, answer = null) }

        /** Probes [url], hop [hops] of the chain. */
        fun ask(
            url: NSURL,
            hops: Int
        ) {
            val task = probeSession.dataTaskWithRequest(downloadRequest(url, wifiOnly, serverRequestPolicy.headers(origin.absoluteString.orEmpty())))
            probe = task
            probes[task.taskIdentifier] = { answer -> if (!closed) step(url, hops, answer) }
            task.resume()
        }

        private fun step(
            url: NSURL,
            hops: Int,
            answer: ProbeAnswer?
        ) {
            when (val step = nextDownloadStep(serverRequestPolicy, origin, url, hops, answer)) {
                is DownloadStep.Follow -> ask(step.url, hops + 1)
                is DownloadStep.Download -> finish { begin(path, source, step.url, wifiOnly, step.withHeaders) }
                DownloadStep.Fail -> finish { listener?.onFailed(path) }
            }
        }

        /** Ends the probing with [then], unless the path has been removed or restarted since. */
        private fun finish(then: () -> Unit) {
            if (!wanted.resolved(path, this)) return
            close()
            then()
        }

        /** Stops the probe and gives back the background time: done, removed or restarted. */
        fun close() {
            if (closed) return
            closed = true
            probe?.cancel()
            release()
        }
    }

    override fun remove(path: String) {
        wanted.clear(path)
        // One from an earlier launch that [restore] hasn't found yet: cancelled once the session lists it, unless it's
        // the download started for the path since
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                downloadTasks.orEmpty().filterIsInstance<NSURLSessionTask>()
                    .filter { it.path() == path && !wanted.isRunning(path, it) }
                    .forEach { it.cancel() }
            }
        }
        filesFor(path).forEach { fileManager.removeItemAtURL(it, null) }
    }

    override fun fileUrl(path: String): String? {
        val file = files()[path] ?: return null
        if (file.size() == 0L) {
            fileManager.removeItemAtURL(file, null)
            return null
        }
        return file.absoluteString
    }

    /**
     * Takes the app delegate's `handleEventsForBackgroundURLSession` call: [completionHandler] is called on the main queue
     * once the session has delivered every event iOS woke the app for. False if [identifier] is another session's.
     */
    fun handleBackgroundEvents(
        identifier: String,
        completionHandler: () -> Unit
    ): Boolean {
        if (identifier != backgroundIdentifier) return false
        backgroundEventsCompletion = completionHandler
        return true
    }

    private fun directoryContents(): List<NSURL> = fileManager
        .contentsOfDirectoryAtURL(directory, includingPropertiesForKeys = null, options = 0u, error = null)
        .orEmpty()
        .filterIsInstance<NSURL>()

    /** The completed downloads in [directory], by path. */
    private fun files(): Map<String, NSURL> = directoryContents()
        .mapNotNull { url -> url.lastPathComponent?.let(DownloadFileNames::path)?.let { it to url } }
        .toMap()

    /** Every file in [directory] that's [path]'s: more than one only if a download came back with another extension. */
    private fun filesFor(path: String): List<NSURL> = directoryContents().filter { url -> url.lastPathComponent?.let(DownloadFileNames::path) == path }

    private fun NSURL.size(): Long? = this.path?.let { (fileManager.attributesOfItemAtPath(it, null)?.get(NSFileSize) as? NSNumber)?.longLongValue }

    private fun NSURLSessionTask.path(): String? = taskDescription?.substringAfter('\n', "")?.takeIf { it.isNotEmpty() }

    /**
     * Moves [path]'s finished download at [location] in as [destination]: first beside it under a staging name, then over
     * any earlier file, so the earlier one is only gone once the new one is in its place.
     */
    private fun moveIn(
        path: String,
        location: NSURL,
        destination: NSURL,
        stagingName: String
    ): Boolean {
        val staging = directory.URLByAppendingPathComponent(stagingName)!!
        fileManager.removeItemAtURL(staging, null)
        if (!fileManager.moveItemAtURL(location, staging, null)) return false
        val existing = destination.path?.let(fileManager::fileExistsAtPath) == true
        val moved = if (existing) {
            fileManager.replaceItemAtURL(destination, withItemAtURL = staging, backupItemName = null, options = 0u, resultingItemURL = null, error = null)
        } else {
            fileManager.moveItemAtURL(staging, destination, null)
        }
        if (!moved) {
            fileManager.removeItemAtURL(staging, null)
            return false
        }
        // An earlier download saved under another extension
        filesFor(path).filter { it.lastPathComponent != destination.lastPathComponent }.forEach { fileManager.removeItemAtURL(it, null) }
        return true
    }

    private inner class Delegate :
        NSObject(),
        NSURLSessionDownloadDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long
        ) {
            val path = downloadTask.path() ?: return
            if (wanted.isWanted(path, downloadTask)) listener?.onProgress(path, totalBytesWritten, totalBytesExpectedToWrite)
        }

        // The temporary file is deleted when this returns, so it's moved here, before anything else
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL
        ) {
            val path = downloadTask.path() ?: return
            // Replaced by a later download of the same song, whose file is the one to keep
            if (!wanted.isWanted(path, downloadTask)) return
            val response = downloadTask.response as? NSHTTPURLResponse
            // A refusal's body is an error page, not the song: its status is reported so a 401/403 can be retried
            if (response != null && response.statusCode !in 200L..299L) return listener?.onFailed(path, response.statusCode.toInt()) ?: Unit
            if (response == null || didFinishDownloadingToURL.size() == 0L) return listener?.onFailed(path) ?: Unit
            val mimeType = downloadTask.taskDescription!!.substringBefore('\n')
            val extension = DownloadFileNames.extension(mimeType, fallback = response.suggestedFilename?.substringAfterLast('.', ""))
            val destination = directory.URLByAppendingPathComponent(DownloadFileNames.fileName(path, extension))!!
            if (moveIn(path, didFinishDownloadingToURL, destination, STAGING_PREFIX + downloadTask.taskIdentifier)) {
                listener?.onCompleted(path)
            } else {
                listener?.onFailed(path)
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            val path = task.path() ?: return
            // Replaced by a later download of the same song, which reports for itself
            if (!wanted.isWanted(path, task)) return
            val held = wanted.finished(path)
            val error = didCompleteWithError ?: return
            // Cancelled by a removal (which forgot it) or a restart; a cancelled task that's still wanted has failed
            if (!held && error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled) return
            listener?.onFailed(path)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didReceiveChallenge: NSURLAuthenticationChallenge,
            completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
        ) {
            serverRequestPolicy.handleChallenge(didReceiveChallenge, completionHandler)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit
        ) {
            // Asked by the isolated session's tasks; a background session's are followed without asking (see the KDoc)
            completionHandler(serverRequestPolicy.redirected(newRequest, task.originalRequest?.URL))
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            val completion = backgroundEventsCompletion ?: return
            backgroundEventsCompletion = null
            NSOperationQueue.mainQueue.addOperationWithBlock { completion() }
        }
    }

    /**
     * Reads each probe's first response, whatever it is, and follows no redirect: [Resolution] decides each hop. The
     * response ends the probe before its body is read.
     */
    private inner class ProbeDelegate :
        NSObject(),
        NSURLSessionDataDelegateProtocol {
        private fun finish(
            task: NSURLSessionTask,
            response: NSHTTPURLResponse?
        ) {
            probes.remove(task.taskIdentifier)?.invoke(response?.let { ProbeAnswer(it.statusCode, it.valueForHTTPHeaderField("Location")) })
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit
        ) {
            finish(task, willPerformHTTPRedirection)
            completionHandler(null)
        }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit
        ) {
            finish(dataTask, didReceiveResponse as? NSHTTPURLResponse)
            completionHandler(NSURLSessionResponseCancel)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            finish(task, null)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didReceiveChallenge: NSURLAuthenticationChallenge,
            completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
        ) {
            serverRequestPolicy.handleChallenge(didReceiveChallenge, completionHandler)
        }
    }

    private companion object {
        /** A finished download's name while it's moved into place; [DownloadFileNames.path] reads no path from it. */
        const val STAGING_PREFIX = ".incoming-"
    }
}

/**
 * Keeps the app running in the background while a probe is out: [hold] returns what gives the time back, and calls its
 * `onExpired` (on the main queue) if iOS is about to suspend the app first.
 */
internal fun interface BackgroundGrace {
    fun hold(onExpired: () -> Unit): () -> Unit
}

/** [BackgroundGrace] as a `UIApplication` background task. */
private object AppBackgroundGrace : BackgroundGrace {
    override fun hold(onExpired: () -> Unit): () -> Unit {
        val app = UIApplication.sharedApplication
        var id = UIBackgroundTaskInvalid
        val end = {
            if (id != UIBackgroundTaskInvalid) {
                app.endBackgroundTask(id)
                id = UIBackgroundTaskInvalid
            }
        }
        id = app.beginBackgroundTaskWithName("Download address") {
            onExpired()
            end()
        }
        return end
    }
}

/** A probe's response: its [status] and `Location`. */
internal class ProbeAnswer(
    val status: Long,
    val location: String?
)

/** What to do after a probe: ask the next address, hand the download this one, or give up on a redirect loop. */
internal sealed interface DownloadStep {
    class Follow(
        val url: NSURL
    ) : DownloadStep

    /** Download [url], with its server's custom headers only if [withHeaders]. */
    class Download(
        val url: NSURL,
        val withHeaders: Boolean
    ) : DownloadStep

    data object Fail : DownloadStep
}

/** How many redirects within the server a download's probes follow before failing it. */
internal const val MAX_DOWNLOAD_REDIRECTS = 10

/**
 * The step after [url]'s [answer], hop [hops] of a chain that began at [origin], the server's address: a redirect to the
 * server's own scheme, host and port is followed with its headers (a loop fails after [MAX_DOWNLOAD_REDIRECTS]), one to
 * anywhere else is where the download goes, without them, and an answer that isn't a redirect means [url] itself is,
 * with them. No answer (the probe failed, or the app's time ran out first) hands over [origin] with the headers, unprobed,
 * as [UrlSessionDownloads.start] explains.
 */
internal fun nextDownloadStep(
    policy: ServerRequestPolicy,
    origin: NSURL,
    url: NSURL,
    hops: Int,
    answer: ProbeAnswer?
): DownloadStep {
    answer ?: return DownloadStep.Download(origin, withHeaders = true)
    val location = answer.location
    val redirect = if (answer.status in 300L..399L && location != null) NSURL.URLWithString(location, relativeToURL = url)?.absoluteURL else null
    val next = redirect ?: return DownloadStep.Download(url, withHeaders = policy.sameOrigin(url, origin))
    return when {
        !policy.sameOrigin(next, origin) -> DownloadStep.Download(next, withHeaders = false)
        hops >= MAX_DOWNLOAD_REDIRECTS -> DownloadStep.Fail
        else -> DownloadStep.Follow(next)
    }
}

/**
 * The request for a download, or a probe of one, of [url]: [headers] are the server's custom headers (#921); the pinned
 * certificate is trusted in the delegate. A probe is the same request, so the server redirects it as it would the download.
 */
internal fun downloadRequest(
    url: NSURL,
    wifiOnly: Boolean,
    headers: Map<String, String>
): NSMutableURLRequest = NSMutableURLRequest.requestWithURL(url).apply {
    // NSURLRequest's properties are read-only vals in Kotlin; the mutable request's setters write them
    setAllowsCellularAccess(!wifiOnly)
    setAllowsExpensiveNetworkAccess(!wifiOnly)
    setAllHTTPHeaderFields(headers.toMap<Any?, Any?>())
}
