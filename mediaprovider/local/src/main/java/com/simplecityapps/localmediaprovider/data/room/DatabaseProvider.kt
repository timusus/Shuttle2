package com.simplecityapps.localmediaprovider.data.room

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase

class DatabaseProvider constructor(context: Context) {

    val database: MediaDatabase by lazy {
        Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}