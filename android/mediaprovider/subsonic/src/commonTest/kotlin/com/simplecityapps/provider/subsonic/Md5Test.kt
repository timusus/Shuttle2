package com.simplecityapps.provider.subsonic

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The hand-written MD5 against RFC 1321's test suite, and the Subsonic API documentation's token example. */
class Md5Test {
    @Test
    fun `matches the RFC 1321 test suite`() {
        md5Hex("") shouldBe "d41d8cd98f00b204e9800998ecf8427e"
        md5Hex("a") shouldBe "0cc175b9c0f1b6a831c399e269772661"
        md5Hex("abc") shouldBe "900150983cd24fb0d6963f7d28e17f72"
        md5Hex("message digest") shouldBe "f96b697d7cb7938d525a2f31aaf161d0"
        md5Hex("abcdefghijklmnopqrstuvwxyz") shouldBe "c3fcd3d76192e4007dfb496cca67e13b"
        md5Hex("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789") shouldBe "d174ab98d277d9f5a5611c2c9f419d9f"
        md5Hex("12345678901234567890123456789012345678901234567890123456789012345678901234567890") shouldBe "57edf4a22be3c955ac49da2e2107b67a"
    }

    @Test
    fun `hashes UTF-8`() {
        md5Hex("é") shouldBe "66ddcd97cfdeabb2f6fb8a999b4bc76f"
        md5Hex("pässwörd") shouldBe "12841e4ba5e37d2fbfc78458c6714ade"
    }

    @Test
    fun `makes the Subsonic documentation's token`() {
        md5Hex("sesame" + "c19b2d") shouldBe "26719a1196d2a940705a59634eb18eab"
    }
}
