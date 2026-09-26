package com.simplecityapps.shuttle.debug.livelog

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Keeps the most recent [CAPACITY] lines; older ones drop off the front as new ones arrive. */
@Singleton
class InMemoryLiveLogBuffer
@Inject
constructor() :
    LiveLogBuffer,
    LiveLogSink {
    private val nextId = AtomicLong()
    private val _lines = MutableStateFlow<List<LiveLogLine>>(emptyList())
    override val lines: StateFlow<List<LiveLogLine>> = _lines.asStateFlow()

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        val text = if (t != null) "$message\n${t.stackTraceToString()}" else message
        val line = LiveLogLine(id = nextId.incrementAndGet(), timestampMillis = System.currentTimeMillis(), priority = priority, tag = tag, message = text)
        _lines.update { (it + line).takeLast(CAPACITY) }
    }

    override fun clear() {
        _lines.value = emptyList()
    }

    companion object {
        private const val CAPACITY = 500
    }
}
