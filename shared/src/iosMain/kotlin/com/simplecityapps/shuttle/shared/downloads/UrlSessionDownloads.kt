package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.mediaprovider.DownloadSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDomainMask
import platform.darwin.NSObject

/**
 * [DownloadTransport] over a background `URLSession`: iOS runs the downloads out of process, so they carry on while the
 * app is suspended, and at the next launch the session's still-running tasks are found again ([restore]). Each task
 * carries its song's path and the file's extension in its `taskDescription`. A completed file is moved into [directory]
 * (Application Support/Downloads, excluded from iCloud backup) under its [DownloadFileNames] name, and the directory's
 * listing is the record of what's downloaded. Every delegate call arrives on the main queue.
 *
 * [isolatedName] (a test graph) gives a plain session and its own directory under tmp, so tests never touch the app's
 * downloads or its background session.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class UrlSessionDownloads(isolatedName: String?) : DownloadTransport {
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

    private val delegate = Delegate()

    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        if (isolatedName == null) {
            NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier((NSBundle.mainBundle.bundleIdentifier ?: "com.simplecityapps.shuttle") + ".downloads")
        } else {
            NSURLSessionConfiguration.defaultSessionConfiguration
        },
        delegate = delegate,
        delegateQueue = NSOperationQueue.mainQueue
    )

    override fun restore(): Set<String> {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                downloadTasks.orEmpty().filterIsInstance<NSURLSessionDownloadTask>().forEach { task ->
                    task.path()?.let { listener?.onRunning(it) }
                }
            }
        }
        return files().keys
    }

    override fun start(
        path: String,
        source: DownloadSource
    ) {
        val url = NSURL.URLWithString(source.url) ?: return listener?.onFailed(path) ?: Unit
        val task = session.downloadTaskWithURL(url)
        // The MIME type names the file; the server's suggested name is the fallback for one it doesn't know
        task.taskDescription = source.mimeType + "\n" + path
        task.resume()
    }

    override fun remove(path: String) {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            downloadTasks.orEmpty().filterIsInstance<NSURLSessionTask>().filter { it.path() == path }.forEach { it.cancel() }
        }
        files()[path]?.let { fileManager.removeItemAtURL(it, null) }
    }

    override fun fileUrl(path: String): String? = files()[path]?.absoluteString

    /** The completed downloads in [directory], by path. */
    private fun files(): Map<String, NSURL> = fileManager
        .contentsOfDirectoryAtURL(directory, includingPropertiesForKeys = null, options = 0u, error = null)
        .orEmpty()
        .filterIsInstance<NSURL>()
        .mapNotNull { url -> url.lastPathComponent?.let(DownloadFileNames::path)?.let { it to url } }
        .toMap()

    private fun NSURLSessionTask.path(): String? = taskDescription?.substringAfter('\n', "")?.takeIf { it.isNotEmpty() }

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
            downloadTask.path()?.let { listener?.onProgress(it, totalBytesWritten, totalBytesExpectedToWrite) }
        }

        // The temporary file is deleted when this returns, so it's moved here, before anything else
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL
        ) {
            val path = downloadTask.path() ?: return
            val response = downloadTask.response as? NSHTTPURLResponse
            if (response == null || response.statusCode !in 200L..299L) return listener?.onFailed(path) ?: Unit
            val mimeType = downloadTask.taskDescription!!.substringBefore('\n')
            val extension = DownloadFileNames.extension(mimeType, fallback = response.suggestedFilename?.substringAfterLast('.', ""))
            files()[path]?.let { fileManager.removeItemAtURL(it, null) }
            val destination = directory.URLByAppendingPathComponent(DownloadFileNames.fileName(path, extension))!!
            if (fileManager.moveItemAtURL(didFinishDownloadingToURL, destination, null)) {
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
            val error = didCompleteWithError ?: return
            if (error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled) return
            task.path()?.let { listener?.onFailed(it) }
        }
    }
}
