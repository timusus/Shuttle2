package au.com.simplecityapps.shuttle.imageloading.coil

import java.net.URLEncoder

fun String.encode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}