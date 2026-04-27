package com.rohittp.plugables.codeview

import java.io.File

class ProjectSourceIndex private constructor(
    private val byName: Map<String, List<Entry>>,
) {
    private data class Entry(val absolutePath: String, val packageHash: Int)

    fun resolve(fileName: String, packageHash: Int): String? {
        val candidates = byName[fileName] ?: return null
        candidates.firstOrNull { it.packageHash == packageHash }?.let { return it.absolutePath }
        return if (candidates.size == 1) candidates[0].absolutePath else null
    }

    companion object {
        private val packagePattern = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

        fun build(roots: Collection<File>): ProjectSourceIndex {
            val byName = mutableMapOf<String, MutableList<Entry>>()
            for (root in roots) {
                if (!root.exists()) continue
                root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                    val pkg = packagePattern.find(file.readText())?.groupValues?.get(1) ?: ""
                    byName
                        .getOrPut(file.name) { mutableListOf() }
                        .add(Entry(file.absolutePath, pkg.hashCode()))
                }
            }
            return ProjectSourceIndex(byName)
        }
    }
}
