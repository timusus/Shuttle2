package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase

/** [MediaDatabase] in the app's databases directory, on the platform SQLite (Room's Android default). */
fun databaseBuilder(context: Context): RoomDatabase.Builder<MediaDatabase> = Room.databaseBuilder(context, MediaDatabase::class.java, DATABASE_NAME)
