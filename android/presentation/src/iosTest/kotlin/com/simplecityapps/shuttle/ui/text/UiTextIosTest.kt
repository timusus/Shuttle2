package com.simplecityapps.shuttle.ui.text

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class UiTextIosTest {
    @Test
    fun aKeyMissingFromTheTableResolvesToItself() {
        StringKey.MEDIA_ACTION_UNDO.text.resolve() shouldBe "media_action_undo"
    }

    @Test
    fun argumentsAreFormattedPositionally() {
        formatLocalized("Couldn't play: %1\$@", listOf("File not found")) shouldBe "Couldn't play: File not found"
    }

    @Test
    fun aPluralTakesItsCountFirst() {
        formatPlural("%1\$d songs added to %2\$@", 3, listOf("Favorites")) shouldBe "3 songs added to Favorites"
    }
}
