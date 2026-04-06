package com.rohittp.plugables.typedevents

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlParserTest {

    private fun parse(yaml: String): List<EventSpec> {
        val tmp = File.createTempFile("events", ".yaml").apply {
            writeText(yaml)
            deleteOnExit()
        }
        return YamlParser.parse(tmp)
    }

    @Test
    fun `shorthand event parses correctly`() {
        val events = parse("- screen_viewed: User viewed a screen")
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("screen_viewed", e.eventName)
        assertEquals("User viewed a screen", e.info)
        assertEquals("logScreenViewed", e.function)
        assertTrue(e.params.isEmpty())
    }

    @Test
    fun `full form event parses correctly`() {
        val events = parse("""
            - purchase_completed:
                info: User completed a purchase
                params:
                  item_id:
                    type: String
                    info: The purchased item ID
                  price:
                    type: Double
                    info: Final price paid
        """.trimIndent())
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("purchase_completed", e.eventName)
        assertEquals("User completed a purchase", e.info)
        assertEquals("logPurchaseCompleted", e.function)
        assertEquals(2, e.params.size)
        assertEquals(ParamSpec("String", "The purchased item ID"), e.params["item_id"])
        assertEquals(ParamSpec("Double", "Final price paid"), e.params["price"])
    }

    @Test
    fun `explicit function override is respected`() {
        val events = parse("""
            - my_event:
                info: Some event
                function: trackMyEvent
        """.trimIndent())
        assertEquals("trackMyEvent", events[0].function)
    }

    @Test
    fun `deriveFunction produces log prefix`() {
        assertEquals("logPurchaseCompleted", YamlParser.deriveFunction("purchase_completed"))
        assertEquals("logScreenViewed", YamlParser.deriveFunction("screen_viewed"))
        assertEquals("logFoo", YamlParser.deriveFunction("foo"))
    }

    @Test
    fun `toParamName converts snake_case to camelCase`() {
        assertEquals("itemId", YamlParser.toParamName("item_id"))
        assertEquals("price", YamlParser.toParamName("price"))
        assertEquals("screenName", YamlParser.toParamName("screen_name"))
    }
}
