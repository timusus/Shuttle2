package com.simplecityapps.shuttle.ui.screens.changelog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Changelog(@Json(name = "commits") val commits: List<String>)