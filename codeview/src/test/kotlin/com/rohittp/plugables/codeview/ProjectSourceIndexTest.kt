package com.rohittp.plugables.codeview

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectSourceIndexTest {

    private fun makeFile(root: File, relPath: String, packageName: String) {
        val f = File(root, relPath)
        f.parentFile.mkdirs()
        f.writeText("package $packageName\n")
    }

    @Test
    fun `resolves unique filename`(@org.junit.jupiter.api.io.TempDir root: File) {
        makeFile(root, "src/main/kotlin/com/example/app/Home.kt", "com.example.app")
        val index = ProjectSourceIndex.build(listOf(root))
        val resolved = index.resolve("Home.kt", "com.example.app".hashCode())
        assertEquals(File(root, "src/main/kotlin/com/example/app/Home.kt").absolutePath, resolved)
    }

    @Test
    fun `disambiguates by packageHash`(@org.junit.jupiter.api.io.TempDir root: File) {
        makeFile(root, "src/main/kotlin/com/a/Home.kt", "com.a")
        makeFile(root, "src/main/kotlin/com/b/Home.kt", "com.b")
        val index = ProjectSourceIndex.build(listOf(root))
        assertEquals(
            File(root, "src/main/kotlin/com/a/Home.kt").absolutePath,
            index.resolve("Home.kt", "com.a".hashCode()),
        )
        assertEquals(
            File(root, "src/main/kotlin/com/b/Home.kt").absolutePath,
            index.resolve("Home.kt", "com.b".hashCode()),
        )
    }

    @Test
    fun `returns null when filename absent`(@org.junit.jupiter.api.io.TempDir root: File) {
        val index = ProjectSourceIndex.build(listOf(root))
        assertNull(index.resolve("Missing.kt", 0))
    }

    @Test
    fun `returns null when hash mismatches with multiple candidates`(@org.junit.jupiter.api.io.TempDir root: File) {
        makeFile(root, "src/main/kotlin/com/a/Home.kt", "com.a")
        makeFile(root, "src/main/kotlin/com/c/Home.kt", "com.c")
        val index = ProjectSourceIndex.build(listOf(root))
        assertNull(index.resolve("Home.kt", "com.b".hashCode()))
    }
}
