package com.rohittp.plugables.viewmodelstub

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KotlinSourceParserTest {

    private fun tempKt(content: String): File =
        File.createTempFile("Test", ".kt").also { it.writeText(content); it.deleteOnExit() }

    @Test
    fun `returns null for file without annotation`() {
        val file = tempKt("""
            package com.example
            class FooImpl
        """.trimIndent())
        assertNull(KotlinSourceParser.parseFile(file))
    }

    @Test
    fun `returns null when annotation only appears in a comment`() {
        val file = tempKt("""
            package com.example
            // @ViewModelStub is used here in docs
            class FooImpl
        """.trimIndent())
        assertNull(KotlinSourceParser.parseFile(file))
    }

    @Test
    fun `throws when annotated class does not end with Impl`() {
        val file = tempKt("""
            package com.example
            @ViewModelStub
            class FooViewModel
        """.trimIndent())
        assertFailsWith<IllegalStateException> { KotlinSourceParser.parseFile(file) }
    }

    @Test
    fun `parses interface and stub names from Impl class`() {
        val file = tempKt("""
            package com.example
            @ViewModelStub
            class FooImpl {
            }
        """.trimIndent())
        val info = assertNotNull(KotlinSourceParser.parseFile(file))
        assertEquals("com.example", info.packageName)
        assertEquals("FooImpl", info.implClassName)
        assertEquals("Foo", info.interfaceName)
        assertEquals("FooStub", info.stubClassName)
    }

    @Test
    fun `extracts val and var properties with explicit types`() {
        val file = tempKt("""
            package com.example
            import kotlinx.coroutines.flow.StateFlow
            @ViewModelStub
            class FooImpl {
                override val count: StateFlow<Int> = MutableStateFlow(0)
                override var name: String = ""
            }
        """.trimIndent())
        val info = assertNotNull(KotlinSourceParser.parseFile(file))
        assertEquals(2, info.properties.size)
        val count = info.properties[0]
        assertEquals("count", count.name)
        assertEquals(false, count.isMutable)
        assertEquals("StateFlow<Int>", count.type)
        val name = info.properties[1]
        assertEquals("name", name.name)
        assertEquals(true, name.isMutable)
    }

    @Test
    fun `extracts public methods including suspend`() {
        val file = tempKt("""
            package com.example
            @ViewModelStub
            class FooImpl {
                override suspend fun load(id: String) {}
                override fun reset() {}
                private fun secret() {}
            }
        """.trimIndent())
        val info = assertNotNull(KotlinSourceParser.parseFile(file))
        assertEquals(2, info.methods.size)
        val load = info.methods[0]
        assertEquals("load", load.name)
        assertEquals(true, load.isSuspend)
        assertEquals(1, load.parameters.size)
        assertEquals("id", load.parameters[0].name)
        assertEquals("String", load.parameters[0].type)
        val reset = info.methods[1]
        assertEquals("reset", reset.name)
        assertEquals(false, reset.isSuspend)
        assertEquals(0, reset.parameters.size)
    }

    @Test
    fun `extracts imports from source file`() {
        val file = tempKt("""
            package com.example
            import kotlinx.coroutines.flow.StateFlow
            import androidx.lifecycle.ViewModel
            @ViewModelStub
            class FooImpl {
            }
        """.trimIndent())
        val info = assertNotNull(KotlinSourceParser.parseFile(file))
        assert(info.imports.any { it.contains("StateFlow") })
        assert(info.imports.any { it.contains("ViewModel") })
    }
}
