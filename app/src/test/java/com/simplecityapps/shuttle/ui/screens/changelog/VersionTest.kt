package com.simplecityapps.shuttle.ui.screens.changelog

import org.junit.Test

class VersionTest {

    @Test
    fun testVersionIsValid() {
        Version("0.1.0")
    }

    @Test
    fun testRcVersionIsValid() {
        Version("1.0.0-RC1")
    }

    @Test
    fun compareTo() {

        // Same values compare as equal

        var a = Version("0.0.1")
        var b = Version("0.0.1")
        assert(b.compareTo(a) == 0)

        a = Version("0.1.1")
        b = Version("0.1.1")
        assert(b.compareTo(a) == 0)

        a = Version("1.1.1")
        b = Version("1.1.1")
        assert(b.compareTo(a) == 0)

        // Larger values compare as larger

        a = Version("0.0.1")
        b = Version("0.0.2")
        assert(b > a)

        a = Version("0.1.0")
        b = Version("0.1.1")
        assert(b > a)

        a = Version("1.0.0")
        b = Version("1.0.1")
        assert(b > a)

        a = Version("0.1.0")
        b = Version("1.0.0")
        assert(b > a)

        // Check suffix

        a = Version("1.0.0-RC1")
        b = Version("1.0.0-RC1")
        assert(b.compareTo(a) == 0)

        a = Version("1.0.0-RC1")
        b = Version("1.0.0-RC2")
        assert(b > a)

        a = Version("1.0.0-RC1")
        b = Version("1.0.1-RC1")
        assert(b > a)

        a = Version("1.0.0-RC2")
        b = Version("1.0.1-RC1")
        assert(b > a)

        a = Version("1.0.0-RC2")
        b = Version("1.0.1")
        assert(b > a)

        a = Version("1.0.0-RC1")
        b = Version("1.0.0")
        assert(b > a)

        a = Version("1.0.0")
        b = Version("1.0.0-RC1")
        assert(b < a)
    }
}