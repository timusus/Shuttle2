package com.simplecityapps.shuttle.shared.downloads

/**
 * Which download each path wants, for [UrlSessionDownloads]: the [Task] it's running, one still [Pending] (finding its
 * address before its task is made), or neither. At most one of the two per path, so a path never has two tasks: a
 * task the session lists for a path that's pending or running another is stale, and cancelled with [cancelTask].
 *
 * A task nobody has claimed yet (an earlier launch's, before [found] lists it) is still wanted: it's the only one for its
 * path. Main-queue only, as the session's delegate calls are.
 */
internal class WantedDownloads<Task : Any, Pending : Any>(
    private val sameTask: (Task, Task) -> Boolean,
    private val cancelTask: (Task) -> Unit,
    private val cancelPending: (Pending) -> Unit
) {
    private val running = mutableMapOf<String, Task>()
    private val pending = mutableMapOf<String, Pending>()

    /** Forgets [path]'s wanted download, for a restart or a removal, cancelling its task or its pending start. */
    fun clear(path: String) {
        running.remove(path)?.let(cancelTask)
        pending.remove(path)?.let(cancelPending)
    }

    /** [path]'s download is [pendingStart] until it's [resolved], replacing any it had. */
    fun pend(
        path: String,
        pendingStart: Pending
    ) {
        clear(path)
        pending[path] = pendingStart
    }

    /** Whether [pendingStart] is still [path]'s, and so may make its task: it's no longer pending either way. */
    fun resolved(
        path: String,
        pendingStart: Pending
    ): Boolean {
        if (pending[path] !== pendingStart) return false
        pending.remove(path)
        return true
    }

    /** [task] is now [path]'s download, replacing any it had. */
    fun run(
        path: String,
        task: Task
    ) {
        clear(path)
        running[path] = task
    }

    /**
     * Takes [task], which the session listed for [path] at launch: adopted (true) if the path wants nothing else, and
     * cancelled if it's pending or running another.
     */
    fun found(
        path: String,
        task: Task
    ): Boolean {
        val current = running[path]
        return when {
            current == null && path !in pending -> {
                running[path] = task
                true
            }

            current == null || !sameTask(current, task) -> {
                cancelTask(task)
                false
            }

            else -> false
        }
    }

    /** Whether [task]'s progress and outcome are [path]'s: not while it's pending, nor if it's running another. */
    fun isWanted(
        path: String,
        task: Task
    ): Boolean = path !in pending && (running[path]?.let { sameTask(it, task) } ?: true)

    /** Whether [task] is the one [path] is running: any other the session lists for it after a removal is stale. */
    fun isRunning(
        path: String,
        task: Task
    ): Boolean = running[path]?.let { sameTask(it, task) } == true

    /** [path]'s wanted task has ended: whether it was one this held, rather than an earlier launch's not yet [found]. */
    fun finished(path: String): Boolean = running.remove(path) != null
}
