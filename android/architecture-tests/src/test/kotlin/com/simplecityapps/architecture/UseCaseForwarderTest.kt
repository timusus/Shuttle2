package com.simplecityapps.architecture

import org.junit.Assert.assertEquals
import org.junit.Test

class UseCaseForwarderTest {

    private fun forwards(invoke: String, vararg parameters: String, own: List<String> = listOf("repo")) = forwardsToOwnProperty(invoke, parameters.toList(), own)

    @Test
    fun `property read is a forwarder`() {
        assertEquals(true, forwards("operator fun invoke() = repo.folders"))
    }

    @Test
    fun `any lambda-free chain is a forwarder`() {
        assertEquals(true, forwards("operator fun invoke(id: Long) = repo.get(id).distinctUntilChanged().firstOrNull()", "id"))
    }

    @Test
    fun `call through this is a forwarder`() {
        assertEquals(true, forwards("operator fun invoke(id: Long) = this.repo.get(id)", "id"))
    }

    @Test
    fun `explicit generics and dotted receivers are forwarders`() {
        assertEquals(true, forwards("operator fun invoke(id: Long) = repo.songs.query<Song>(id)", "id"))
    }

    @Test
    fun `forwarding to a body property is a forwarder`() {
        assertEquals(true, forwards("operator fun invoke(id: Long) = songs.get(id)", "id", own = listOf("repo", "songs")))
        assertEquals(false, forwards("operator fun invoke(id: Long) = other.get(id)", "id"))
    }

    @Test
    fun `real work is not a forwarder`() {
        assertEquals(false, forwards("operator fun invoke() = repo.songs.map { it.name }"))
        assertEquals(false, forwards("operator fun invoke(id: Long) = repo.get(id + 1)", "id"))
        assertEquals(false, forwards("operator fun invoke() = repo.songs.size + 1"))
        assertEquals(false, forwards("operator fun invoke(id: Long) = repo.songs", "id"))
    }
}
