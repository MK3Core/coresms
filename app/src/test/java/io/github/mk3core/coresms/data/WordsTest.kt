package io.github.mk3core.coresms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WordsTest {

    @Test
    fun splitsLowercasesAndDropsAccents() {
        assertEquals(listOf("meet", "at", "the", "cafe", "at", "5pm"), Words.split("Meet at the Café, at 5PM!"))
    }

    @Test
    fun keepsApostropheWordsTogether() {
        assertEquals(listOf("dont", "forget", "alexs", "keys"), Words.split("Don't forget Alex’s keys"))
    }

    @Test
    fun splitsIdeographsOneByOne() {
        assertEquals(listOf("hi", "東", "京"), Words.split("hi東京"))
    }

    @Test
    fun ignoresPunctuationAndEmoji() {
        assertEquals(listOf("ok"), Words.split("👍 ... ok?!"))
        assertTrue(Words.split("😂😂").isEmpty())
    }

    @Test
    fun matchesWordStartsInOriginalText() {
        val text = "Tacos tonight? Café opens at 6"
        val ranges = Words.matches(text, "taco cafe")
        assertEquals(listOf("Tacos", "Café"), ranges.map { text.substring(it) })
    }

    @Test
    fun snippetStartsNearTheFirstMatch() {
        val text = "This is a long message about many different things before we finally mention the pizza place"
        val (snippet, ranges) = Words.snippet(text, Words.matches(text, "pizza"))
        assertTrue(snippet.startsWith("…"))
        assertEquals("pizza", snippet.substring(ranges.single()))
    }

    @Test
    fun snippetKeepsShortTextWhole() {
        val text = "pizza tonight?"
        val (snippet, ranges) = Words.snippet(text, Words.matches(text, "tonight"))
        assertEquals(text, snippet)
        assertEquals("tonight", snippet.substring(ranges.single()))
    }

    private fun matchinfo(vararg ints: Int): ByteArray {
        val buffer = ByteBuffer.allocate(ints.size * 4).order(ByteOrder.nativeOrder())
        ints.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    @Test
    fun bm25FavorsRareWordsAndShortMessages() {
        // 1 phrase, 1 column, 1000 rows, average 10 words, this row 10 words,
        // then hits here, hits everywhere, rows with a hit.
        val rare = SearchIndex.bm25(matchinfo(1, 1, 1000, 10, 10, 1, 5, 5))
        val common = SearchIndex.bm25(matchinfo(1, 1, 1000, 10, 10, 1, 500, 400))
        val longMessage = SearchIndex.bm25(matchinfo(1, 1, 1000, 10, 60, 1, 5, 5))
        assertTrue(rare > common)
        assertTrue(rare > longMessage)
        assertEquals(0.0, SearchIndex.bm25(matchinfo(1, 1, 1000, 10, 10, 0, 5, 5)), 0.0)
    }
}
