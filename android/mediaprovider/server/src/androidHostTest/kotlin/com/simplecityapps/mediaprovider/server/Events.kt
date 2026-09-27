package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent

/** What an event carries, comparable with `shouldBe` (FlowEvent has no equals). */
sealed interface Event {
    data class Progress(val data: Any?) : Event

    data class Success(val result: Any?) : Event

    data class Failure(val message: String?) : Event
}

fun <T, U> List<FlowEvent<T, U>>.described(): List<Event> = map { event ->
    when (event) {
        is FlowEvent.Progress -> Event.Progress(event.data)
        is FlowEvent.Success -> Event.Success(event.result)
        is FlowEvent.Failure -> Event.Failure(event.message)
    }
}
