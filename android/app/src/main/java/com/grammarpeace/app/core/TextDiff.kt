package com.grammarpeace.app.core

/** Word-level diff used to highlight what the AI changed. */
object TextDiff {
    enum class Kind { SAME, ADDED, REMOVED }

    data class Segment(val text: String, val kind: Kind)

    /**
     * Diff of [old] vs [new]. Falls back to showing [new] as unchanged when the text is too long
     * to diff cheaply (the LCS table is quadratic).
     */
    fun segments(old: String, new: String, maxTokens: Int = 600): List<Segment> {
        val a = tokenize(old)
        val b = tokenize(new)
        if (a.size > maxTokens || b.size > maxTokens) return listOf(Segment(new, Kind.SAME))

        // LCS table, filled from the end so we can walk forward.
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val merged = ArrayList<Segment>()
        fun emit(text: String, kind: Kind) {
            val last = merged.lastOrNull()
            // Merge neighbours of the same kind so the UI has fewer pieces.
            if (last != null && last.kind == kind) merged[merged.lastIndex] = Segment(last.text + text, kind)
            else merged.add(Segment(text, kind))
        }

        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { emit(a[i], Kind.SAME); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { emit(a[i], Kind.REMOVED); i++ }
                else -> { emit(b[j], Kind.ADDED); j++ }
            }
        }
        while (i < a.size) emit(a[i++], Kind.REMOVED)
        while (j < b.size) emit(b[j++], Kind.ADDED)
        return merged
    }

    /** Splits into alternating runs of whitespace and non-whitespace, so joining the tokens gives back the input. */
    fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val tokens = ArrayList<String>()
        var start = 0
        var startIsSpace = text[0].isWhitespace()
        for (index in 1 until text.length) {
            val isSpace = text[index].isWhitespace()
            if (isSpace != startIsSpace) {
                tokens.add(text.substring(start, index))
                start = index
                startIsSpace = isSpace
            }
        }
        tokens.add(text.substring(start))
        return tokens
    }
}
