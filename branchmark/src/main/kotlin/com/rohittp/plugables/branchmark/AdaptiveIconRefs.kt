package com.rohittp.plugables.branchmark

/** A resource reference like `@drawable/ic_launcher_foreground` split into its type and name. */
data class ResourceRef(val type: String, val name: String) {
    companion object {
        /** Parse `@drawable/foo` / `@mipmap/foo` (optionally `@android:.../...`) into a [ResourceRef], or null. */
        fun parse(raw: String?): ResourceRef? {
            if (raw == null) return null
            val body = raw.trim().removePrefix("@")
            if (body.isEmpty()) return null
            val slash = body.indexOf('/')
            if (slash < 0) return null
            val type = body.substring(0, slash).substringAfter(':') // drop any package, e.g. android:
            val name = body.substring(slash + 1)
            if (type.isEmpty() || name.isEmpty()) return null
            return ResourceRef(type, name)
        }
    }
}

/**
 * The drawable references pulled out of an `<adaptive-icon>` definition. Background and monochrome
 * are preserved verbatim when branchmark regenerates the debug adaptive XML; only the foreground is
 * rewritten to point at the stamped image.
 */
data class AdaptiveIconRefs(
    val foreground: ResourceRef,
    val background: ResourceRef?,
    val monochrome: ResourceRef?,
)
