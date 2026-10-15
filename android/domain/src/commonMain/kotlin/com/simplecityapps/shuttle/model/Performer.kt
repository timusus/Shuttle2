package com.simplecityapps.shuttle.model

/** A performer credited on a song, with the instrument or role when the tag names one. */
data class Performer(val name: String, val role: String? = null)
