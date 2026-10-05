package com.simplecityapps.fakes

import com.simplecityapps.shuttle.analytics.Analytics

/** Keeps every captured event, for assertions on what a use case or ViewModel records. */
class RecordingAnalytics : Analytics {
    data class Event(
        val name: String,
        val properties: Map<String, Any>
    )

    val events = mutableListOf<Event>()

    override fun capture(
        event: String,
        properties: Map<String, Any>
    ) {
        events += Event(event, properties)
    }

    /** The super properties registered, by name. */
    val registered = mutableMapOf<String, Any>()

    override fun register(
        name: String,
        value: Any
    ) {
        registered[name] = value
    }

    /** The captured events' names, in order. */
    val names: List<String> get() = events.map { it.name }
}
