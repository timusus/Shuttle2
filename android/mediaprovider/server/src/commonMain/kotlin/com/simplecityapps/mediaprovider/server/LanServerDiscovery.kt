package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Sends one UDP broadcast and collects the replies that arrive within a window, as text. */
interface LanBroadcaster {
    suspend fun broadcast(
        message: String,
        port: Int,
        listenMillis: Long,
    ): List<String>
}

/**
 * Finds Jellyfin and Emby servers on the local network with their discovery broadcast: "who is JellyfinServer?" (or
 * EmbyServer) on UDP port 7359, which each server answers with its address, id and name as JSON. Plex and Subsonic
 * have no such broadcast here, so they find nothing.
 */
@ContributesBinding(AppScope::class)
@Inject
class LanServerDiscovery(
    private val broadcaster: LanBroadcaster,
) : ServerDiscovery {
    override suspend fun discover(type: MediaProviderType): List<DiscoveredServer> {
        val query = when (type) {
            MediaProviderType.Jellyfin -> JELLYFIN_QUERY
            MediaProviderType.Emby -> EMBY_QUERY
            else -> return emptyList()
        }
        val replies = try {
            broadcaster.broadcast(query, DISCOVERY_PORT, LISTEN_MILLIS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        return replies
            .mapNotNull { parseDiscoveryReply(it) }
            .distinctBy { it.id }
            .map { DiscoveredServer(it.name, it.address) }
    }

    internal data class Reply(
        val id: String,
        val name: String,
        val address: String,
    )

    companion object {
        const val DISCOVERY_PORT = 7359
        const val LISTEN_MILLIS = 1_500L
        const val JELLYFIN_QUERY = "who is JellyfinServer?"
        const val EMBY_QUERY = "who is EmbyServer?"

        /** A server's answer, or null when it isn't one: `{"Address": "http://…", "Id": "…", "Name": "…"}`. */
        internal fun parseDiscoveryReply(reply: String): Reply? {
            val json = try {
                Json.parseToJsonElement(reply) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return null
            val address = json.string("Address")?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
            val id = json.string("Id") ?: address
            val name = json.string("Name") ?: address
            return Reply(id, name, address)
        }

        private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }
}
