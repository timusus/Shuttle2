package com.simplecityapps.mediaprovider

data class Progress(val progress: Int, val total: Int) {
    fun asFloat(): Float {
        return progress / total.toFloat()
    }
}