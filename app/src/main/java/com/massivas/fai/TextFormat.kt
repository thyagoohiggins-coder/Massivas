package com.massivas.fai

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ParsedVehicle(
    val vin: String,
    val model: CarModel?,
    val status: Status?,
    val repairs: List<Repair>,
    val notes: String,
    val forecast: Long? = null,
    val difficulty: Difficulty? = null,
)

/**
 * Lê listas do WhatsApp nos formatos:
 *
 *   1 - VB001778 - CS55 - SECANDO(terrão)⚠️      (modelo na mesma linha)
 *   2 - VB000433 - C928 - PINTURA(cab.04)
 *
 *   CS55 INFINITY - 4                            (modelo como título do grupo)
 *   VB002207 - Ex Ok - Falta Delta, Pestana FR LH Am.
 */
object TextFormat {
    private val ptBR = Locale("pt", "BR")
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", ptBR)

    // VB001778 (letras + números) ou VIN completo de 17 caracteres.
    private val vinRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]{1,4}[0-9]{5,13}|[A-HJ-NPR-Za-hj-npr-z0-9]{17})(?![A-Za-z0-9])")
    private val forecastRegex = Regex("(?:⏱|PREV\\.?|PREVISAO:?)\\s*(\\d{1,2})/(\\d{1,2})(?:/\\d{2,4})?\\s+(\\d{1,2})[:h](\\d{2})", RegexOption.IGNORE_CASE)

    private fun wordRegex(kw: String) =
        Regex("(?<![A-Z0-9])" + Regex.escape(normalize(kw)).replace(" ", "\\E\\s*\\Q") + "(?![A-Z0-9])")

    private val modelPatterns: List<Pair<CarModel, Regex>> =
        CarModel.entries.flatMap { m -> m.aliases.map { m to wordRegex(it) } }
    private val stationPatterns: List<Pair<Station, Regex>> =
        Station.entries.flatMap { s -> s.keywords.map { s to wordRegex(it) } }

    fun parse(text: String): List<ParsedVehicle> {
        val out = mutableListOf<ParsedVehicle>()
        var currentModel: CarModel? = null
        for (raw in text.lines()) {
            val line = raw.trim().trim('*', '_', '~').trim()
            if (line.isEmpty()) continue

            val vinMatch = vinRegex.findAll(line).firstOrNull { m ->
                val t = m.value
                t.count { it.isDigit() } >= 4 && t.any { it.isLetter() }
            }
            if (vinMatch != null) {
                val before = line.substring(0, vinMatch.range.first)
                val after = line.substring(vinMatch.range.last + 1)
                // "🟠 33 - VB000957 ...": número da linha e emoji de nível antes do VIN.
                val rest = if (before.none { it.isLetter() }) {
                    Difficulty.entries.filter { before.contains(it.emoji) }.joinToString(" ") { it.emoji } + " " + after
                } else "$before $after"
                out += parseVehicle(cleanVin(vinMatch.value), rest, currentModel)
                continue
            }

            val header = CarModel.fromHeader(line)
            if (header != null) {
                currentModel = header
                continue
            }
            val n = normalize(line)
            if (n.startsWith("MASSIVA") || n.startsWith("TOTAL") || n.startsWith("LEGENDA")) continue
            // Linha de continuação: junta ao último reparo/observação.
            val last = out.lastOrNull() ?: continue
            out[out.lastIndex] = if (last.repairs.isNotEmpty()) {
                val reps = last.repairs.toMutableList()
                val r = reps.last()
                reps[reps.lastIndex] = r.copy(note = (r.note + " " + line).trim())
                last.copy(repairs = reps)
            } else {
                last.copy(notes = (last.notes + " " + line).trim())
            }
        }
        // Se o mesmo VIN aparecer duas vezes, fica a última ocorrência.
        return out.associateBy { it.vin }.values.toList()
    }

    /** Analisa o texto da linha depois do VIN: modelo, status, estações, previsão e nível. */
    private fun parseVehicle(vin: String, restRaw: String, headerModel: CarModel?): ParsedVehicle {
        val text = restRaw
        val folded = fold(text)
        val removed = BooleanArray(text.length)
        fun remove(r: IntRange) { for (i in r) if (i in removed.indices) removed[i] = true }

        // Modelo (o maior apelido encontrado)
        var model: CarModel? = null
        modelPatterns
            .mapNotNull { (m, re) -> re.find(folded)?.let { m to it.range } }
            .maxByOrNull { it.second.last - it.second.first }
            ?.let { (m, r) -> model = m; remove(r) }

        // Previsão e nível
        var forecast: Long? = null
        forecastRegex.find(text)?.let { f ->
            val (d, mo, h, mi) = f.destructured
            val cal = Calendar.getInstance()
            cal.set(cal.get(Calendar.YEAR), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), 0)
            cal.set(Calendar.MILLISECOND, 0)
            forecast = cal.timeInMillis
            remove(f.range)
        }
        var difficulty: Difficulty? = null
        for (dif in Difficulty.entries) {
            val i = text.indexOf(dif.emoji)
            if (i >= 0) { difficulty = dif; remove(i until i + dif.emoji.length) }
        }
        Regex("NIVEL\\s*(FACIL|MEDIO|DIFICIL)").find(folded)?.let {
            difficulty = when (it.groupValues[1]) { "FACIL" -> Difficulty.FACIL; "MEDIO" -> Difficulty.MEDIO; else -> Difficulty.DIFICIL }
            remove(it.range)
        }

        // Status
        var status: Status? = null
        Regex("(?<![A-Z])TUNEL(\\s+DE\\s+LIBERACAO)?").find(folded)?.let { status = Status.TUNEL; remove(it.range) }
        if (status == null && !folded.contains("NAO LIBERAD")) {
            Regex("(?<![A-Z])LIBERAD[OA]S?(?![A-Z])").find(folded)?.let { status = Status.LIBERADO; remove(it.range) }
        }
        text.indexOf("✅").takeIf { it >= 0 }?.let { remove(it until it + 1) }

        // Estações (na ordem em que aparecem). Texto entre parênteses logo depois vira a descrição.
        val stationHits = mutableListOf<Pair<Station, IntRange>>()
        for ((s, re) in stationPatterns) {
            for (m in re.findAll(folded)) {
                if (m.range.any { removed[it] }) continue
                if (stationHits.any { (os, r) -> os == s || r.first <= m.range.last && m.range.first <= r.last }) continue
                stationHits += s to m.range
            }
        }
        stationHits.sortBy { it.second.first }
        val repairs = mutableListOf<Repair>()
        for ((s, r) in stationHits) {
            remove(r)
            var note = ""
            val paren = Regex("^\\s*\\(([^)]*)\\)").find(text.substring(r.last + 1))
            if (paren != null) {
                note = paren.groupValues[1].trim()
                remove((r.last + 1)..(r.last + paren.range.last + 1))
            }
            repairs += Repair(s, note)
        }

        val leftover = cleanup(text.filterIndexed { i, _ -> !removed[i] })
        var notes = ""
        if (leftover.count { it.isLetterOrDigit() } >= 3) {
            if (repairs.size == 1 && repairs[0].note.isEmpty()) repairs[0] = repairs[0].copy(note = leftover)
            else notes = leftover
        }

        val anything = repairs.isNotEmpty() || notes.isNotEmpty() || forecast != null || difficulty != null
        return ParsedVehicle(
            vin = vin,
            model = model ?: headerModel,
            status = status ?: if (anything) Status.PROCESSO else null,
            repairs = repairs,
            notes = notes,
            forecast = forecast,
            difficulty = difficulty,
        )
    }

    private fun cleanup(s: String): String =
        s.replace(Regex("\\s*[-–—|+]\\s*([-–—|+]\\s*)+"), " - ")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', '–', '—', '|', '+', ':', ',', ' ')
            .trim()

    fun formatDate(ms: Long): String = dateFmt.format(Date(ms))

    const val LEGEND = "Legenda: 🟡 Fácil | 🟠 Médio | 🔴 Difícil | ⏱ Previsão | ✅ Liberado"

    /** Gera o texto para o WhatsApp, no formato numerado. */
    fun export(
        vehicles: List<Vehicle>,
        title: String,
        includeReleased: Boolean = true,
        includeDate: Boolean = true,
    ): String {
        val list = vehicles
            .filter { includeReleased || it.status != Status.LIBERADO }
            .sortedWith(compareBy<Vehicle> { it.model.ordinal }.then(vinComparator))
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append("*").append(title.trim()).append("*\n")
        if (includeDate) sb.append(SimpleDateFormat("dd/MM/yyyy HH:mm", ptBR).format(Date())).append("\n")
        sb.append(LEGEND).append("\n\n")
        list.forEachIndexed { i, v -> sb.append(lineFor(v, i + 1)).append("\n") }
        val released = list.count { it.status == Status.LIBERADO }
        val tunnel = list.count { it.status == Status.TUNEL }
        sb.append("\n*Total: ${list.size}* | Liberados ✅: $released | Túnel: $tunnel | Em processo: ${list.size - released - tunnel}")
        return sb.toString()
    }

    private fun lineFor(v: Vehicle, n: Int): String {
        val sb = StringBuilder()
        if (v.status != Status.LIBERADO) v.difficulty?.let { sb.append(it.emoji).append(" ") }
        sb.append(n).append(" - ").append(v.vin).append(" - ").append(v.model.short)
        val desc = when (v.status) {
            Status.LIBERADO -> "LIBERADO ✅"
            Status.TUNEL -> "TÚNEL DE LIBERAÇÃO"
            Status.PROCESSO -> {
                val pend = v.pendingRepairs
                if (pend.isEmpty() && v.repairs.isNotEmpty()) "REPAROS CONCLUÍDOS"
                else pend.joinToString(" + ") { r ->
                    r.station.label.uppercase() + if (r.note.isBlank()) "" else " (${r.note})"
                }
            }
        }
        if (desc.isNotEmpty()) sb.append(" - ").append(desc)
        if (v.notes.isNotBlank()) sb.append(" - ").append(v.notes)
        if (v.status != Status.LIBERADO) v.forecast?.let { sb.append(" | ⏱ ").append(formatDate(it)) }
        return sb.toString()
    }
}
