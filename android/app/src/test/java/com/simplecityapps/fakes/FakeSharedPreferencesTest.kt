package com.simplecityapps.fakes

import io.kotest.matchers.shouldBe
import org.junit.Test

class FakeSharedPreferencesTest {
    private val prefs = FakeSharedPreferences()

    @Test
    fun `remove then put on the same key keeps the put`() {
        prefs.edit().putString("key", "a").commit()

        prefs.edit().remove("key").putString("key", "b").commit()

        prefs.getString("key", null) shouldBe "b"
    }

    @Test
    fun `put then remove on the same key keeps the remove`() {
        prefs.edit().putString("key", "a").commit()

        prefs.edit().putString("key", "b").remove("key").commit()

        prefs.getString("key", null) shouldBe null
        prefs.contains("key") shouldBe false
    }

    @Test
    fun `clear applies before puts in the same editor regardless of call order`() {
        prefs.edit().putString("existing", "value").commit()

        prefs.edit().putString("new", "value").clear().commit()

        prefs.contains("existing") shouldBe false
        prefs.getString("new", null) shouldBe "value"
    }

    @Test
    fun `putString with a null value removes the key`() {
        prefs.edit().putString("key", "value").commit()

        prefs.edit().putString("key", null).commit()

        prefs.contains("key") shouldBe false
    }

    @Test
    fun `putStringSet with a null value removes the key`() {
        prefs.edit().putStringSet("key", setOf("value")).commit()

        prefs.edit().putStringSet("key", null).commit()

        prefs.contains("key") shouldBe false
    }

    @Test
    fun `commit returns true`() {
        prefs.edit().putString("key", "value").commit() shouldBe true
    }
}
