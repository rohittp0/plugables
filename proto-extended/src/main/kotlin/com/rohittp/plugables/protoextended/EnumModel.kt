package com.rohittp.plugables.protoextended

/** The Kotlin type a proto scalar maps to. */
enum class KotlinScalar(val kotlinName: String) {
    STRING("String"),
    DOUBLE("Double"),
    FLOAT("Float"),
    INT("Int"),
    LONG("Long"),
    BOOLEAN("Boolean"),
}

/**
 * One enum constant's value for a metadata property, as the raw string wire-schema
 * reports it. Converting to a Kotlin literal is the renderer's job, not the reader's.
 */
data class ConstantValue(val constantName: String, val rawValue: String)

/** A generated metadata extension property, e.g. `val AspectRatio.width: Int`. */
data class MetaProperty(
    val name: String,
    val type: KotlinScalar,
    /** One entry per enum constant, in declaration order. */
    val values: List<ConstantValue>,
)

/** Which Android resource accessors an enum asked for via `(gen.resources)`. */
data class ResourceFlags(
    val displayName: Boolean = false,
    val icon: Boolean = false,
) {
    val any: Boolean get() = displayName || icon
}

/**
 * A proto enum flattened into everything the renderers need.
 *
 * [kotlinRef] is how generated code refers to the enum: `AspectRatio` for a top-level
 * enum, `Distance.Unit` for a nested one. [kotlinImport] is the outermost class to
 * import so that reference resolves — for a nested enum that is the *enclosing message*
 * (`…Distance`), never the nested enum itself, which would shadow `kotlin.Unit`.
 */
data class ProtoEnumInfo(
    /** Fully-qualified proto name. Sort key only; never emitted. */
    val qualifiedName: String,
    val kotlinRef: String,
    val kotlinImport: String,
    val constantNames: List<String>,
    val metaProperties: List<MetaProperty>,
    val resourceFlags: ResourceFlags,
)
