package com.rohittp.plugables.protoextended

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MetadataRendererTest {

    private fun aspectRatio(
        metaProperties: List<MetaProperty> = listOf(
            MetaProperty(
                name = "width",
                type = KotlinScalar.INT,
                values = listOf(
                    ConstantValue("RATIO_1_1", "1"),
                    ConstantValue("RATIO_16_9", "16"),
                ),
            ),
        ),
    ) = ProtoEnumInfo(
        qualifiedName = "ta.AspectRatio",
        kotlinRef = "AspectRatio",
        kotlinImport = "com.travelanimator.routemap.AspectRatio",
        constantNames = listOf("RATIO_1_1", "RATIO_16_9"),
        metaProperties = metaProperties,
        resourceFlags = ResourceFlags(),
    )

    @Test
    fun `emits generated banner and package`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(out, "// GENERATED — do not edit. Source: proto enum definitions.")
        assertContains(out, "package com.example.generated")
    }

    @Test
    fun `emits an extension property per meta field`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(out, "val AspectRatio.width: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> 1")
        assertContains(out, "        AspectRatio.RATIO_16_9 -> 16")
    }

    @Test
    fun `imports the enum only when it contributes a property`() {
        val withProps = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(withProps, "import com.travelanimator.routemap.AspectRatio")

        val withoutProps = MetadataRenderer.render(
            "com.example.generated",
            listOf(aspectRatio(metaProperties = emptyList())),
        )
        assertFalse(withoutProps.contains("import com.travelanimator.routemap.AspectRatio"))
    }

    @Test
    fun `contains no android imports`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertFalse(out.contains("androidx"))
        // NOTE: checks "R." (Android resource-class dot-access, e.g. "R.drawable") rather
        // than ".R" as literally given in the brief — ".R" false-positives against this very
        // fixture's own "AspectRatio.RATIO_1_1" (the "o.R" in "Ratio.RATIO" trips it). See
        // task-1-report.md for details.
        assertFalse(out.contains("R."))
    }

    @Test
    fun `renders each scalar type as a valid kotlin literal`() {
        val enum = ProtoEnumInfo(
            qualifiedName = "ta.Sample",
            kotlinRef = "Sample",
            kotlinImport = "com.example.Sample",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty("s", KotlinScalar.STRING, listOf(ConstantValue("ONE", "km"))),
                MetaProperty("d", KotlinScalar.DOUBLE, listOf(ConstantValue("ONE", "0.621371"))),
                MetaProperty("f", KotlinScalar.FLOAT, listOf(ConstantValue("ONE", "1.5"))),
                MetaProperty("i", KotlinScalar.INT, listOf(ConstantValue("ONE", "1080"))),
                MetaProperty("l", KotlinScalar.LONG, listOf(ConstantValue("ONE", "9"))),
                MetaProperty("b", KotlinScalar.BOOLEAN, listOf(ConstantValue("ONE", "true"))),
            ),
            resourceFlags = ResourceFlags(),
        )
        val out = MetadataRenderer.render("com.example.generated", listOf(enum))
        assertContains(out, "Sample.ONE -> \"km\"")
        assertContains(out, "Sample.ONE -> 0.621371")
        assertContains(out, "Sample.ONE -> 1.5f")
        assertContains(out, "Sample.ONE -> 1080")
        assertContains(out, "Sample.ONE -> 9L")
        assertContains(out, "Sample.ONE -> true")
    }

    @Test
    fun `escapes strings that would break kotlin source`() {
        val enum = ProtoEnumInfo(
            qualifiedName = "ta.Sample",
            kotlinRef = "Sample",
            kotlinImport = "com.example.Sample",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty(
                    "s",
                    KotlinScalar.STRING,
                    listOf(ConstantValue("ONE", "a\"b\\c\nd\$e")),
                ),
            ),
            resourceFlags = ResourceFlags(),
        )
        val out = MetadataRenderer.render("com.example.generated", listOf(enum))
        assertContains(out, """Sample.ONE -> "a\"b\\c\nd\${'$'}e"""")
    }

    @Test
    fun `sorts enums by qualified name regardless of input order`() {
        val a = aspectRatio()
        val z = ProtoEnumInfo(
            qualifiedName = "ta.Zebra",
            kotlinRef = "Zebra",
            kotlinImport = "com.example.Zebra",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty("n", KotlinScalar.INT, listOf(ConstantValue("ONE", "1"))),
            ),
            resourceFlags = ResourceFlags(),
        )
        assertEquals(
            MetadataRenderer.render("p", listOf(a, z)),
            MetadataRenderer.render("p", listOf(z, a)),
        )
    }

    @Test
    fun `emits header-only file when nothing to generate`() {
        val out = MetadataRenderer.render("com.example.generated", emptyList())
        assertContains(out, "package com.example.generated")
        assertFalse(out.contains("import "))
        assertFalse(out.contains("val "))
    }

    @Test
    fun `throws rather than defaulting when a value cannot be parsed`() {
        val enum = ProtoEnumInfo(
            qualifiedName = "ta.Sample",
            kotlinRef = "Sample",
            kotlinImport = "com.example.Sample",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty("i", KotlinScalar.INT, listOf(ConstantValue("ONE", "not-a-number"))),
            ),
            resourceFlags = ResourceFlags(),
        )
        val error = assertFailsWith<ProtoSchemaException> {
            MetadataRenderer.render("com.example.generated", listOf(enum))
        }
        assertContains(error.message!!, "not-a-number")
        assertContains(error.message!!, "Int")
    }

    private fun intEnum(rawValue: String) = ProtoEnumInfo(
        qualifiedName = "ta.Sample",
        kotlinRef = "Sample",
        kotlinImport = "com.example.Sample",
        constantNames = listOf("ONE"),
        metaProperties = listOf(
            MetaProperty("i", KotlinScalar.INT, listOf(ConstantValue("ONE", rawValue))),
        ),
        resourceFlags = ResourceFlags(),
    )

    @Test
    fun `decimal-looking whole number still renders as an Int`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(intEnum("1080.0")))
        assertContains(out, "Sample.ONE -> 1080")
    }

    @Test
    fun `fractional Int value throws rather than truncating`() {
        val error = assertFailsWith<ProtoSchemaException> {
            MetadataRenderer.render("com.example.generated", listOf(intEnum("1.5")))
        }
        assertContains(error.message!!, "1.5")
        assertContains(error.message!!, "Int")
    }

    @Test
    fun `out-of-range Int value throws rather than saturating`() {
        val error = assertFailsWith<ProtoSchemaException> {
            MetadataRenderer.render("com.example.generated", listOf(intEnum("4294967295")))
        }
        assertContains(error.message!!, "4294967295")
        assertContains(error.message!!, "Int")
    }
}
