package io.github.mk3core.coresms.data

import java.text.Normalizer
import java.util.Locale

/**
 * Splits text into searchable words the same way for messages and queries:
 * lowercase, accents dropped ("Café" → "cafe"), apostrophes inside words
 * dropped ("don't" → "dont"), and Chinese/Japanese characters one at a time
 * since they aren't separated by spaces.
 */
object Words {

    private const val MAX_WORD = 64

    /** A word in the original text: where it is and its searchable form. */
    class Token(val range: IntRange, val word: String)

    fun split(text: String): List<String> = tokens(text).map { it.word }

    fun tokens(text: String): List<Token> {
        val result = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val size = Character.charCount(cp)
            when {
                isIdeograph(cp) -> {
                    add(result, text, i, i + size)
                    i += size
                }
                isWordChar(cp) -> {
                    var end = i + size
                    while (end < text.length) {
                        val next = text.codePointAt(end)
                        if (isWordChar(next) && !isIdeograph(next)) {
                            end += Character.charCount(next)
                        } else if (isApostrophe(next) && end + 1 < text.length && isWordChar(text.codePointAt(end + 1)) && !isIdeograph(text.codePointAt(end + 1))) {
                            end += 1
                        } else {
                            break
                        }
                    }
                    add(result, text, i, end)
                    i = end
                }
                else -> i += size
            }
        }
        return result
    }

    /** Where in [text] the words of [query] appear (as whole words or word starts). */
    fun matches(text: String, query: String): List<IntRange> {
        val wanted = split(query).distinct()
        if (wanted.isEmpty()) return emptyList()
        return tokens(text).filter { token -> wanted.any { token.word.startsWith(it) } }.map { it.range }
    }

    /**
     * A piece of [text] that starts a little before the first match, so the
     * match is visible in a two-line preview, with [ranges] shifted to fit.
     */
    fun snippet(text: String, ranges: List<IntRange>, lead: Int = 32): Pair<String, List<IntRange>> {
        val flat = text.replace('\n', ' ')
        val first = ranges.minOfOrNull { it.first } ?: return flat to emptyList()
        if (first <= lead) return flat to ranges
        // Start at a word boundary a little before the match.
        var start = first - lead
        while (start < first && !flat[start - 1].isWhitespace()) start++
        val prefix = "…"
        val shift = start - prefix.length
        return (prefix + flat.substring(start)) to ranges.filter { it.first >= start }.map { (it.first - shift)..(it.last - shift) }
    }

    private fun add(into: MutableList<Token>, text: String, start: Int, end: Int) {
        val word = normalize(text.substring(start, end))
        if (word.isNotEmpty()) into += Token(start until end, word.take(MAX_WORD))
    }

    private fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        val sb = StringBuilder(decomposed.length)
        var i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    private fun isWordChar(cp: Int): Boolean {
        if (Character.isLetterOrDigit(cp)) return true
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun isApostrophe(cp: Int) = cp == '\''.code || cp == '’'.code

    private fun isIdeograph(cp: Int): Boolean = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> false
    }
}
