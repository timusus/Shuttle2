package com.simplecityapps.mediaprovider.repository

/**
 * Compares integers, moving zero values to the bottom
 */
val zeroLastComparator = Comparator<Int> { a, b ->
    if (a == b) {
        return@Comparator 0
    }
    if (a == 0) {
        return@Comparator 1
    }
    if (b == 0) {
        return@Comparator -1
    }
    return@Comparator 0
}