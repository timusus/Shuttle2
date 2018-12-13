package com.simplecityapps.localmediaprovider.data.room

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase

// Todo: Make Singleton & Inject via DI
object DatabaseProvider {

    private var database: MediaDatabase? = null

    fun getDatabase(context: Context): MediaDatabase {
        return database ?: initDatabase(context)
    }

    fun initDatabase(context: Context): MediaDatabase {
        database = Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .fallbackToDestructiveMigration()
            .build()

        return database!!
    }
}