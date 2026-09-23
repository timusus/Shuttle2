package com.simplecityapps.playback.exoplayer

import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class ByteUtilsTest {
    @Test
    fun `round trips positive, negative, max and min 24 bit values`() {
        listOf(0, 1, -1, 12345, -12345, Int24_MAX_VALUE, Int24_MIN_VALUE).forEach { value ->
            val buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt24(value)
            buffer.flip()
            buffer.getInt24() shouldBe value
        }
    }

    @Test
    fun `getInt24 reads exactly 3 bytes and advances position by 3`() {
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt24(Int24_MAX_VALUE)
        buffer.putInt24(Int24_MIN_VALUE)
        buffer.flip()

        buffer.position() shouldBe 0
        buffer.getInt24()
        buffer.position() shouldBe 3
        buffer.getInt24()
        buffer.position() shouldBe 6
    }

    @Test
    fun `putInt24 writes exactly 3 bytes and advances position by 3`() {
        val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)

        buffer.position() shouldBe 0
        buffer.putInt24(1)
        buffer.position() shouldBe 3
        buffer.putInt24(2)
        buffer.position() shouldBe 6
        buffer.putInt24(3)
        buffer.position() shouldBe 9
    }

    @Test
    fun `honours big endian byte order`() {
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt24(305419)
        buffer.flip()
        buffer.getInt24() shouldBe 305419
    }
}
