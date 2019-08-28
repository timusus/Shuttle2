package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import java.net.URLEncoder

fun String.encode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}