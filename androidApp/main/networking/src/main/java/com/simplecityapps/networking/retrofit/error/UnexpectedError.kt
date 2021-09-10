package com.simplecityapps.networking.retrofit.error

class UnexpectedError(val t: Throwable) : Error() {

    override fun toString(): String {
        return "UnexpectedError (${t.localizedMessage})"
    }
}