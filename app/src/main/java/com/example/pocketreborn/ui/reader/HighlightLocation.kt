package com.example.pocketreborn.ui.reader

import com.example.pocketreborn.data.local.entity.HighlightEntity

internal fun findHighlightOffset(articleText: String, highlight: HighlightEntity): Int? {
    val passage = highlight.highlightedText
    if (passage.isEmpty()) return null

    val savedStart = highlight.startOffset
    val savedEnd = highlight.endOffset
    if (savedStart >= 0 && savedEnd <= articleText.length && savedStart <= savedEnd &&
        articleText.substring(savedStart, savedEnd) == passage
    ) {
        return savedStart
    }

    val occurrences = mutableListOf<Int>()
    var searchFrom = 0
    while (searchFrom <= articleText.length - passage.length) {
        val found = articleText.indexOf(passage, searchFrom)
        if (found < 0) break
        occurrences += found
        searchFrom = found + passage.length
    }
    if (occurrences.size == 1) return occurrences.single()

    val contextualMatches = occurrences.filter { start ->
        val end = start + passage.length
        val prefixMatches = highlight.prefixContext.isNullOrEmpty() ||
            articleText.substring(0, start).endsWith(highlight.prefixContext)
        val suffixMatches = highlight.suffixContext.isNullOrEmpty() ||
            articleText.substring(end).startsWith(highlight.suffixContext)
        prefixMatches && suffixMatches
    }
    return contextualMatches.singleOrNull()
}
