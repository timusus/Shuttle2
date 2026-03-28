package com.simplecityapps.fakes

import com.simplecityapps.playback.queue.QueueWatcher

/**
 * Creates a [QueueWatcher] for testing. QueueWatcher is a concrete class that dispatches
 * callbacks to registered listeners. Call [QueueWatcher.onQueuePositionChanged] directly
 * to simulate queue position changes in tests.
 */
fun createTestQueueWatcher(): QueueWatcher = QueueWatcher()
