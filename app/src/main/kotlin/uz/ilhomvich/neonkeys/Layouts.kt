package uz.ilhomvich.neonkeys

enum class KT { CHAR, SHIFT, DEL, ENTER, SPACE, GOTO, LANG, EMOJI, SPACER }

class Key(
    val label: String,
    val type: KT = KT.CHAR,
    val w: Float = 1f,
    val alts: List<String> = emptyList(),
    val hint: String = "",
    val data: String = ""
)

object Layouts {
    val NAMES = listOf("English", "Oʻzbek", "Русский")

    private class L(val r1: String, val r2: String, val r3: String, val alts: Map<String, List<String>>)

    private val EN = L(
        "qwertyuiop", "asdfghjkl", "zxcvbnm",
        mapOf(
            "a" to listOf("à", "á", "â", "ä", "ã", "å", "ā"),
            "e" to listOf("è", "é", "ê", "ë", "ē"),
            "i" to listOf("ì", "í", "î", "ï"),
            "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø"),
            "u" to listOf("ù", "ú", "û", "ü"),
            "c" to listOf("ç", "ć", "č"),
            "n" to listOf("ñ", "ń"),
            "s" to listOf("ß", "ś", "š"),
            "y" to listOf("ý", "ÿ"),
            "z" to listOf("ž", "ź")
        )
    )
    private val UZ = L(
        "qwertyuiop", "asdfghjkl", "zxcvbnm",
        mapOf(
            "o" to listOf("oʻ", "ō"),
            "g" to listOf("gʻ", "ğ"),
            "s" to listOf("sh", "ş"),
            "c" to listOf("ch", "ç"),
            "u" to listOf("ü"),
            "a" to listOf("ä"),
            "i" to listOf("ï")
        )
    )
    private val RU = L(
        "йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю",
        mapOf("е" to listOf("ё"))
    )

    private val SHIFT = Key("", KT.SHIFT, 1.5f)
    private val DEL = Key("", KT.DEL, 1.5f)
    private fun sp(w: Float) = Key("", KT.SPACER, w)

    private fun letters(s: String, alts: Map<String, List<String>>, digitHints: Boolean): List<Key> =
        s.mapIndexed { i, ch ->
            val c = ch.toString()
            val a = alts[c] ?: emptyList()
            if (digitHints && i < 10) {
                val d = ((i + 1) % 10).toString()
                Key(c, KT.CHAR, 1f, a + d, d)
            } else Key(c, KT.CHAR, 1f, a)
        }

    private fun chars(s: String): List<Key> = s.map { Key(it.toString()) }

    fun build(mode: String, lang: Int, numRow: Boolean): List<List<Key>> = when (mode) {
        "sym1" -> listOf(
            chars("1234567890"),
            chars("@#\$_&-+()/"),
            listOf(Key("=\\<", KT.GOTO, 1.5f, data = "sym2")) + chars("*\"':;!?") + DEL,
            bottom(false, lang)
        )
        "sym2" -> listOf(
            chars("1234567890"),
            chars("[]{}<>^=%\\"),
            listOf(Key("?123", KT.GOTO, 1.5f, data = "sym1")) + chars("£€¥¢~|°") + DEL,
            bottom(false, lang)
        )
        else -> abc(lang, numRow)
    }

    private fun abc(lang: Int, numRow: Boolean): List<List<Key>> {
        val l = when (lang) { 1 -> UZ; 2 -> RU; else -> EN }
        val rows = ArrayList<List<Key>>()
        if (numRow) rows.add(chars("1234567890"))
        rows.add(letters(l.r1, l.alts, !numRow))
        rows.add(listOf(sp(0.5f)) + letters(l.r2, l.alts, false) + sp(0.5f))
        rows.add(listOf(SHIFT) + letters(l.r3, l.alts, false) + DEL)
        rows.add(bottom(true, lang))
        return rows
    }

    private fun bottom(abc: Boolean, lang: Int): List<Key> {
        val l = ArrayList<Key>()
        l.add(if (abc) Key("?123", KT.GOTO, 1.4f, data = "sym1") else Key("ABC", KT.GOTO, 1.4f, data = "abc"))
        if (abc) l.add(Key("", KT.LANG, 1f))
        val commaAlts = if (lang == 1) listOf("ʼ", "'", "\"", ";", ":") else listOf("'", "\"", ";", ":", "!", "?")
        l.add(Key(",", KT.CHAR, 1f, commaAlts))
        l.add(Key("", KT.SPACE, if (abc) 3.6f else 4.6f))
        l.add(Key(".", KT.CHAR, 1f, listOf("…", "!", "?", ":", ";", "-")))
        l.add(Key("", KT.EMOJI, 1f))
        l.add(Key("", KT.ENTER, 1.4f))
        return l
    }

    fun emojiBottom(): List<Key> = listOf(
        Key("ABC", KT.GOTO, 1.6f, data = "abc"),
        Key("", KT.SPACE, 5f),
        Key("", KT.DEL, 1.6f)
    )
}
