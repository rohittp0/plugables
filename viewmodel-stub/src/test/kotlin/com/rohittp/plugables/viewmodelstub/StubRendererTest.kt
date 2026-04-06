package com.rohittp.plugables.viewmodelstub

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class StubRendererTest {

    private fun sampleInfo() = ViewModelInfo(
        packageName = "com.example",
        implClassName = "VideoViewModelImpl",
        interfaceName = "VideoViewModel",
        stubClassName = "VideoViewModelStub",
        imports = listOf("import kotlinx.coroutines.flow.StateFlow"),
        properties = listOf(
            PropertyInfo("isLoading", false, "StateFlow<Boolean>"),
            PropertyInfo("title", true, "String"),
            PropertyInfo("error", false, "String?"),
        ),
        methods = listOf(
            MethodInfo("load", listOf(ParameterInfo("id", "String")), null, false),
            MethodInfo("getCount", emptyList(), "Int", false),
            MethodInfo("fetchData", listOf(ParameterInfo("query", "String?")), null, true),
        ),
    )

    @Test
    fun `renderInterface contains package and interface declaration`() {
        val result = StubRenderer.renderInterface(sampleInfo())
        assertContains(result, "package com.example")
        assertContains(result, "interface VideoViewModel {")
    }

    @Test
    fun `renderInterface contains val and var properties`() {
        val result = StubRenderer.renderInterface(sampleInfo())
        assertContains(result, "val isLoading: StateFlow<Boolean>")
        assertContains(result, "var title: String")
        assertContains(result, "val error: String?")
    }

    @Test
    fun `renderInterface adds nullable default to nullable method params`() {
        val result = StubRenderer.renderInterface(sampleInfo())
        assertContains(result, "query: String? = null")
    }

    @Test
    fun `renderInterface includes suspend modifier`() {
        val result = StubRenderer.renderInterface(sampleInfo())
        assertContains(result, "suspend fun fetchData(")
    }

    @Test
    fun `renderInterface includes return type when present`() {
        val result = StubRenderer.renderInterface(sampleInfo())
        assertContains(result, "): Int")
    }

    @Test
    fun `renderStub contains package and class declaration`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "package com.example")
        assertContains(result, "class VideoViewModelStub(")
        assertContains(result, ") : VideoViewModel {")
    }

    @Test
    fun `renderStub gives StateFlow property a MutableStateFlow default`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "MutableStateFlow(false)")
    }

    @Test
    fun `renderStub gives nullable property a null default`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "override val error: String? = null")
    }

    @Test
    fun `renderStub gives String property an empty string default`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "override var title: String = \"\"")
    }

    @Test
    fun `renderStub no-op method bodies for Unit-returning methods`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "override fun load(id: String) {}")
    }

    @Test
    fun `renderStub provides default return value for non-Unit method`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "override fun getCount(): Int = 0")
    }

    @Test
    fun `renderStub adds required coroutine imports`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertContains(result, "import kotlinx.coroutines.flow.MutableStateFlow")
    }

    @Test
    fun `renderStub does not contain generated comment referencing old task name`() {
        val result = StubRenderer.renderStub(sampleInfo())
        assertFalse(result.contains("GeneratePreviewStubsTask"))
    }
}
