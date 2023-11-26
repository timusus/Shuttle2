package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.BuildConfig
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_23_24
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_24_25
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_25_26
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_26_27
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_27_28
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_28_29
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_29_30
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_30_31
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_31_32
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_32_33
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_33_34
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_34_35
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_35_36
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_36_37
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_37_38
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_38_39
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_39_40

class DatabaseProvider(
    private val context: Context
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
                MIGRATION_36_37,
                MIGRATION_37_38,
                MIGRATION_38_39,
                MIGRATION_39_40,
            )
            .apply {
                if (!BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration()
                }
            }
            .build()
    }
}
