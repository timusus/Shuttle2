package com.simplecityapps.shuttle.persistence

import com.simplecityapps.shuttle.settings.Preference
import com.simplecityapps.shuttle.settings.Setting
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** [InMemoryKeyValueStore] behaves as SharedPreferences does, so tests over it see what the app sees. */
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryKeyValueStoreTest {
    private val store = InMemoryKeyValueStore()

    @Test
    fun `values read back by type and missing keys read as the default`() {
        store.edit {
            putBoolean("b", true)
            putInt("i", 1)
            putLong("l", 2L)
            putFloat("f", 3f)
            putString("s", "x")
        }

        store.getBoolean("b", false) shouldBe true
        store.getInt("i", 0) shouldBe 1
        store.getLong("l", 0) shouldBe 2L
        store.getFloat("f", 0f) shouldBe 3f
        store.getString("s", null) shouldBe "x"
        store.getInt("missing", 7) shouldBe 7
    }

    @Test
    fun `a read of a key stored with another type throws`() {
        store.putString("key", "x")

        shouldThrow<ClassCastException> { store.getInt("key", 0) }
    }

    @Test
    fun `storing a null string removes the key`() {
        store.putString("key", "x")

        store.putString("key", null)

        store.contains("key") shouldBe false
    }

    @Test
    fun `a setting stored with another type reads as its default`() {
        val preference = Preference(store, Setting.int("key", 5))
        store.putString("key", "x")

        preference.value shouldBe 5
        preference.isSet() shouldBe true

        preference.reset()
        preference.isSet() shouldBe false
    }

    @Test
    fun `changes emit on subscribing then for each write or removal or clear of the key`() = runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<Unit>()
        val job = launch { store.changes("key").toList(emissions) }

        store.putInt("other", 1)
        store.putInt("key", 1)
        store.remove("key")
        store.clear()
        job.cancel()

        emissions.size shouldBe 4
    }
}
