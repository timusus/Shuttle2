package com.simplecityapps.shuttle.parcel

import android.os.Parcel
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parceler

actual object InstantParceler : Parceler<Instant?> {

    override fun create(parcel: Parcel): Instant? {
        val epochSeconds = parcel.readLong()
        if (epochSeconds != -1L) {
            return Instant.fromEpochSeconds(epochSeconds)
        }
        return null
    }

    override fun Instant?.write(parcel: Parcel, flags: Int) {
        parcel.writeLong(this?.epochSeconds ?: -1L)
    }
}
