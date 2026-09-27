package com.simplecityapps

// The iOS tests run on one thread (runTest), so a plain list is enough
actual fun <T> threadSafeList(): MutableList<T> = mutableListOf()
