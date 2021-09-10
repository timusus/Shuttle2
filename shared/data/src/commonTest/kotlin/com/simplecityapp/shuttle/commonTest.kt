package com.simplecityapp.shuttle

import kotlin.test.Test
import kotlin.test.assertTrue

class CommonGreetingTest {

    @Test
    fun testExample() {
        assertTrue(com.simplecityapps.shuttle.Greeting().greeting().contains("Hello"), "Check 'Hello' is mentioned")
    }
}