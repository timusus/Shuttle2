package com.simplecityapps.trial

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedProductsQueryTest {
    private val types = setOf("inapp", "subs")

    @Test
    fun `waits for every product type`() {
        val pass = OwnedProductsQuery(types)
        assertEquals(OwnedProductsQuery.Outcome.Pending, pass.onResult("inapp", setOf(ProductIds.LEGACY_LIFETIME)))
    }

    @Test
    fun `both types succeeding yields the union`() {
        val pass = OwnedProductsQuery(types)
        pass.onResult("inapp", setOf(ProductIds.LEGACY_LIFETIME))
        assertEquals(
            OwnedProductsQuery.Outcome.Complete(setOf(ProductIds.LEGACY_LIFETIME, ProductIds.PRO_SUBSCRIPTION)),
            pass.onResult("subs", setOf(ProductIds.PRO_SUBSCRIPTION))
        )
    }

    @Test
    fun `a failed type completes the pass as a failure instead of blocking it`() {
        val pass = OwnedProductsQuery(types)
        pass.onResult("inapp", setOf(ProductIds.LEGACY_LIFETIME))
        assertEquals(OwnedProductsQuery.Outcome.Failed(setOf("subs")), pass.onResult("subs", null))
    }

    @Test
    fun `a failure reported first still fails the pass`() {
        val pass = OwnedProductsQuery(types)
        assertEquals(OwnedProductsQuery.Outcome.Pending, pass.onResult("subs", null))
        assertEquals(OwnedProductsQuery.Outcome.Failed(setOf("subs")), pass.onResult("inapp", emptySet()))
    }

    @Test
    fun `a later pass where both types succeed completes normally`() {
        OwnedProductsQuery(types).apply {
            onResult("inapp", null)
            onResult("subs", null)
        }
        val pass = OwnedProductsQuery(types)
        pass.onResult("inapp", emptySet())
        assertEquals(OwnedProductsQuery.Outcome.Complete(emptySet()), pass.onResult("subs", emptySet()))
    }
}
