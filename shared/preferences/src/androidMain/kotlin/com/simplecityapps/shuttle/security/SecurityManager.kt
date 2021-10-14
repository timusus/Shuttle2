package com.simplecityapps.shuttle.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SecurityManager {

    private val cipher by lazy {
        Cipher.getInstance("AES/GCM/NoPadding")
    }

    private val keyGenerator by lazy {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        keyGenerator
    }

    private val key by lazy {
        keyGenerator.generateKey()
    }

    fun encryptData(data: ByteArray): ByteArray {
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun decryptData(encryptedData: ByteArray): ByteArray {
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, cipher.iv))
        return cipher.doFinal(encryptedData)
    }
}