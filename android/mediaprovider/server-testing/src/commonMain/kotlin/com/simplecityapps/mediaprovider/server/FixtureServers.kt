package com.simplecityapps.mediaprovider.server

/** The test resource at [path] (`jellyfin/songs.json`, say, from `src/commonTest/resources`), as text. */
expect fun readFixture(path: String): String

/** A [FixtureServer] answering with the JSON fixtures in the test resources' [fixtureDir] (`jellyfin`, say). */
fun FixtureServer(fixtureDir: String): FixtureServer = FixtureServer { name -> readFixture("$fixtureDir/$name") }
