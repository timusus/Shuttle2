package com.simplecityapps.shuttle.parcel

import android.os.Parcel
import kotlinx.datetime.LocalDate

actual object LocalDateParceler : Parceler<LocalDate?> {

    override fun create(parcel: Parcel) = LocalDate.parse(parcel.readString()!!)

    override fun LocalDate?.write(parcel: Parcel, flags: Int) {
        this?.toString()?.let { parcel.writeString(it) }
    }
}