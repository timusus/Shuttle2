package com.simplecityapps.shuttle.model

/** Songs shorter than this are hidden from the library (ringtones, voice notes); songs in a playlist still show there. */
enum class MinTrackLength(val seconds: Int) {
    Off(0),
    TenSeconds(10),
    ThirtySeconds(30),
    SixtySeconds(60);

    /** A song exactly [seconds] long is kept; one with no known duration is too, since it can't be judged. */
    fun keeps(song: Song): Boolean = song.duration <= 0 || song.duration >= seconds * 1000
}
