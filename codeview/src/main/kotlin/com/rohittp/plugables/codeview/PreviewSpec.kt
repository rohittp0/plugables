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
)

data class RenderedPreview(
    val spec: PreviewSpec,
    val imagePath: String?,
    val nodes: List<NodeInfo>,
)
