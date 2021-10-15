package com.simplecityapps.shuttle.security

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecurityManager {

    private val cipher by lazy {
        Cipher.getInstance("AES/GCM/NoPadding")
    }

    private val secretKey by lazy {
        SecretKeySpec(Base64.decode("yn3uZljJFfMg0W6BQi+JHg==", Base64.DEFAULT), "AES")
    }

    private val secureRandom by lazy { SecureRandom() }

    fun encryptData(data: ByteArray): String {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val cipherData = cipher.doFinal(data)
        val byteBuffer = ByteBuffer.allocate(iv.size + cipherData.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherData)
        return Base64.encodeToString(byteBuffer.array(), Base64.DEFAULT)
    }

    fun decryptData(data: String): ByteArray {
        val message = Base64.decode(data, Base64.DEFAULT)
        val parameterSpec = GCMParameterSpec(128, message, 0, 12)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        return cipher.doFinal(message, 12, message.size - 12)
    }
}