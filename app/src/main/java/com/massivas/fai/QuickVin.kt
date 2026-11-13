package com.massivas.fai

/**
 * Entende listas rápidas de VINs, digitadas ou faladas:
 *
 *   "VB001778 CS55, VB000433 C928, VB004163"
 *   "vê bê zero zero um sete sete oito cs cinquenta e cinco vírgula vb mil setecentos e setenta e nove"
 *
 * O modelo depois do VIN é opcional. Se o VIN vier só com números, usa o prefixo do anterior (ex.: VB).
 */
object QuickVin {
    data class Entry(
        val vin: String,
        val model: CarModel?,
        val prefixAdded: Boolean,
        val prefixFixed: Boolean = false,
        val padded: Boolean = false,
    )
    data class Result(val entries: List<Entry>, val unknown: List<String>, val unknownModels: List<String>)

    private val units = mapOf(
        "ZERO" to 0, "UM" to 1, "UMA" to 1, "DOIS" to 2, "DUAS" to 2, "TRES" to 3, "QUATRO" to 4,
        "CINCO" to 5, "SEIS" to 6, "MEIA" to 6, "SETE" to 7, "OITO" to 8, "NOVE" to 9,
    )
    private val tensAndTeens = mapOf(
        "DEZ" to 10, "ONZE" to 11, "DOZE" to 12, "TREZE" to 13, "QUATORZE" to 14, "CATORZE" to 14,
        "QUINZE" to 15, "DEZESSEIS" to 16, "DEZESEIS" to 16, "DEZASSEIS" to 16, "DEZESSETE" to 17,
        "DEZESETE" to 17, "DEZOITO" to 18, "DEZENOVE" to 19,
        "VINTE" to 20, "TRINTA" to 30, "QUARENTA" to 40, "CINQUENTA" to 50,
        "SESSENTA" to 60, "SETENTA" to 70, "OITENTA" to 80, "NOVENTA" to 90,
        "CEM" to 100, "CENTO" to 100, "DUZENTOS" to 200, "TREZENTOS" to 300, "QUATROCENTOS" to 400,
        "QUINHENTOS" to 500, "SEISCENTOS" to 600, "SETECENTOS" to 700, "OITOCENTOS" to 800, "NOVECENTOS" to 900,
    )
    private const val MIL = "MIL"

    /** Nome falado das letras (sem acento). I, O e Q não existem em VIN. */
    private val letterNames = mapOf(
        "BE" to "B", "CE" to "C", "EFE" to "F", "GE" to "G", "AGA" to "H", "JOTA" to "J",
        "CA" to "K", "KA" to "K", "ELE" to "L", "EME" to "M", "ENE" to "N", "PE" to "P", "ERRE" to "R",
        "ESSE" to "S", "TE" to "T", "VE" to "V", "DABLIO" to "W", "DABLIU" to "W", "XIS" to "X",
        "IPSILON" to "Y", "ZE" to "Z", "U" to "U",
    )
    private val fillers = setOf(
        "O", "A", "OS", "AS", "DE", "DO", "DA", "QUE", "MODELO", "CARRO", "VIN", "NUMERO", "COM", "NO", "NA",
    )
    private val separators = setOf(",", ";", "VIRGULA", "PROXIMO", "PROXIMA", "PONTO", "OUTRO")

    private val modelAliases: List<Pair<String, CarModel>> =
        CarModel.entries.flatMap { m -> m.aliases.map { compact(it) to m } }.sortedByDescending { it.first.length }

    private fun compact(s: String) = fold(s).filter { it.isLetterOrDigit() }

    private fun isNumberWord(t: String) = t in units || t in tensAndTeens || t == MIL

    private val spelled = letterNames + mapOf("DE" to "D")

    /** "BEBE" → "BB", "VEBE" → "VB" (letras faladas grudadas). */
    private fun decomposeLetters(t: String): String? {
        if (t.length < 4) return null
        val best = arrayOfNulls<String>(t.length + 1)
        best[0] = ""
        for (i in 0 until t.length) {
            val cur = best[i] ?: continue
            for ((name, letter) in spelled) {
                if (t.startsWith(name, i) && best[i + name.length] == null) best[i + name.length] = cur + letter
            }
        }
        return best[t.length]?.takeIf { it.length >= 2 }
    }

    /** Letras cujo nome falado termina em "ê" e se confundem (vê/bê/dê/pê/tê...). */
    private val confusable = setOf('B', 'V', 'D', 'P', 'T', 'G', 'C', 'Z', 'E', 'W')

    /** Separa em grupos (um por VIN) e converte palavras em letras/números. */
    private fun segments(text: String): List<List<String>> {
        val tokens = fold(text)
            .replace("-", "")
            .replace(Regex("[,;\\n]"), " , ")
            .replace(Regex("[^A-Z0-9, ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() }

        val segs = mutableListOf<MutableList<String>>(mutableListOf())
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t in separators -> segs += mutableListOf<String>()
                isNumberWord(t) -> {
                    // junta uma sequência de números falados ("dois mil e trinta e cinco", "zero zero um")
                    val run = mutableListOf(t)
                    var j = i + 1
                    while (j < tokens.size) {
                        val n = tokens[j]
                        if (isNumberWord(n)) { run += n; j++ }
                        else if (n == "E" && j + 1 < tokens.size && isNumberWord(tokens[j + 1])) { run += n; j++ }
                        else break
                    }
                    segs.last() += spokenNumber(run)
                    i = j
                    continue
                }
                t in fillers -> Unit
                t == "E" -> segs.last() += "E"
                t in letterNames -> segs.last() += letterNames.getValue(t)
                else -> segs.last() += (if (t.all { it.isLetter() }) decomposeLetters(t) else null) ?: t
            }
            i++
        }
        return segs.filter { it.isNotEmpty() }
    }

    /** "zero zero um sete" → "0017"; "mil setecentos e setenta e oito" → "1778". */
    private fun spokenNumber(words: List<String>): String {
        val out = StringBuilder()
        var total = 0
        var current = 0
        var started = false
        var prev: String? = null
        fun flush() {
            if (started) out.append(total + current)
            total = 0; current = 0; started = false
        }
        for (w in words) {
            if (w == "E") { prev = w; continue }
            val continues = started && (prev == "E" || prev == MIL || w == MIL)
            if (!continues) flush()
            when {
                w == "ZERO" -> { out.append('0'); prev = w; continue }
                w == MIL -> { total += (if (current == 0) 1 else current) * 1000; current = 0 }
                w in units -> current += units.getValue(w)
                else -> current += tensAndTeens.getValue(w)
            }
            started = true
            prev = w
        }
        flush()
        return out.toString()
    }

    /**
     * @param prefix prefixo das letras (ex.: "VB")
     * @param forcePrefix sempre usa [prefix] no lugar das letras ouvidas (corrige "BB", "VV"...)
     * @param padTo completa com zeros à esquerda até esse número de dígitos (0 = não completa)
     */
    fun parse(text: String, prefix: String = "VB", forcePrefix: Boolean = false, padTo: Int = 0): Result {
        val entries = mutableListOf<Entry>()
        val unknown = mutableListOf<String>()
        val unknownModels = mutableListOf<String>()
        var lastPrefix = prefix
        for (seg in segments(text)) {
            val full = seg.firstOrNull { it.length == 17 && it.any(Char::isDigit) && it.any(Char::isLetter) }
            var vin: String
            val rest: String
            var added = false
            var fixed = false
            var padded = false
            if (full != null) {
                vin = full
                rest = seg.filter { it != full }.joinToString("")
            } else {
                val c = seg.joinToString("")
                val m = Regex("^([A-Z]{0,4})([0-9]{4,})(.*)$").find(c)
                if (m == null) { unknown += seg.joinToString(" "); continue }
                var letters = m.groupValues[1]
                when {
                    forcePrefix && prefix.isNotEmpty() -> {
                        if (letters.isEmpty()) added = true else if (letters != prefix) fixed = true
                        letters = prefix
                    }
                    letters.isEmpty() -> { letters = lastPrefix; added = lastPrefix.isNotEmpty() }
                    letters != prefix && letters.length == prefix.length &&
                        letters.indices.all { letters[it] in confusable && prefix[it] in confusable } -> {
                        letters = prefix; fixed = true
                    }
                    else -> lastPrefix = letters
                }
                var digits = m.groupValues[2]
                if (padTo > 0 && digits.length < padTo) { digits = digits.padStart(padTo, '0'); padded = true }
                vin = letters + digits
                rest = m.groupValues[3]
            }
            val r = rest.replace("TIGUO", "TIGGO").replace(Regex("TIGO(?!O)"), "TIGGO")
            fun find(x: String) = modelAliases.firstOrNull { x.startsWith(it.first) }?.second
            val model = if (r.isEmpty()) null else find(r) ?: find(r.removePrefix("E"))
            if (r.isNotEmpty() && model == null) unknownModels += "$vin: $rest"
            entries += Entry(vin, model, added, fixed, padded)
        }
        return Result(entries.associateBy { it.vin }.values.toList(), unknown, unknownModels)
    }

    /**
     * Converte a fala em texto para o campo, SEM decidir onde o VIN termina:
     * só "vírgula" (ou "próximo") separa. Ex.: "vê bê zero zero um dois" → "VB 0012".
     */
    fun convertSpeech(text: String): String {
        val tokens = fold(text)
            .replace("-", "")
            .replace(Regex("[,;\\n]"), " , ")
            .replace(Regex("[^A-Z0-9, ]"), " ")
            .split(' ').filter { it.isNotBlank() }
        val sb = StringBuilder()
        var piece = StringBuilder()
        fun flush() {
            if (piece.isNotBlank()) {
                val segs = segments(piece.toString())
                val conv = segs.joinToString(" ") { it.joinToString(" ") }
                if (conv.isNotBlank()) { if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' '); sb.append(conv) }
            }
            piece = StringBuilder()
        }
        for (t in tokens) {
            if (t in separators) { flush(); sb.append(", ") } else piece.append(t).append(' ')
        }
        flush()
        return sb.toString()
    }

    /** Arruma os VINs já terminados (antes de uma vírgula) no formato "VB001778 CS55". */
    fun tidy(text: String, prefix: String, forcePrefix: Boolean, padTo: Int): String {
        val parts = text.replace(Regex("\\s+"), " ").split(',')
        if (parts.size < 2) return text.replace(Regex("\\s+"), " ").trimStart()
        val done = parts.dropLast(1).map { p ->
            val r = parse(p, prefix, forcePrefix, padTo)
            val e = r.entries.singleOrNull()
            if (e != null && r.unknown.isEmpty()) (if (e.model != null) "${e.vin} ${e.model.short}" else e.vin) else p.trim()
        }.filter { it.isNotBlank() }
        val last = parts.last().trim()
        return done.joinToString(", ") + ", " + last
    }

    /** Converte o que foi falado em texto limpo: "VB001778 CS55, VB000433". */
    fun normalizeSpeech(text: String, defaultPrefix: String = "VB"): String {
        val r = parse(text, defaultPrefix)
        val parts = r.entries.map { e -> if (e.model != null) "${e.vin} ${e.model.short}" else e.vin } + r.unknown
        return parts.joinToString(", ")
    }

    /** Quantos VINs um texto falado gera (para escolher a melhor alternativa do reconhecedor). */
    fun score(text: String): Int {
        val r = parse(text)
        return r.entries.size * 10 + r.entries.count { it.model != null } * 2 - r.unknown.size * 5 - r.unknownModels.size * 3
    }
}
