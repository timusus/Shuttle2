package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplecityapps.localmediaprovider.BuildConfig
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.migrations.*
import com.simplecityapps.mediaprovider.repository.PlaylistSongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Named

class DatabaseProvider constructor(
    private val context: Context,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) {

    val database: MediaDatabase by lazy {
        Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .addMigrations(
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_36_37
            )
            .addCallback(callback)
            .apply {
                if (!BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration()
                }
            }
            .build()
    }

    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)

            Timber.i("Database opened at version ${db.version}")
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            appCoroutineScope.launch {
                database.playlistDataDao().insert(
                    PlaylistData(
                        name = favoritesName,
                        sortOrder = PlaylistSongSortOrder.Position
                    )
                )
            }
        }
    }
}