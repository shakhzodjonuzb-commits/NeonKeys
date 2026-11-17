package uz.ilhomvich.neonkeys

class Suggest(private val prefs: Prefs) {
    companion object {
        @Volatile private var inst: Suggest? = null
        fun get(p: Prefs): Suggest = inst ?: synchronized(this) { inst ?: Suggest(p).also { inst = it } }
    }

    private val learned = HashMap<String, Int>()
    private var dirty = 0

    init {
        prefs.learned.lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 3) p[2].toIntOrNull()?.let { learned[p[0] + "\t" + p[1]] = it }
        }
    }

    fun suggest(prefix: String, lang: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val pre = "$lang\t"
        val out = LinkedHashSet<String>()
        learned.entries.asSequence()
            .filter { it.key.startsWith(pre) }
            .map { it.key.substring(pre.length) to it.value }
            .filter { it.first.startsWith(p) && it.first != p }
            .sortedByDescending { it.second }
            .take(2)
            .forEach { out.add(it.first) }
        for (w in Words.list(lang)) {
            if (out.size >= 3) break
            val lw = w.lowercase()
            if (lw.startsWith(p) && lw != p) out.add(lw)
        }
        return out.map { caseLike(prefix, it) }
    }

    private fun caseLike(prefix: String, w: String): String = when {
        prefix.length > 1 && prefix.none { it.isLowerCase() } -> w.uppercase()
        prefix[0].isUpperCase() -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    fun learn(word: String, lang: Int) {
        val w = word.lowercase()
        if (w.length < 3) return
        val k = "$lang\t$w"
        learned[k] = (learned[k] ?: 0) + 1
        if (learned.size > 1500) {
            val it = learned.entries.iterator()
            while (it.hasNext() && learned.size > 1200) if (it.next().value <= 1) it.remove()
        }
        if (++dirty >= 4) save()
    }

    fun save() {
        dirty = 0
        prefs.learned = learned.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    fun clear() {
        learned.clear()
        prefs.learned = ""
    }
}
