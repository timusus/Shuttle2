package com.simplecityapps

import java.util.Collections

actual fun <T> threadSafeList(): MutableList<T> = Collections.synchronizedList(mutableListOf())
