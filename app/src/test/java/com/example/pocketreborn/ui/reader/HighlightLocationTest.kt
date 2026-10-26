package com.example.pocketreborn.ui.reader

import com.example.pocketreborn.data.local.entity.HighlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightLocationTest {

    @Test
    fun findsPassageAtSavedOffset() {
        val text = "Opening. A saved passage. Ending."
        val start = text.indexOf("A saved passage.")
        val highlight = HighlightEntity(
            id = "highlight",
            articleId = "article",
            highlightedText = "A saved passage.",
            startOffset = start,
            endOffset = start + "A saved passage.".length
        )

        assertEquals(start, findHighlightOffset(text, highlight))
    }

    @Test
    fun usesContextWhenSavedOffsetsNoLongerMatch() {
        val text = "First repeated quote. Middle. Second repeated quote."
        val secondStart = text.lastIndexOf("repeated quote")
        val highlight = HighlightEntity(
            id = "highlight",
            articleId = "article",
            highlightedText = "repeated quote",
            prefixContext = "Second ",
            suffixContext = ".",
            startOffset = 0,
            endOffset = "repeated quote".length
        )

        assertEquals(secondStart, findHighlightOffset(text, highlight))
    }

    @Test
    fun returnsNoLocationWhenPassageNoLongerExists() {
        val highlight = HighlightEntity(
            id = "highlight",
            articleId = "article",
            highlightedText = "removed passage",
            startOffset = 3,
            endOffset = 18
        )

        assertNull(findHighlightOffset("Changed article text.", highlight))
    }
}
