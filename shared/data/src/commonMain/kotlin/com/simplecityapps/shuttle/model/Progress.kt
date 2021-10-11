package com.simplecityapps.shuttle.model

open class Progress(val progress: Int, val total: Int) {
    val asFloat: Float = progress / total.toFloat()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Progress

        if (progress != other.progress) return false
        if (total != other.total) return false

        return true
    }

    override fun hashCode(): Int {
        var result = progress
        result = 31 * result + total
        return result
    }

    override fun toString(): String {
        return "Progress(progress=$progress, total=$total, asFloat=$asFloat)"
    }
}