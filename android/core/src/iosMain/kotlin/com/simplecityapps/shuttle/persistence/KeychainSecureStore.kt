package com.simplecityapps.shuttle.persistence

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * A [SecureStore] in the Keychain: one generic-password item per key, under [service]. Booleans are stored as
 * "true"/"false".
 *
 * Accessibility is `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, as Shuttle Podcasts' feed credentials:
 * readable by background work once the device has been unlocked, and never carried to another device in a backup.
 * A Keychain failure reads as nothing stored, so a server sign-in that has gone unreadable asks to sign in again
 * rather than crashing.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStore(
    private val service: String = DEFAULT_SERVICE
) : SecureStore {
    override fun getString(key: String): String? = memScoped {
        val query = newQuery(key)
        try {
            CFDictionaryAddValue(query.dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query.dict, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query.dict, result.ptr) != ERR_SEC_SUCCESS) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            data.toKotlinString()
        } finally {
            query.release()
        }
    }

    override fun putString(
        key: String,
        value: String?
    ) {
        remove(key)
        if (value == null) return
        val data = value.toNSData() ?: return
        val query = newQuery(key)
        try {
            query.addObject(kSecValueData, data)
            CFDictionaryAddValue(query.dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query.dict, null)
        } finally {
            query.release()
        }
    }

    override fun getBoolean(key: String): Boolean = getString(key).toBoolean()

    override fun putBoolean(
        key: String,
        value: Boolean
    ) = putString(key, value.toString())

    private fun remove(key: String) {
        val query = newQuery(key)
        try {
            SecItemDelete(query.dict)
        } finally {
            query.release()
        }
    }

    private fun newQuery(key: String): Query {
        val query = Query(
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )
        )
        CFDictionaryAddValue(query.dict, kSecClass, kSecClassGenericPassword)
        query.addObject(kSecAttrService, service)
        query.addObject(kSecAttrAccount, key)
        return query
    }

    /** A CFMutableDictionary plus the bridged values it owns, so they are all released together. */
    private class Query(
        val dict: CFMutableDictionaryRef?
    ) {
        private val owned = mutableListOf<COpaquePointer>()

        fun addObject(
            key: COpaquePointer?,
            value: Any
        ) {
            val bridged = CFBridgingRetain(value) ?: return
            owned += bridged
            CFDictionaryAddValue(dict, key, bridged)
        }

        fun release() {
            owned.forEach { CFRelease(it) }
            owned.clear()
            dict?.let { CFRelease(it) }
        }
    }

    companion object {
        const val DEFAULT_SERVICE: String = "com.simplecityapps.shuttle.secure"
        private const val ERR_SEC_SUCCESS = 0
    }
}

/** UTF-8 both ways. Kotlin `String` and `NSString` are distinct types on Native, so a cast is not it. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.toNSData(): NSData? {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKotlinString(): String? {
    val length = length.toInt()
    if (length == 0) return ""
    val pointer = bytes ?: return null
    return runCatching { pointer.readBytes(length).decodeToString() }.getOrNull()
}
