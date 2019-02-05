package com.simplecityapps.localmediaprovider

import com.simplecityapps.localmediaprovider.Diff.Companion.diff
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffTest {

    class Song(val title: String, val lastModified: Long) : ContentsComparator<Song> {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Song

            if (title != other.title) return false

            return true
        }

        override fun hashCode(): Int {
            return title.hashCode()
        }

        override fun areContentsEqual(other: Song): Boolean {
            return lastModified == other.lastModified
        }
    }

    @Test
    fun testDiff() {

        // 1. Test that two equal data sets result in 0 diff results

        var oldData = mutableListOf(
            Song("First", 0),
            Song("Second", 0)
        )

        var newData = mutableListOf(
            Song("First", 0),
            Song("Second", 0)
        )

        var diff = diff(oldData, newData)

        assertEquals(diff.insertions.size, 0)
        assertEquals(diff.deletions.size, 0)
        assertEquals(diff.updates.size, 0)


        // 2. Test that new data results in a diff insertion

        oldData = mutableListOf(
            Song("First", 0)
        )

        newData = mutableListOf(
            Song("First", 0),
            Song("Second", 0)
        )

        diff = diff(oldData, newData)

        assertEquals(diff.insertions.size, 1)
        assertEquals(diff.deletions.size, 0)
        assertEquals(diff.updates.size, 0)


        // 3. Test that removed data results in a diff deletion

        oldData = mutableListOf(
            Song("First", 0),
            Song("Second", 0)
        )

        newData = mutableListOf(
            Song("First", 0)
        )

        diff = diff(oldData, newData)


        assertEquals(diff.insertions.size, 0)
        assertEquals(diff.deletions.size, 1)
        assertEquals(diff.updates.size, 0)


        // 4. Test that changed data results in a diff update

        oldData = mutableListOf(
            Song("First", 0)
        )

        newData = mutableListOf(
            Song("First", 1)
        )

        diff = diff(oldData, newData)

        assertEquals(diff.insertions.size, 0)
        assertEquals(diff.deletions.size, 0)
        assertEquals(diff.updates.size, 1)


        // 5. Test all of the above

        oldData = mutableListOf(
            Song("First", 0),
            Song("Second", 0),
            Song("Third", 0),
            Song("Fourth", 0)
        )

        newData = mutableListOf(
            Song("Second", 0),
            Song("Third", 1),
            Song("Fourth", 0),
            Song("Fifth", 0)
        )

        diff = diff(oldData, newData)

        assertEquals(diff.insertions.size, 1)
        assertEquals(diff.deletions.size, 1)
        assertEquals(diff.updates.size, 1)
    }

}