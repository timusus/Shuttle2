package com.simplecityapp.shuttle

import kotlin.test.Test
import kotlin.test.assertTrue

class IosGreetingTest {

    @Test
    fun testExample() {
        assertTrue(com.simplecityapps.shuttle.Greeting().greeting().contains("iOS"), "Check iOS is mentioned")
    }
}