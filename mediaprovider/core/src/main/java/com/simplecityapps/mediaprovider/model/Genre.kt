package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.simplecityapps.mediaprovider.MediaProvider
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class Genre(
    val name: String,
    val songCount: Int,
    val duration: Int,
    val mediaProviders: List<MediaProvider.Type>
) : Parcelable