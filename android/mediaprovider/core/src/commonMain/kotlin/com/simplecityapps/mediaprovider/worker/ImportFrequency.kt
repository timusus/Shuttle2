package com.simplecityapps.mediaprovider.worker

/** How often the library is rescanned in the background (by `MediaImportWorker` on Android), as Settings > Library stores it. */
enum class ImportFrequency(val value: Int) {
    Never(0),
    Daily(1),
    Weekly(2)
    ;

    fun intervalInDays(): Long = when (this) {
        Never -> 0L
        Daily -> 1L
        Weekly -> 7L
    }
}
