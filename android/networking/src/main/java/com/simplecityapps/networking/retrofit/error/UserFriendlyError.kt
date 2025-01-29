package com.simplecityapps.networking.retrofit.error

class UserFriendlyError(override var message: String) : Error(message) {
    override fun toString(): String = "UserFriendlyError(message: $message)"
}
