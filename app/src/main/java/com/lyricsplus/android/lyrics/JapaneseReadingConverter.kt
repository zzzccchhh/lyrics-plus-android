package com.lyricsplus.android.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer

class JapaneseReadingConverter {
    private val tokenizer by lazy { Tokenizer() }

    fun preWarm() {
        runCatching {
            tokenizer.tokenize("あ")
        }
    }

    fun readingFor(text: String): String? {
        if (!text.looksJapanese()) return null
        val reading = runCatching {
            tokenizer.tokenize(text)
                .mapNotNull { token ->
                    val reading = token.reading
                    val source = if (reading == "*" || reading.isNullOrBlank()) token.surface else reading
                    source.kanaToRomaji()
                        .takeIf { romaji -> romaji.any { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() } }
                }
                .joinToString(" ")
        }.getOrNull()

        return reading
            ?.takeIf { it.isNotBlank() && it != text }
    }

    fun furiganaFor(text: String): String? {
        if (!text.looksJapanese()) return null
        val furigana = runCatching {
            tokenizer.tokenize(text).joinToString("") { token ->
                val surface = token.surface
                if (surface.any { it.isKanji() }) {
                    val katakanaReading = token.reading
                    val reading = if (katakanaReading == "*" || katakanaReading.isNullOrBlank()) {
                        surface
                    } else {
                        katakanaReading
                    }
                    val hiragana = reading.katakanaToHiragana()
                    "<ruby>$surface<rt>$hiragana</rt></ruby>"
                } else {
                    surface
                }
            }
        }.getOrNull()

        return furigana?.takeIf { it.isNotBlank() && it != text }
    }

    private fun String.looksJapanese(): Boolean =
        any { it in '\u3040'..'\u30FF' } || any { it in '\u4E00'..'\u9FFF' }

    private fun Char.isKanji(): Boolean =
        this in '\u4E00'..'\u9FFF'

    private fun String.katakanaToHiragana(): String {
        return map { char ->
            if (char in '\u30A1'..'\u30F6') {
                (char.code - 0x60).toChar()
            } else {
                char
            }
        }.joinToString("")
    }

    private fun String.kanaToRomaji(): String {
        val normalized = map { char ->
            if (char in '\u30A1'..'\u30F6') {
                (char.code - 0x60).toChar()
            } else {
                char
            }
        }.joinToString("")

        val result = StringBuilder()
        var i = 0
        var doubleNextConsonant = false

        while (i < normalized.length) {
            val char = normalized[i]
            if (char == 'っ') {
                doubleNextConsonant = true
                i += 1
                continue
            }
            if (char == 'ー') {
                result.extendPreviousVowel()
                i += 1
                continue
            }

            val two = normalized.substring(i, (i + 2).coerceAtMost(normalized.length))
            val mapped = romajiDigraphs[two]
            val value = if (mapped != null) {
                i += 2
                mapped
            } else {
                i += 1
                romajiKana[char] ?: char.toString()
            }

            if (doubleNextConsonant && value.isNotBlank()) {
                val first = value.first()
                if (first.isLetter() && first !in listOf('a', 'e', 'i', 'o', 'u')) {
                    result.append(first)
                }
                doubleNextConsonant = false
            }
            result.append(value)
        }

        return result.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun StringBuilder.extendPreviousVowel() {
        val vowel = lastOrNull { it in listOf('a', 'e', 'i', 'o', 'u') } ?: return
        append(vowel)
    }

    private companion object {
        val romajiDigraphs = mapOf(
            "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
            "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
            "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
            "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
            "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
            "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
            "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
            "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
            "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
            "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
            "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
            "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
            "てぃ" to "ti", "でぃ" to "di", "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo"
        )

        val romajiKana = mapOf(
            'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
            'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
            'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
            'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
            'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
            'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
            'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
            'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
            'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
            'わ' to "wa", 'を' to "wo", 'ん' to "n",
            'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
            'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
            'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
            'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
            'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
            'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
            'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
            '、' to " ", '。' to " ", '！' to " ", '？' to " ", '・' to " "
        )
    }
}
