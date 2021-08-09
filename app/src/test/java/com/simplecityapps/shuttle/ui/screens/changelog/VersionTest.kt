package com.simplecityapps.shuttle.ui.screens.changelog

import com.vdurmont.semver4j.Semver
import org.junit.Test

class VersionTest {

    @Test
    fun testVersionIsValid() {
        Semver("0.1.0")
    }

    @Test
    fun testRcSemverIsValid() {
        Semver("1.0.0-RC1")
    }

    @Test
    fun testAlphaSemverIsValid() {
        Semver("1.0.0-alpha01")
    }

    @Test
    fun compareTo() {

        // Same values compare as equal

        var a = Semver("0.0.1")
        var b = Semver("0.0.1")
        assert(b.compareTo(a) == 0)

        a = Semver("0.1.1")
        b = Semver("0.1.1")
        assert(b.compareTo(a) == 0)

        a = Semver("1.1.1")
        b = Semver("1.1.1")
        assert(b.compareTo(a) == 0)

        // Larger values compare as larger

        a = Semver("0.0.1")
        b = Semver("0.0.2")
        assert(b > a)

        a = Semver("0.1.0")
        b = Semver("0.1.1")
        assert(b > a)

        a = Semver("1.0.0")
        b = Semver("1.0.1")
        assert(b > a)

        a = Semver("0.1.0")
        b = Semver("1.0.0")
        assert(b > a)

        // Check RC

        a = Semver("1.0.0-RC1")
        b = Semver("1.0.0-RC1")
        assert(b.compareTo(a) == 0)

        a = Semver("1.0.0-RC1")
        b = Semver("1.0.0-RC2")
        assert(b > a)

        a = Semver("1.0.0-RC1")
        b = Semver("1.0.1-RC1")
        assert(b > a)

        a = Semver("1.0.0-RC2")
        b = Semver("1.0.1-RC1")
        assert(b > a)

        a = Semver("1.0.0-RC2")
        b = Semver("1.0.1")
        assert(b > a)

        a = Semver("1.0.0-RC1")
        b = Semver("1.0.0")
        assert(b > a)

        a = Semver("1.0.0")
        b = Semver("1.0.0-RC1")
        assert(b < a)

        // Check alpha

        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.0-alpha01")
        assert(b.compareTo(a) == 0)

        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.0-alpha02")
        assert(b > a)

        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.1-alpha01")
        assert(b > a)

        a = Semver("1.0.0-alpha02")
        b = Semver("1.0.1-alpha01")
        assert(b > a)

        a = Semver("1.0.0-alpha02")
        b = Semver("1.0.1")
        assert(b > a)

        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.0")
        assert(b > a)

        a = Semver("1.0.0")
        b = Semver("1.0.0-alpha01")
        assert(b < a)

        // RC > Alpha
        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.0-RC01")
        assert(b > a)

        a = Semver("1.0.0-alpha01")
        b = Semver("1.0.1-RC01")
        assert(b > a)

        a = Semver("1.0.0-RC01")
        b = Semver("1.0.1-alpha01")
        assert(b > a)
    }
}