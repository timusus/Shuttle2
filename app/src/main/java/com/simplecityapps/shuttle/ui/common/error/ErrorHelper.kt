package com.simplecityapps.shuttle.ui.common.error

fun Error.userDescription(): String {
    return when (this) {
        is UserFriendlyError -> message
        is UnexpectedError -> "An unexpected error occurred."
        else -> "An unknown error occurred."
    }
}