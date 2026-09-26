package com.simplecityapps.imageloading

import java.net.URLEncoder

fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
