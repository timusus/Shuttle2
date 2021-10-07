package com.simplecityapps.shuttle.ui.common

data class Progress(val progress: Int, val total: Int) {
    fun asFloat(): Float {
        return progress / total.toFloat()
    }
}