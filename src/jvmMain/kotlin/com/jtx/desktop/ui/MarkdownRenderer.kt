package com.jtx.desktop.ui

object MarkdownRenderer {

    fun renderMarkdown(markdown: String): String {
        val lines = markdown.split("\n")
        val result = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("### ") -> result.append(line.removePrefix("### ")).append("\n")
                line.startsWith("## ") -> result.append(line.removePrefix("## ")).append("\n")
                line.startsWith("# ") -> result.append(line.removePrefix("# ")).append("\n")
                line.startsWith("- ") || line.startsWith("* ") -> result.append("  • ${line.removePrefix("- ").removePrefix("* ")}").append("\n")
                line.startsWith("> ") -> result.append("  > ${line.removePrefix("> ")}").append("\n")
                line.startsWith("```") -> { }
                else -> {
                    var processed = line
                    while (processed.contains("**")) {
                        val start = processed.indexOf("**")
                        val end = processed.indexOf("**", start + 2)
                        if (end > start) {
                            result.append(processed.substring(0, start))
                            result.append(processed.substring(start + 2, end))
                            processed = processed.substring(end + 2)
                        } else break
                    }
                    result.append(processed).append("\n")
                }
            }
        }
        return result.toString().trimEnd()
    }
}