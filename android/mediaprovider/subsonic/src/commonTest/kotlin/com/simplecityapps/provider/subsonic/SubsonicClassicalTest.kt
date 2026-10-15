package com.simplecityapps.provider.subsonic

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.subsonic.http.SongDto
import com.simplecityapps.shuttle.model.Performer
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** OpenSubsonic's classical fields (`contributors`, `displayComposer`, `works`, `movements`, `groupings`) on a song. */
class SubsonicClassicalTest {
    // The Json the client decodes with, so these tests see what a server response does
    private val json = S2Json

    private fun song(body: String) = json.decodeFromString<SongDto>("""{"id":"s1","title":"T"$body}""").toSong()

    @Test
    fun `an explicit null in each classical list decodes as an empty list`() {
        val dto =
            json.decodeFromString<SongDto>(
                """{"id":"s1","contributors":null,"works":null,"movements":null,"groupings":null}"""
            )

        dto.contributors shouldBe emptyList()
        dto.works shouldBe emptyList()
        dto.movements shouldBe emptyList()
        dto.groupings shouldBe emptyList()
    }

    @Test
    fun `a song maps its contributors - work - movement and grouping`() {
        val song =
            song(
                """
                ,"contributors":[
                  {"role":"composer","artist":{"id":"c1","name":"Johann Sebastian Bach"}},
                  {"role":"conductor","artist":{"id":"c2","name":"John Eliot Gardiner"}},
                  {"role":"arranger","artist":{"id":"c3","name":"Mozart"}},
                  {"role":"performer","subRole":"choir","artist":{"id":"c4","name":"Monteverdi Choir"}},
                  {"role":"performer","artist":{"id":"c5","name":"English Baroque Soloists"}}
                ],
                "works":[{"name":"Mass in B minor"}],
                "movements":[{"name":"Kyrie","number":1,"count":27}],
                "groupings":["Choral"]
                """
            )

        song.composer shouldBe "Johann Sebastian Bach"
        song.conductor shouldBe "John Eliot Gardiner"
        song.arranger shouldBe "Mozart"
        song.performers shouldBe listOf(Performer("Monteverdi Choir", "choir"), Performer("English Baroque Soloists"))
        song.work shouldBe "Mass in B minor"
        song.movementName shouldBe "Kyrie"
        song.movementNumber shouldBe 1
        song.movementTotal shouldBe 27
        song.grouping shouldBe "Choral"
    }

    @Test
    fun `displayComposer stands in when there are no composer contributors`() {
        song(""","displayComposer":"Ravel"""").composer shouldBe "Ravel"
    }

    @Test
    fun `a song from a server without the fields has no classical metadata`() {
        val song = song("")

        song.composer shouldBe null
        song.conductor shouldBe null
        song.arranger shouldBe null
        song.work shouldBe null
        song.movementName shouldBe null
        song.movementNumber shouldBe null
        song.movementTotal shouldBe null
        song.performers shouldBe null
        song.grouping shouldBe null
    }
}
