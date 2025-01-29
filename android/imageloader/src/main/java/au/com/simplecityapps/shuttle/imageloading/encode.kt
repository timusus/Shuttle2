package au.com.simplecityapps.shuttle.imageloading

import java.net.URLEncoder

fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
