package com.rohittp.plugables.typedevents

import kotlin.test.Test
import kotlin.test.assertContains

class ClassRendererTest {

    @Test
    fun `renders abstract class with logEvent`() {
        val output = ClassRenderer.render("events.yaml", emptyList())
        assertContains(output, "abstract class AnalyticsBase")
        assertContains(output, "protected open fun logEvent(eventName: String, params: Map<String, Any?>) = Unit")
        assertContains(output, "companion object {")
        assertContains(output, "var instance: AnalyticsBase? = null")
        assertContains(output, "package com.rohittp.plugables.analytics")
        assertContains(output, "GENERATED FILE")
    }

    @Test
    fun `renders method for shorthand event`() {
        val events = listOf(
            EventSpec("screen_viewed", "User viewed a screen", "logScreenViewed", emptyMap())
        )
        val output = ClassRenderer.render("events.yaml", events)
        assertContains(output, "fun logScreenViewed()")
        assertContains(output, "instance?.logEvent(\"screen_viewed\", emptyMap())")
        assertContains(output, "* User viewed a screen")
    }

    @Test
    fun `renders method with params`() {
        val events = listOf(
            EventSpec(
                "purchase_completed",
                "User completed a purchase",
                "logPurchaseCompleted",
                mapOf(
                    "item_id" to ParamSpec("String", "The purchased item ID"),
                    "price" to ParamSpec("Double", "Final price paid")
                )
            )
        )
        val output = ClassRenderer.render("events.yaml", events)
        assertContains(output, "fun logPurchaseCompleted(itemId: String, price: Double)")
        assertContains(output, "instance?.logEvent(\"purchase_completed\", mapOf(\"item_id\" to itemId, \"price\" to price))")
        assertContains(output, "@param itemId The purchased item ID")
        assertContains(output, "@param price Final price paid")
    }

    @Test
    fun `source file name appears in header`() {
        val output = ClassRenderer.render("my_events.yaml", emptyList())
        assertContains(output, "Source: my_events.yaml")
    }
}
