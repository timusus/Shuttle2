package com.simplecityapps.shuttle.error

class UnexpectedError(override val cause: Throwable) : Throwable() {

    override fun toString(): String {
        return "UnexpectedError (${cause.message})"
    }
}