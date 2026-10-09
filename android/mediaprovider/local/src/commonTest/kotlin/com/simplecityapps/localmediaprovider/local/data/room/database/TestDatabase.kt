package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.RoomDatabase

/** An empty in-memory [MediaDatabase] builder on the platform's production driver; tests add callbacks and `build()` it. */
expect fun inMemoryMediaDatabaseBuilder(): RoomDatabase.Builder<MediaDatabase>

/** Base of every test that opens [inMemoryMediaDatabaseBuilder]: on Android it supplies the Robolectric context the builder needs. */
expect abstract class InMemoryDatabaseTest()
