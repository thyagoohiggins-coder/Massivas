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
        val color: CarColor? = null,
        val stations: List<Station> = emptyList(),
        val bolsao: Bolsao? = null,
        val status: Status? = null,
    )
    data class Result(
        val entries: List<Entry>,
        val unknown: List<String>,
        val unknownModels: List<String>,
        /** Texto arrumado, na ordem: "VB001778 CS55, VB000433, ..." */
        val canonical: String = "",
    )

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
                t.length == 17 && t.any(Char::isDigit) && t.any(Char::isLetter) -> segs.last() += t
                else -> Regex("[A-Z]+|[0-9]+").findAll(t).forEach { run ->
                    val r = run.value
                    segs.last() += (if (r[0].isLetter()) decomposeLetters(r) else null) ?: r
                }
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

    private val colorWords = mapOf(
        "AZUL" to CarColor.AZUL,
        "CINZA" to CarColor.CINZA, "CINZENTO" to CarColor.CINZA, "CINZENTA" to CarColor.CINZA, "PRATA" to CarColor.CINZA,
        "PRETO" to CarColor.PRETO, "PRETA" to CarColor.PRETO,
        "BRANCO" to CarColor.BRANCO, "BRANCA" to CarColor.BRANCO,
    )

    /** Frases-chave (estação, bolsão, status) já no formato das peças faladas. */
    private class Phrase(val tokens: List<String>, val marker: String)

    private val phrases: List<Phrase> by lazy {
        fun toks(k: String) = segments(k).flatten()
        val list = mutableListOf<Phrase>()
        for (s in Station.entries) for (k in s.keywords) list += Phrase(toks(k), "#ST:" + s.name)
        for (s in Station.entries) list += Phrase(toks(s.label), "#ST:" + s.name)
        list += Phrase(toks("AVALIACAO"), "#ST:" + Station.AVALIACAO_QA.name)
        for (b in Bolsao.entries) {
            for (k in b.keywords) list += Phrase(toks(k), "#BO:" + b.name)
            list += Phrase(toks(b.label), "#BO:" + b.name)
        }
        for (k in listOf("TUNEL DE LIBERACAO", "TUNEL")) list += Phrase(toks(k), "#SS:" + Status.TUNEL.name)
        for (k in listOf("LIBERADO", "LIBERADA")) list += Phrase(toks(k), "#SS:" + Status.LIBERADO.name)
        list.filter { it.tokens.isNotEmpty() }
            .distinctBy { it.tokens to it.marker }
            .sortedWith(compareByDescending<Phrase> { it.tokens.size }.thenByDescending { it.tokens.sumOf { t -> t.length } })
    }

    /** Troca as palavras-chave por marcadores ("PINTURA" → "#ST:PINTURA"). */
    private fun markPhrases(seg: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < seg.size) {
            val ph = phrases.firstOrNull { ph ->
                i + ph.tokens.size <= seg.size && ph.tokens.indices.all { seg[i + it] == ph.tokens[it] } &&
                    // "T1E ADAS" e "TIGGO 7 ADAS" são modelo, não a estação Adas
                    !(ph.marker == "#ST:ADAS" && (
                        out.takeLast(3).joinToString("") == "T1E" || out.lastOrNull() == "T1E" ||
                            out.takeLast(2) == listOf("TIGGO", "7")))
            }
            if (ph != null) { out += ph.marker; i += ph.tokens.size } else { out += seg[i]; i++ }
        }
        return out
    }

    private fun isFull(p: String) = p.length == 17 && p.any(Char::isDigit) && p.any(Char::isLetter)

    /**
     * @param prefix prefixo das letras (ex.: "VB")
     * @param forcePrefix sempre usa [prefix] no lugar das letras ouvidas (corrige "BB", "VV"...)
     * @param digitLen quantos dígitos tem um VIN (ex.: 6). Ao completar, o próximo número já é outro VIN,
     *   então a "vírgula" é opcional.
     */
    fun parse(text: String, prefix: String = "VB", forcePrefix: Boolean = false, digitLen: Int = 6): Result =
        parse(text, listOf(prefix), forcePrefix, digitLen)

    /** Letras iguais valem 2, letras que soam parecido (vê/bê/cê...) valem 1. */
    private fun similarity(heard: String, p: String): Int {
        var score = 0
        for (i in 0 until minOf(heard.length, p.length)) {
            score += when {
                heard[i] == p[i] -> 2
                heard[i] in confusable && p[i] in confusable -> 1
                else -> -1
            }
        }
        return score - 2 * kotlin.math.abs(heard.length - p.length)
    }

    private fun soundsLike(heard: String, p: String) =
        heard.length == p.length && heard.indices.all { heard[it] == p[it] || (heard[it] in confusable && p[it] in confusable) }

    /**
     * Vários prefixos (ex.: VB e VC): as letras ouvidas viram o prefixo mais parecido.
     * VIN falado só com números usa o último prefixo usado.
     */
    fun parse(text: String, prefixList: List<String>, forcePrefix: Boolean = false, digitLen: Int = 6): Result {
        val prefixes = prefixList.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        val prefix = prefixes.firstOrNull() ?: ""
        val entries = LinkedHashMap<String, Entry>()
        val unknown = mutableListOf<String>()
        val unknownModels = mutableListOf<String>()
        val canonical = mutableListOf<String>()
        var lastPrefix = prefix

        var letters = ""
        var digits = ""
        var full: String? = null
        val rest = StringBuilder()
        var color: CarColor? = null
        val stations = mutableListOf<Station>()
        var bolsao: Bolsao? = null
        var status: Status? = null

        fun model(r0: String): CarModel? {
            val r = r0.replace("TIGUO", "TIGGO").replace(Regex("TIGO(?!O)"), "TIGGO")
            fun find(x: String) = modelAliases.firstOrNull { x.startsWith(it.first) }?.second
            return if (r.isEmpty()) null else find(r) ?: find(r.removePrefix("E"))
        }

        fun finish() {
            if (letters.length > 4) { rest.insert(0, letters); letters = "" }
            val f = full
            when {
                f != null || digits.length >= 4 -> {
                    var added = false
                    var fixed = false
                    val vin = if (f != null) f else {
                        var l = letters
                        // em empate, fica o último prefixo usado
                        fun closest(h: String) = prefixes.maxWithOrNull(
                            compareBy<String> { similarity(h, it) }.thenBy { if (it == lastPrefix) 1 else 0 }
                        )!!
                        when {
                            l.isEmpty() -> { l = lastPrefix; added = lastPrefix.isNotEmpty() }
                            l in prefixes -> lastPrefix = l
                            forcePrefix && prefixes.isNotEmpty() -> { l = closest(l); fixed = true; lastPrefix = l }
                            prefixes.any { soundsLike(l, it) } -> {
                                l = closest(l); fixed = true; lastPrefix = l
                            }
                            else -> lastPrefix = l
                        }
                        l + digits
                    }
                    val m = model(rest.toString())
                    if (rest.isNotEmpty() && m == null) unknownModels += "$vin: $rest"
                    if (vin !in entries) entries[vin] = Entry(vin, m, added, fixed, color = color, stations = stations.toList(), bolsao = bolsao, status = status)
                    else {
                        val old = entries.getValue(vin)
                        entries[vin] = old.copy(
                            model = m ?: old.model, color = color ?: old.color,
                            stations = (old.stations + stations).distinct(), bolsao = bolsao ?: old.bolsao, status = status ?: old.status,
                        )
                    }
                    canonical += (listOfNotNull(
                        vin, m?.short, color?.label?.uppercase(),
                        when (status) { Status.TUNEL -> "TÚNEL"; Status.LIBERADO -> "LIBERADO"; else -> null },
                        bolsao?.label?.uppercase(),
                    ) + stations.map { it.label.uppercase() }).joinToString(" ")
                }
                letters.isNotEmpty() || digits.isNotEmpty() || rest.isNotEmpty() -> {
                    val raw = listOf(letters, digits, rest.toString()).filter { it.isNotEmpty() }.joinToString(" ")
                    unknown += raw
                    canonical += raw
                }
            }
            letters = ""; digits = ""; full = null; rest.setLength(0); color = null
            stations.clear(); bolsao = null; status = null
        }

        fun startDigits(p: String) {
            digits = p
            // números grudados ("005964005982") viram VINs separados
            while (digits.length >= 2 * digitLen) {
                val restDigits = digits.drop(digitLen)
                digits = digits.take(digitLen)
                finish()
                digits = restDigits
            }
        }

        for (seg0 in segments(text)) {
            // junta letras vizinhas: "V" "B" → "VB", "UNI" "T" → "UNIT"
            val seg = mutableListOf<String>()
            for (p in markPhrases(seg0)) {
                val last = seg.lastOrNull()
                if (last != null && !isFull(p) && !isFull(last) && p.all(Char::isLetter) && last.all(Char::isLetter) &&
                    p !in colorWords && last !in colorWords
                ) seg[seg.lastIndex] = last + p
                else seg += p
            }
            for ((i, p) in seg.withIndex()) {
                val hasVin = full != null || digits.isNotEmpty()
                when {
                    isFull(p) -> { if (hasVin || letters.isNotEmpty()) finish(); full = p }
                    p in colorWords -> color = colorWords.getValue(p)
                    p.startsWith("#ST:") -> enumOrNull<Station>(p.substring(4))?.let { if (it !in stations) stations += it }
                    p.startsWith("#BO:") -> bolsao = enumOrNull<Bolsao>(p.substring(4))
                    p.startsWith("#SS:") -> status = enumOrNull<Status>(p.substring(4))
                    p.all(Char::isLetter) -> {
                        val next = seg.getOrNull(i + 1)
                        // "VB"/"BB" (parecido com o prefixo) começa outro VIN, mesmo no fim de um trecho falado
                        val prefixLike = prefixes.any { p == it || soundsLike(p, it) }
                        val nextIsDigits = next != null && next.all(Char::isDigit)
                        val startsVin = (p.length <= 4 && nextIsDigits && next!!.length >= 4 && model(p) == null) ||
                            (prefixLike && (next == null || nextIsDigits))
                        when {
                            hasVin && startsVin -> { finish(); letters = p }
                            hasVin -> rest.append(p)
                            else -> letters += p
                        }
                    }
                    else -> { // números
                        val complete = full != null || digits.length >= digitLen || rest.isNotEmpty()
                        when {
                            complete && p.length >= 4 -> { finish(); startDigits(p) }
                            complete -> rest.append(p)
                            digits.isNotEmpty() && (p.length >= digitLen || (digits.length >= 4 && digits.length + p.length > digitLen)) -> {
                                finish(); startDigits(p)
                            }
                            else -> startDigits(digits + p)
                        }
                    }
                }
            }
            finish()
        }
        return Result(entries.values.toList(), unknown, unknownModels, canonical.joinToString(", "))
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

    /** Reescreve o campo já arrumado: "VB003090, VB003940, VB004645 CS55". */
    fun tidy(text: String, prefix: String, forcePrefix: Boolean, digitLen: Int = 6): String =
        tidy(text, listOf(prefix), forcePrefix, digitLen)

    fun tidy(text: String, prefixes: List<String>, forcePrefix: Boolean, digitLen: Int = 6): String {
        val out = parse(text, prefixes, forcePrefix, digitLen).canonical
        return if (out.isNotEmpty() && text.trimEnd().endsWith(",")) "$out, " else out
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
