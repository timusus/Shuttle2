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
import platform.Foundation.setValue
import platform.Foundation.valueForHTTPHeaderField
import platform.darwin.NSObject

/**
 * [DownloadTransport] over a background `URLSession`: iOS runs the downloads out of process, so they carry on while the
 * app is suspended, and at the next launch the session's still-running tasks are found again ([restore]). Each task
 * carries its song's path and the file's extension in its `taskDescription`. A completed file is moved into [directory]
 * (Application Support/Downloads, excluded from iCloud backup) under its [DownloadFileNames] name, replacing any earlier
 * one only once it's there, and the directory's listing is the record of what's downloaded.
 *
 * Every delegate call arrives on the main queue, where [start] and [remove] are called too, so [tasks] is only touched
 * there. A path's wanted task is the one [tasks] holds: a removal cancels it there and then, and a task the session still
 * runs for a removed or restarted path is cancelled and ignored, never mistaken for the one that replaced it.
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

    /** Each path's wanted task: the one [start] made, or one [restore] found still running from an earlier launch. */
    private val tasks = mutableMapOf<String, NSURLSessionTask>()

    /** The downloads still finding their address, for a server with custom headers: see [start]. */
    private val resolving = mutableMapOf<String, Resolution>()

    private class Resolution {
        var task: NSURLSessionTask? = null
        var cancelled = false

        fun cancel() {
            cancelled = true
            task?.cancel()
        }
    }

    /** What each probe request does with its response, by task: called once, with null if it failed. */
    private val probes = mutableMapOf<ULong, (NSHTTPURLResponse?) -> Unit>()

    /** An ordinary session for the probes, which a background session can't make (it only runs downloads). */
    private val probeSession: NSURLSession = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration,
        delegate = ProbeDelegate(),
        delegateQueue = NSOperationQueue.mainQueue
    )

    /** The app delegate's completion handler for the session's background events, until they've all been delivered. */
    private var backgroundEventsCompletion: (() -> Unit)? = null

    override fun restore(): Set<String> {
        // A move into place that the app didn't live to finish
        directoryContents().filter { it.lastPathComponent?.startsWith(STAGING_PREFIX) == true }.forEach { fileManager.removeItemAtURL(it, null) }
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                downloadTasks.orEmpty().filterIsInstance<NSURLSessionDownloadTask>().forEach { task ->
                    val path = task.path() ?: return@forEach
                    val wanted = tasks[path]
                    when {
                        wanted == null -> {
                            tasks[path] = task
                            listener?.onRunning(path)
                        }

                        // Started again since launch: the new task replaces this one
                        !wanted.isSameTask(task) -> task.cancel()
                    }
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
        tasks.remove(path)?.cancel()
        resolving.remove(path)?.cancel()
        // A server with custom headers (#921): its redirects are followed here, with the headers, before the background
        // session gets the final address. iOS follows a background task's redirects itself while the app is suspended,
        // without asking the delegate, so a download that sent the headers could carry them to another host (#933).
        if (serverRequestPolicy.headers(source.url).isEmpty()) return begin(path, source, url, wifiOnly, withHeaders = false)
        val resolution = Resolution()
        resolving[path] = resolution
        resolve(resolution, url, url, wifiOnly, hops = 0) { final, withHeaders ->
            if (resolving[path] !== resolution) return@resolve
            resolving.remove(path)
            if (final == null) listener?.onFailed(path) else begin(path, source, final, wifiOnly, withHeaders)
        }
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
        tasks[path] = task
        task.resume()
    }

    /**
     * Follows [url]'s redirects as far as they stay at [origin] (its server), with the server's headers, then reports where
     * the download is to go: that address, with the headers only if it's still the server's. Null if a request failed.
     */
    private fun resolve(
        resolution: Resolution,
        origin: NSURL,
        url: NSURL,
        wifiOnly: Boolean,
        hops: Int,
        done: (NSURL?, Boolean) -> Unit
    ) {
        val probe = probeSession.dataTaskWithRequest(downloadRequest(url, wifiOnly, serverRequestPolicy.headers(origin.absoluteString.orEmpty()), probe = true))
        resolution.task = probe
        probes[probe.taskIdentifier] = { response ->
            if (resolution.cancelled) {
                // Removed or restarted meanwhile: nothing to report
            } else if (response == null) {
                done(null, false)
            } else {
                when (val step = nextDownloadStep(serverRequestPolicy, origin, url, response.statusCode, response.valueForHTTPHeaderField("Location"))) {
                    is DownloadStep.Follow -> if (hops >= MAX_REDIRECTS) done(null, false) else resolve(resolution, origin, step.url, wifiOnly, hops + 1, done)
                    is DownloadStep.Download -> done(step.url, step.withHeaders)
                }
            }
        }
        probe.resume()
    }

    override fun remove(path: String) {
        tasks.remove(path)?.cancel()
        resolving.remove(path)?.cancel()
        // One from an earlier launch that [restore] hasn't found yet: cancelled once the session lists it, unless it's
        // the download started for the path since
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                val wanted = tasks[path]
                downloadTasks.orEmpty().filterIsInstance<NSURLSessionTask>()
                    .filter { it.path() == path && (wanted == null || !wanted.isSameTask(it)) }
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

    private fun NSURLSessionTask.isSameTask(other: NSURLSessionTask): Boolean = taskIdentifier == other.taskIdentifier

    /** Whether [task] is [path]'s wanted task, or one from an earlier launch that's still the only one for it. */
    private fun isWanted(
        path: String,
        task: NSURLSessionTask
    ): Boolean = tasks[path]?.isSameTask(task) ?: true

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
            if (isWanted(path, downloadTask)) listener?.onProgress(path, totalBytesWritten, totalBytesExpectedToWrite)
        }

        // The temporary file is deleted when this returns, so it's moved here, before anything else
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL
        ) {
            val path = downloadTask.path() ?: return
            // Replaced by a later download of the same song, whose file is the one to keep
            if (!isWanted(path, downloadTask)) return
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
            val wanted = tasks[path]
            // Replaced by a later download of the same song, which reports for itself
            if (wanted != null && !wanted.isSameTask(task)) return
            tasks.remove(path)
            val error = didCompleteWithError ?: return
            // Cancelled by a removal (which forgot it) or a restart; a cancelled task that's still wanted has failed
            if (wanted == null && error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled) return
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
            completionHandler(serverRequestPolicy.redirected(newRequest, task.originalRequest?.URL))
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            val completion = backgroundEventsCompletion ?: return
            backgroundEventsCompletion = null
            NSOperationQueue.mainQueue.addOperationWithBlock { completion() }
        }
    }

    /** Reads each probe's first response, whatever it is, and follows no redirect: [resolve] decides each hop. */
    private inner class ProbeDelegate :
        NSObject(),
        NSURLSessionDataDelegateProtocol {
        private fun finish(
            task: NSURLSessionTask,
            response: NSHTTPURLResponse?
        ) {
            probes.remove(task.taskIdentifier)?.invoke(response)
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

        const val MAX_REDIRECTS = 10
    }
}

/** What to do with a probe's response: ask the next address, or hand the download this one. */
internal sealed interface DownloadStep {
    class Follow(
        val url: NSURL
    ) : DownloadStep

    /** Download [url], with its server's custom headers only if [withHeaders]. */
    class Download(
        val url: NSURL,
        val withHeaders: Boolean
    ) : DownloadStep
}

/**
 * The step after [url]'s response ([status] and its `Location`), a hop in a chain that began at [origin], the server's
 * address: a redirect to the server's own scheme, host and port is followed with its headers, one to anywhere else is
 * where the download goes, without them, and an answer that isn't a redirect means [url] itself is, with them.
 */
internal fun nextDownloadStep(
    policy: ServerRequestPolicy,
    origin: NSURL,
    url: NSURL,
    status: Long,
    location: String?
): DownloadStep {
    val redirect = if (status in 300L..399L && location != null) NSURL.URLWithString(location, relativeToURL = url)?.absoluteURL else null
    val next = redirect ?: return DownloadStep.Download(url, withHeaders = policy.sameOrigin(url, origin))
    return if (policy.sameOrigin(next, origin)) DownloadStep.Follow(next) else DownloadStep.Download(next, withHeaders = false)
}

/**
 * The request for a download or probe of [url]: [headers] are the server's custom headers (#921); the pinned certificate is
 * trusted in the delegate. A probe asks for its first byte only, as it is only read for its status and `Location`.
 */
internal fun downloadRequest(
    url: NSURL,
    wifiOnly: Boolean,
    headers: Map<String, String>,
    probe: Boolean = false
): NSMutableURLRequest = NSMutableURLRequest.requestWithURL(url).apply {
    // NSURLRequest's properties are read-only vals in Kotlin; the mutable request's setters write them
    setAllowsCellularAccess(!wifiOnly)
    setAllowsExpensiveNetworkAccess(!wifiOnly)
    setAllHTTPHeaderFields(headers.toMap<Any?, Any?>())
    if (probe) setValue("bytes=0-0", forHTTPHeaderField = "Range")
}
