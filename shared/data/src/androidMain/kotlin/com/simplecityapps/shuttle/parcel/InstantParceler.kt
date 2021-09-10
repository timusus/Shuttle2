package com.simplecityapps.shuttle.parcel

import android.os.Parcel
import kotlinx.datetime.Instant

actual object InstantParceler : Parceler<Instant?> {

    override fun create(parcel: Parcel) = Instant.fromEpochSeconds(parcel.readLong())

    override fun Instant?.write(parcel: Parcel, flags: Int) {
        this?.let { parcel.writeLong(epochSeconds) }
    }
}