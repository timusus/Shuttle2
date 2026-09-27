package com.simplecityapps.mediaprovider.server

actual fun readFixture(path: String): String = checkNotNull(FixtureServer::class.java.classLoader.getResource(path)) { "No fixture $path" }.readText()
