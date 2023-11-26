package com.simplecityapps.shuttle.ui.common.view

enum class SeekDirection {
    Forward,
    Backward
    ;

    companion object {
        fun fromInt(value: Int): SeekDirection {
            return when (value) {
                0 -> Forward
                1 -> Backward
                else -> throw IllegalStateException("Invalid seek direction value")
            }
        }
    }
}
