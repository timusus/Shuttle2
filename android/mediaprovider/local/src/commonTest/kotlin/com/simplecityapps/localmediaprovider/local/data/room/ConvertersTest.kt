package com.simplecityapps.localmediaprovider.local.data.room

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ConvertersTest {
    private val converters = Converters()

    // What the java.util.Date converter stored and read: Date.time, the epoch milliseconds (#584).
    private val storedMillis = listOf(0L, 1L, 1_750_000_000_000L, -86_400_000L, Long.MAX_VALUE / 1_000, Long.MIN_VALUE / 1_000)

    @Test
    fun anInstantIsStoredAsItsEpochMilliseconds() {
        storedMillis.forEach { millis ->
            assertEquals(millis, converters.instantToTimestamp(Instant.fromEpochMilliseconds(millis)))
        }
    }

    @Test
    fun aStoredValueReadsBackAsTheSameInstant() {
        storedMillis.forEach { millis ->
            val instant = converters.fromTimestamp(millis)
            assertEquals(Instant.fromEpochMilliseconds(millis), instant)
            assertEquals(millis, converters.instantToTimestamp(instant))
        }
    }

    @Test
    fun anInstantFinerThanAMillisecondIsStoredTruncated() {
        assertEquals(1_750_000_000_123L, converters.instantToTimestamp(Instant.fromEpochSeconds(1_750_000_000, 123_456_789)))
    }

    @Test
    fun nullStaysNull() {
        assertNull(converters.fromTimestamp(null))
        assertNull(converters.instantToTimestamp(null))
    }

    @Test
    fun aStringListIsSemicolonJoinedAndSkipsEmptyEntries() {
        assertEquals("a;b", converters.toString(listOf("a", "b")))
        assertEquals(listOf("a", "b"), converters.fromString("a;;b;"))
        assertEquals(emptyList(), converters.fromString(""))
    }

    @Test
    fun enumsAreStoredByName() {
        assertEquals("Jellyfin", converters.fromMediaProvider(MediaProviderType.Jellyfin))
        assertEquals(MediaProviderType.Jellyfin, converters.toMediaProvider("Jellyfin"))
        assertEquals(SongSortOrder.Default, converters.toSortOrder("NoLongerASortOrder"))
    }
}
