package com.rohittp.plugables.protoextended

import org.gradle.api.GradleException
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ComposeResourceValidatorTest {

    private val ratio = ProtoEnumInfo(
        qualifiedName = "ta.AspectRatio",
        kotlinRef = "AspectRatio",
        kotlinImport = "com.example.AspectRatio",
        constantNames = listOf("RATIO_1_1"),
        metaProperties = emptyList(),
        resourceFlags = ResourceFlags(displayName = true, icon = true),
    )

    @Test
    fun `accepts matching base string and drawable`(@TempDir tmp: File) {
        writeBaseResources(tmp)

        ComposeResourceValidator.validate(tmp, listOf(ratio))
    }

    @Test
    fun `reports every missing resource in one failure`(@TempDir tmp: File) {
        val failure = assertFailsWith<GradleException> {
            ComposeResourceValidator.validate(tmp, listOf(ratio))
        }

        assertContains(failure.message.orEmpty(), "ta.AspectRatio.RATIO_1_1")
        assertContains(failure.message.orEmpty(), "string `ratio_1_1`")
        assertContains(failure.message.orEmpty(), "drawable `ratio_1_1`")
    }

    private fun writeBaseResources(root: File) {
        root.resolve("values").mkdirs()
        root.resolve("values/strings.xml").writeText(
            """<resources><string name="ratio_1_1">Square</string></resources>""",
        )
        root.resolve("drawable").mkdirs()
        root.resolve("drawable/ratio_1_1.xml").writeText("<vector />")
    }
}
