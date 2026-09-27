package com.simplecityapps.shuttle.ui.screens.changelog

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One release's notes, as the bundled `changelog.json` lists them. */
@Serializable
data class Changeset(
    val versionName: String,
    @SerialName("releaseDate") val dateString: String,
    val features: List<String>,
    val fixes: List<String>,
    val improvements: List<String>,
    val notes: List<String>
) {
    /** [dateString] is `dd/MM/yyyy`. */
    val date: LocalDate
        get() = dateString.split('/').let { (day, month, year) -> LocalDate(year.toInt(), month.toInt(), day.toInt()) }
}
