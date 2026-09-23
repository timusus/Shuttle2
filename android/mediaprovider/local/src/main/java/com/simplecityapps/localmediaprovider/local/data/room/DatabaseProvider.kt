package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.BuildConfig
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.migrations.ALL_MIGRATIONS

class DatabaseProvider(
    private val context: Context
) {
    val database: MediaDatabase by lazy {
        Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .addMigrations(*ALL_MIGRATIONS)
            .apply {
                if (!BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration()
                }
            }
            .build()
    }
}
