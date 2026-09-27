package com.simplecityapps.shuttle.sorting

/** SplitMix64's finaliser: spreads consecutive ids (and seeds) across the whole range, for seeded random orders. */
internal fun splitMix64(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}
