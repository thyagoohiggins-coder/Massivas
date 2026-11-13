package com.massivas.fai

/** Compara duas listas de VINs. */
object VinCompare {
    data class Diff(val onlyMine: List<String>, val both: List<String>, val onlyOther: List<String>)

    fun compare(mine: List<String>, other: List<String>): Diff {
        val a = mine.distinct()
        val b = other.distinct()
        val setA = a.toSet()
        val setB = b.toSet()
        fun sorted(l: List<String>) = l.sortedWith(compareBy<String> { vinSortKey(it) }.thenBy { it })
        return Diff(
            onlyMine = sorted(a.filter { it !in setB }),
            both = sorted(a.filter { it in setB }),
            onlyOther = sorted(b.filter { it !in setA }),
        )
    }

    /** Lê VINs de qualquer texto: lista do WhatsApp ou VINs separados por vírgula. */
    fun vinsFromText(text: String, prefixes: List<String>, forcePrefix: Boolean, digitLen: Int = 6): List<String> {
        val whatsapp = TextFormat.parse(text).map { it.vin }
        val quick = QuickVin.parse(text, prefixes, forcePrefix, digitLen).entries.map { it.vin }
        return if (whatsapp.size >= quick.size) whatsapp else quick
    }
}
