package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import io.reactivex.Single

interface SongProvider {
    fun findSongs(): Single<List<SongData>>
}