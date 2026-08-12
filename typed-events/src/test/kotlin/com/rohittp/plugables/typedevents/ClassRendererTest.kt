package com.rohittp.plugables.typedevents

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ClassRendererTest {

    // ── renderHandlerFile ─────────────────────────────────────────────────────

    @Test
    fun `renderHandlerFile contains package and generated header`() {
        val output = ClassRenderer.renderHandlerFile()
        assertContains(output, "package com.rohittp.plugables.analytics")
        assertContains(output, "GENERATED FILE")
    }

    @Test
    fun `renderHandlerFile contains common sink boundary`() {
        val output = ClassRenderer.renderHandlerFile()
        assertContains(output, "fun interface AnalyticsSink")
        assertContains(output, "fun capture(eventName: String, properties: Map<String, Any?>)")
        assertContains(output, "internal var typedEventSink: AnalyticsSink? = null")
        assertContains(output, "fun registerAnalyticsSink(sink: AnalyticsSink)")
    }

    @Test
    fun `renderHandlerFile contains registerTypedEventHandler taking a function`() {
        val output = ClassRenderer.renderHandlerFile()
        assertContains(output, "fun registerTypedEventHandler(handler: (eventName: String, params: Map<String, Any?>) -> Unit)")
        assertContains(output, "registerAnalyticsSink(AnalyticsSink(handler))")
    }

    @Test
    fun `renderHandlerFile does not contain annotation class or reflection`() {
        val output = ClassRenderer.renderHandlerFile()
        assertFalse(output.contains("annotation class"))
        assertFalse(output.contains("@Target"))
        assertFalse(output.contains(".declaredMethods"))
        assertFalse(output.contains("AnalyticsBase"))
        assertFalse(output.contains("var instance"))
    }

    // ── renderEventsFile ──────────────────────────────────────────────────────

    @Test
    fun `renderEventsFile contains package and generated header`() {
        val output = ClassRenderer.renderEventsFile("events.yaml", emptyList())
        assertContains(output, "package com.rohittp.plugables.analytics")
        assertContains(output, "GENERATED FILE")
        assertContains(output, "Source: events.yaml")
    }

    @Test
    fun `renderEventsFile source file name appears in header`() {
        val output = ClassRenderer.renderEventsFile("my_events.yaml", emptyList())
        assertContains(output, "Source: my_events.yaml")
    }

    @Test
    fun `renderEventsFile does not contain AnalyticsBase or companion object`() {
        val output = ClassRenderer.renderEventsFile("events.yaml", emptyList())
        assertFalse(output.contains("abstract class AnalyticsBase"))
        assertFalse(output.contains("companion object"))
        assertFalse(output.contains("var instance"))
    }

    @Test
    fun `renderHandlerFile requires a registered sink before invoking`() {
        val events = listOf(
            EventSpec("screen_viewed", "User viewed a screen", "logScreenViewed", emptyMap())
        )
        val output = ClassRenderer.renderHandlerFile()
        assertContains(output, "checkNotNull(typedEventSink)")
        assertContains(output, "registerAnalyticsSink() must be called before logging events")
    }

    @Test
    fun `renderEventsFile renders top-level function for shorthand event`() {
        val events = listOf(
            EventSpec("screen_viewed", "User viewed a screen", "logScreenViewed", emptyMap())
        )
        val output = ClassRenderer.renderEventsFile("events.yaml", events)
        assertContains(output, "fun logScreenViewed()")
        assertContains(output, "emitTypedEvent(\"screen_viewed\", emptyMap())")
        assertContains(output, "* User viewed a screen")
    }

    @Test
    fun `renderEventsFile renders top-level function with params`() {
        val events = listOf(
            EventSpec(
                "purchase_completed",
                "User completed a purchase",
                "logPurchaseCompleted",
                mapOf(
                    "item_id" to ParamSpec("String", "The purchased item ID"),
                    "price"   to ParamSpec("Double", "Final price paid")
                )
            )
        )
        val output = ClassRenderer.renderEventsFile("events.yaml", events)
        assertContains(output, "fun logPurchaseCompleted(itemId: String, price: Double)")
        assertContains(output, "emitTypedEvent(\"purchase_completed\", mapOf(\"item_id\" to itemId, \"price\" to price))")
        assertContains(output, "@param itemId The purchased item ID")
        assertContains(output, "@param price Final price paid")
    }

    @Test
    fun `renderEventsFile does not use instance call in function body`() {
        val events = listOf(
            EventSpec("screen_viewed", "User viewed a screen", "logScreenViewed", emptyMap())
        )
        val output = ClassRenderer.renderEventsFile("events.yaml", events)
        assertFalse(output.contains("instance?.logEvent"))
    }

    @Test
    fun `renderFacadeFile delegates through fully-qualified generated functions`() {
        val events = listOf(
            EventSpec(
                "purchase_completed",
                "User completed a purchase",
                "logPurchaseCompleted",
                mapOf("price" to ParamSpec("Double", "Final price paid")),
            ),
        )
        val output = ClassRenderer.renderFacadeFile("events.yaml", events)

        assertContains(output, "object AnalyticsEvents")
        assertContains(output, "fun logPurchaseCompleted(price: Double)")
        assertContains(
            output,
            "com.rohittp.plugables.analytics.logPurchaseCompleted(price)",
        )
    }

    @Test
    fun `renderSchemaFile pins ordered event and property names`() {
        val events = listOf(
            EventSpec(
                "purchase_completed",
                "User completed a purchase",
                "logPurchaseCompleted",
                linkedMapOf(
                    "item_id" to ParamSpec("String", "The purchased item ID"),
                    "price" to ParamSpec("Double", "Final price paid"),
                ),
            ),
        )
        val output = ClassRenderer.renderSchemaFile("events.yaml", events)

        assertContains(output, "\"purchase_completed\",")
        assertContains(
            output,
            "\"purchase_completed\" to listOf(\"item_id\", \"price\")",
        )
    }
}
