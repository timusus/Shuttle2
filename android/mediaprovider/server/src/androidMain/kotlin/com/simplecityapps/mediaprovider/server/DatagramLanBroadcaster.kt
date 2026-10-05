package com.simplecityapps.mediaprovider.server

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Broadcasts to 255.255.255.255 from an ephemeral port and reads replies until [LanBroadcaster.broadcast]'s window
 * closes. Needs no permission beyond INTERNET: a broadcast on the local subnet isn't Android 17's local network access
 * (not enforced below targetSdk 37 anyway).
 */
@ContributesBinding(AppScope::class)
@Inject
class DatagramLanBroadcaster : LanBroadcaster {
    override suspend fun broadcast(
        message: String,
        port: Int,
        listenMillis: Long,
    ): List<String> = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val bytes = message.encodeToByteArray()
            socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(BROADCAST_ADDRESS), port))

            val replies = mutableListOf<String>()
            val buffer = ByteArray(MAX_REPLY_BYTES)
            val deadline = System.nanoTime() + listenMillis * 1_000_000
            while (true) {
                ensureActive()
                val remainingMillis = (deadline - System.nanoTime()) / 1_000_000
                if (remainingMillis <= 0) break
                socket.soTimeout = remainingMillis.toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    break
                }
                replies += String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            }
            replies
        }
    }

    private companion object {
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val MAX_REPLY_BYTES = 4_096
    }
}
