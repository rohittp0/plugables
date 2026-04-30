package com.rohittp.plugables.codeview

data class SourceLocation(
    val file: String?,
    val line: Int,
)

data class PreviewSpec(
    val id: String,
    val previewFqn: String,
    val displayName: String,
    val source: SourceLocation,
    /**
     * SHA-256 (hex) of the entire source file the preview is declared in. Used to short-circuit
     * re-rendering when the source hasn't changed since the previous run — see
     * [GeneratePreviewTestsTask] and the `sourceHash` field in sidecar JSON v2+.
     */
    val sourceHash: String,
)

data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class NodeInfo(
    val id: Int,
    val name: String,
    val bounds: Bounds,
    val source: SourceLocation,
    val parentId: Int?,
    /** Single line of source at [source.line], trimmed. Null if the file isn't in sourceDirs. */
    val codeSnippet: String?,
)

data class RenderedPreview(
    val spec: PreviewSpec,
    val imagePath: String?,
    val nodes: List<NodeInfo>,
    /** Plain text strings actually shown on screen, sourced from the Compose semantics tree. */
    val renderedTexts: List<String>,
    /** Non-null when `renderOne` threw at render time inside the batch. The card renders broken. */
    val renderError: String? = null,
)
