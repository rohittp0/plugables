package com.rohittp.plugables.codeview

import java.net.URLEncoder

object IdeUrlBuilder {
    fun build(scheme: String, absoluteFile: String, line: Int): String =
        when (scheme) {
            "vscode" -> "vscode://file$absoluteFile:$line"
            else -> "idea://open?file=${encodePath(absoluteFile)}&line=$line"
        }

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) seg
            else URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
}
