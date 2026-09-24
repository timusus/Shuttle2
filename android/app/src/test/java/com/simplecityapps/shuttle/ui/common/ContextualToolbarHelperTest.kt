package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test

private data class Item(
    val id: Long,
    val sortOrder: Long
)

/**
 * Verifies selection survives a rebind where [Item.sortOrder] (or any field besides [Item.id])
 * changes, which happens whenever the backing list is re-sorted or reordered while items are
 * selected. Without a [keySelector], equals()-based [Set] membership treats the rebound instance
 * as a different item and drops or duplicates the selection.
 */
class ContextualToolbarHelperTest {
    private fun helper() = ContextualToolbarHelper<Item>(keySelector = { it.id }).apply {
        toolbar = mockk<Toolbar>(relaxed = true)
        contextualToolbar = mockk<Toolbar>(relaxed = true)
    }

    @Test
    fun `selecting an item via long click activates selection`() {
        val helper = helper()
        val item = Item(id = 1, sortOrder = 0)

        helper.handleLongClick(item)

        helper.isActive shouldBe true
        helper.selectedItems shouldBe listOf(item)
    }

    @Test
    fun `clicking a rebound instance with a different sortOrder still deselects it`() {
        val helper = helper()
        val original = Item(id = 1, sortOrder = 0)
        helper.handleLongClick(original)

        val rebound = Item(id = 1, sortOrder = 5)
        helper.handleClick(rebound)

        helper.selectedItems shouldBe emptyList()
        helper.isActive shouldBe false
    }

    @Test
    fun `clicking a rebound instance with a different sortOrder does not duplicate the selection`() {
        val helper = helper()
        val a = Item(id = 1, sortOrder = 0)
        val b = Item(id = 2, sortOrder = 0)
        helper.handleLongClick(a)
        helper.handleClick(b)

        val reboundA = Item(id = 1, sortOrder = 9)
        helper.handleClick(reboundA)
        helper.handleClick(reboundA)

        helper.selectedItems shouldBe listOf(b, reboundA)
    }

    @Test
    fun `hide clears selection and deactivates`() {
        val helper = helper()
        helper.handleLongClick(Item(id = 1, sortOrder = 0))

        helper.hide()

        helper.isActive shouldBe false
        helper.selectedItems shouldBe emptyList()
    }
}
