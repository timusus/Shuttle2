package com.simplecityapps.shuttle.parcel

import android.os.Parcel
import kotlinx.datetime.LocalDate

actual object LocalDateParceler : Parceler<LocalDate?> {

    override fun create(parcel: Parcel): LocalDate? {
        val string = parcel.readString() ?: ""
        if (string != "") {
            return LocalDate.parse(string)
        }
        return null
    }

    override fun LocalDate?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.toString() ?: "")
    }
}