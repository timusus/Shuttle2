package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

actual fun inMemoryMediaDatabaseBuilder(): RoomDatabase.Builder<MediaDatabase> = Room
    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediaDatabase::class.java)
    .allowMainThreadQueries()

// @RunWith is @Inherited, so subclasses in commonTest run under Robolectric without naming the runner.
@RunWith(AndroidJUnit4::class)
actual abstract class InMemoryDatabaseTest actual constructor()
