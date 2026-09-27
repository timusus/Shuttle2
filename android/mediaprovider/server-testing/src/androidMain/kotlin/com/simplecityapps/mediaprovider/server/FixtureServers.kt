package com.simplecityapps.mediaprovider.server

/** A [FixtureServer] answering with the JSON fixtures in the test resources' [fixtureDir] (`src/androidHostTest/resources/jellyfin`, say). */
fun FixtureServer(fixtureDir: String): FixtureServer = FixtureServer { name ->
    checkNotNull(FixtureServer::class.java.classLoader.getResource("$fixtureDir/$name")) { "No fixture $fixtureDir/$name" }.readText()
}
