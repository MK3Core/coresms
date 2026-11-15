package io.github.mk3core.coresms.reactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsTest {

    @Test
    fun classicTapbacks() {
        assertEquals(ParsedReaction("❤️", "See you at 6"), Reactions.parse("Loved “See you at 6”"))
        assertEquals(ParsedReaction("👍", "ok"), Reactions.parse("Liked “ok”"))
        assertEquals(ParsedReaction("😂", "lol what"), Reactions.parse("Laughed at “lol what”"))
        assertEquals(ParsedReaction("‼️", "Big news"), Reactions.parse("Emphasized “Big news”"))
        assertEquals(ParsedReaction("❓", "Dinner?"), Reactions.parse("Questioned “Dinner?”"))
        assertEquals(ParsedReaction("👎", "Monday"), Reactions.parse("Disliked “Monday”"))
    }

    @Test
    fun straightQuotesAndMultiline() {
        assertEquals(ParsedReaction("❤️", "line one\nline two"), Reactions.parse("Loved \"line one\nline two\""))
    }

    @Test
    fun emojiReactionsFromIos18() {
        assertEquals(ParsedReaction("🔥", "New car"), Reactions.parse("Reacted 🔥 to “New car”"))
        assertEquals(ParsedReaction("🫶🏽", "thanks"), Reactions.parse("Reacted 🫶🏽 to “thanks”"))
    }

    @Test
    fun removals() {
        assertEquals(ParsedReaction("❤️", "See you", removed = true), Reactions.parse("Removed a heart from “See you”"))
        assertEquals(ParsedReaction("🔥", "New car", removed = true), Reactions.parse("Removed 🔥 from “New car”"))
    }

    @Test
    fun mediaReactions() {
        assertEquals(ParsedReaction("❤️", null, target = "image"), Reactions.parse("Loved an image"))
        assertEquals(ParsedReaction("😮", null, target = "video"), Reactions.parse("Reacted 😮 to a video"))
    }

    @Test
    fun googleMessagesFormat() {
        assertEquals(ParsedReaction("👍", "on my way"), Reactions.parse("👍 to “on my way”"))
    }

    @Test
    fun ordinaryTextsAreNotReactions() {
        assertNull(Reactions.parse("Loved the movie last night"))
        assertNull(Reactions.parse("Reacted badly to “the news”"))
        assertNull(Reactions.parse("Went to “the place”"))
        assertNull(Reactions.parse("ok"))
    }

    @Test
    fun composeUsesTapbackWordsForTheClassicSix() {
        assertEquals("Loved “See you at 6”", Reactions.compose("❤️", "See you at 6", false))
        assertEquals("Reacted 🔥 to “New car”", Reactions.compose("🔥", "New car", false))
        assertEquals("Removed a heart from “x”", Reactions.compose("❤️", "x", false, removed = true))
        assertEquals("Loved an image", Reactions.compose("❤️", "", true))
    }

    @Test
    fun composedReactionsParseBack() {
        for (emoji in Reactions.QUICK + listOf("🔥", "🙏")) {
            val parsed = Reactions.parse(Reactions.compose(emoji, "Test message", false))
            assertEquals(emoji, parsed?.emoji)
            assertEquals("Test message", parsed?.quoted)
        }
    }

    @Test
    fun matchingTruncatedQuotes() {
        assertTrue(Reactions.matches("See you", "See you"))
        assertTrue(Reactions.matches("This is a long message that got cut…", "This is a long message that got cut off by the phone"))
        assertFalse(Reactions.matches("Something else", "See you"))
    }
}
