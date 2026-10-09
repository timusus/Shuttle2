package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.RoomDatabase
import com.simplecityapps.localmediaprovider.local.data.room.inMemoryDatabaseBuilder

actual fun inMemoryMediaDatabaseBuilder(): RoomDatabase.Builder<MediaDatabase> = inMemoryDatabaseBuilder()

actual abstract class InMemoryDatabaseTest actual constructor()
