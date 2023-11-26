package com.simplecityapps.mediaprovider

sealed class FlowEvent<out T, out U> {
    class Progress<T, U>(val data: U) : FlowEvent<T, U>()

    class Success<T>(val result: T) : FlowEvent<T, Nothing>()

    class Failure(val message: String?) : FlowEvent<Nothing, Nothing>()
}

data class MessageProgress(
    val message: String,
    val progress: Progress?
)
