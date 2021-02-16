package com.simplecityapps.shuttle.ui.screens.changelog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.*

@JsonClass(generateAdapter = true)
data class Changeset(
    @Json(name = "versionName") val versionName: String,
    @Json(name = "releaseDate") val dateString: String,
    @Json(name = "features") val features: List<String>,
    @Json(name = "fixes") val fixes: List<String>,
    @Json(name = "improvements") val improvements: List<String>,
    @Json(name = "notes") val notes: List<String>
) {
    val version: Version = Version(versionName)
    val date: Date = dateFormat.parse(dateString)!!

    companion object {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    }
}