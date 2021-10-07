package com.simplecityapps.shuttle

import com.simplecityapps.shuttle.ui.ViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGreetingTest {

    @Test
    fun testExample() {
        assertTrue("Check Android is mentioned", ViewModel().greeting().contains("Android"))
    }
}