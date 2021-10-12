package com.simplecityapps.shuttle

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGreetingTest {

    @Test
    fun testExample() {
        assertTrue("Check Android is mentioned", com.simplecityapps.shuttle.Greeting().greeting().contains("Android"))
    }
}