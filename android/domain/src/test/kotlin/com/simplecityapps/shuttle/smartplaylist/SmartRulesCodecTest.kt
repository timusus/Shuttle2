package com.simplecityapps.shuttle.smartplaylist

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import org.junit.Test

class SmartRulesCodecTest {
    private val everyRule = SmartRules(
        match = SmartRules.Match.Any,
        rules = TextField.entries.flatMap { field -> TextOperator.entries.map { operator -> Rule.Text(field, operator, "value") } } +
            NumberField.entries.flatMap { field ->
                listOf(
                    NumberCondition.Is(1),
                    NumberCondition.IsNot(2),
                    NumberCondition.LessThan(3),
                    NumberCondition.GreaterThan(4),
                    NumberCondition.Between(5, 6),
                ).map { condition -> Rule.Number(field, condition) }
            } +
            DateField.entries.flatMap { field ->
                listOf(
                    DateCondition.InTheLast(7),
                    DateCondition.NotInTheLast(180),
                    DateCondition.Before(LocalDate(2020, 1, 31)),
                    DateCondition.After(LocalDate(2021, 12, 1)),
                ).map { condition -> Rule.Date(field, condition) }
            } +
            MediaProviderType.entries.flatMap { provider -> EnumOperator.entries.map { operator -> Rule.Provider(operator, provider) } } +
            Song.Type.entries.flatMap { type -> EnumOperator.entries.map { operator -> Rule.Type(operator, type) } } +
            listOf(Rule.Favourite(true), Rule.Favourite(false)),
        sort = SmartSort.LastPlayed,
        descending = true,
        limit = Limit.Minutes(60),
    )

    @Test
    fun `every rule, sort and limit survives a round trip`() {
        SmartRulesCodec.decode(SmartRulesCodec.encode(everyRule)) shouldBe everyRule
        SmartSort.entries.forEach { sort -> SmartRulesCodec.decode(SmartRulesCodec.encode(SmartRules(sort = sort))) shouldBe SmartRules(sort = sort) }
        SmartRulesCodec.decode(SmartRulesCodec.encode(SmartRules(limit = Limit.Songs(25)))) shouldBe SmartRules(limit = Limit.Songs(25))
        SmartRulesCodec.decode(SmartRulesCodec.encode(SmartRules())) shouldBe SmartRules()
    }

    /** The stored form is a contract: this JSON must keep decoding to the same rules whatever the classes are renamed to. */
    @Test
    fun `decodes the version 1 format`() {
        val json =
            """
            {"version":1,"rules":{"match":"all","rules":[
              {"type":"text","field":"genre","operator":"contains","value":"rock"},
              {"type":"number","field":"play-count","condition":{"type":"between","min":1,"max":5}},
              {"type":"date","field":"last-played","condition":{"type":"not-in-the-last","days":180}},
              {"type":"date","field":"date-added","condition":{"type":"after","date":"2026-01-31"}},
              {"type":"provider","operator":"is-not","value":"Plex"},
              {"type":"type","operator":"is","value":"Audiobook"},
              {"type":"favourite","isFavourite":true}
            ],"sort":"random","descending":false,"limit":{"type":"songs","count":50}}}
            """.trimIndent()

        SmartRulesCodec.decode(json) shouldBe SmartRules(
            match = SmartRules.Match.All,
            rules = listOf(
                Rule.Text(TextField.Genre, TextOperator.Contains, "rock"),
                Rule.Number(NumberField.PlayCount, NumberCondition.Between(1, 5)),
                Rule.Date(DateField.LastPlayed, DateCondition.NotInTheLast(180)),
                Rule.Date(DateField.DateAdded, DateCondition.After(LocalDate(2026, 1, 31))),
                Rule.Provider(EnumOperator.IsNot, MediaProviderType.Plex),
                Rule.Type(EnumOperator.Is, Song.Type.Audiobook),
                Rule.Favourite(true),
            ),
            sort = SmartSort.Random,
            limit = Limit.Songs(50),
        )
    }

    @Test
    fun `encodes the version and every field`() {
        SmartRulesCodec.encode(SmartRules(rules = listOf(Rule.Favourite()))) shouldBe
            """{"version":1,"rules":{"match":"all","rules":[{"type":"favourite","isFavourite":true}],"sort":"default","descending":false,"limit":null}}"""
    }

    @Test
    fun `missing fields take their defaults and unknown ones are ignored`() {
        SmartRulesCodec.decode("""{"version":1,"rules":{"shuffleEveryTime":true}}""") shouldBe SmartRules()
    }

    @Test
    fun `a rule this version doesn't know fails to decode`() {
        shouldThrow<SerializationException> {
            SmartRulesCodec.decode("""{"version":2,"rules":{"rules":[{"type":"rating","stars":5}]}}""")
        }
    }
}
