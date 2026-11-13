package com.massivas.fai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedVehicle(
    val vin: String,
    val model: CarModel?,
    val status: Status?,
    val repairs: List<Repair>,
    val notes: String,
)

/**
 * Lê listas no formato usado no WhatsApp, por exemplo:
 *
 *   CS55 INFINITY - 4
 *   VB002035 - Liberado ✅
 *   VB002207 - Ex Ok - Falta Delta, Pestana FR LH Am.
 *   UNI-T - 2
 *   VB006263 - Polimento
 */
object TextFormat {
    private val vinLine = Regex("^\\s*[•*\\-]?\\s*([A-Za-z0-9]{6,17})\\b\\s*(?:[-–—:]+\\s*(.*))?$")
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    fun parse(text: String): List<ParsedVehicle> {
        val out = mutableListOf<ParsedVehicle>()
        var currentModel: CarModel? = null
        for (raw in text.lines()) {
            val line = raw.trim().trim('*', '_', '~').trim()
            if (line.isEmpty()) continue

            val m = vinLine.matchEntire(line)
            val token = m?.groupValues?.get(1)
            if (m != null && token != null && token.count { it.isDigit() } >= 4) {
                out += parseVehicle(cleanVin(token), m.groupValues[2].trim(), currentModel)
                continue
            }
            val header = CarModel.fromHeader(line)
            if (header != null) {
                currentModel = header
                continue
            }
            // Linha de continuação: junta ao último reparo/observação.
            val last = out.lastOrNull() ?: continue
            val n = normalize(line)
            if (n.startsWith("MASSIVA") || n.startsWith("TOTAL")) continue
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

    private fun parseVehicle(vin: String, rest: String, model: CarModel?): ParsedVehicle {
        val n = normalize(rest)
        if (n.contains("TUNEL")) return ParsedVehicle(vin, model, Status.TUNEL, emptyList(), "")
        if (n.contains("LIBERADO") && !n.contains("NAO LIBERADO")) {
            return ParsedVehicle(vin, model, Status.LIBERADO, emptyList(), "")
        }
        if (rest.isBlank()) return ParsedVehicle(vin, model, null, emptyList(), "")

        val parts = rest.split(Regex("\\s+[-–—]\\s+|\\s*[-–—]\\s+(?=\\S)"), limit = 2)
        val head = normalize(parts[0])
        val note = parts.getOrNull(1)?.trim().orEmpty()
        val stations = detectStations(head)
        return if (stations.isNotEmpty()) {
            val repairs = stations.mapIndexed { i, s -> Repair(s, if (i == 0) note else "") }
            ParsedVehicle(vin, model, Status.PROCESSO, repairs, "")
        } else {
            ParsedVehicle(vin, model, Status.PROCESSO, emptyList(), rest.trim())
        }
    }

    private fun detectStations(text: String): List<Station> {
        val found = mutableListOf<Pair<Int, Station>>()
        for (s in Station.entries) {
            val idx = s.keywords.map { kw ->
                Regex("(^|[^A-Z0-9])" + Regex.escape(normalize(kw)) + "($|[^A-Z0-9])").find(text)?.range?.first ?: -1
            }.filter { it >= 0 }.minOrNull()
            if (idx != null) found += idx to s
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    fun formatDate(ms: Long): String = dateFmt.format(Date(ms))

    /** Gera o texto para compartilhar no WhatsApp. */
    fun export(vehicles: List<Vehicle>, includeReleased: Boolean = true): String {
        val list = vehicles.filter { includeReleased || it.status != Status.LIBERADO }
        val sb = StringBuilder()
        sb.append("MASSIVA FAI - No Processo\n")
        sb.append(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())).append("\n")
        for (model in CarModel.entries) {
            val group = list.filter { it.model == model }.sortedWith(vinComparator)
            if (group.isEmpty()) continue
            sb.append("\n*").append(model.label.uppercase()).append(" (").append(model.code).append(") - ")
                .append(group.size).append("*\n")
            for (v in group) sb.append(lineFor(v)).append("\n")
        }
        val total = list.size
        val released = list.count { it.status == Status.LIBERADO }
        sb.append("\nTotal: $total | Liberados: $released | Pendentes: ${total - released}")
        return sb.toString()
    }

    private fun lineFor(v: Vehicle): String {
        val parts = mutableListOf<String>()
        when (v.status) {
            Status.LIBERADO -> parts += "Liberado ✅"
            Status.TUNEL -> parts += "Túnel de liberação"
            Status.PROCESSO -> {
                val pend = v.pendingRepairs
                if (pend.isEmpty() && v.repairs.isNotEmpty()) parts += "Reparos concluídos"
                pend.forEach { r -> parts += if (r.note.isBlank()) r.station.label else "${r.station.label} - ${r.note}" }
            }
        }
        if (v.notes.isNotBlank()) parts += v.notes
        if (v.status != Status.LIBERADO) {
            v.forecast?.let { parts += "Prev. ${formatDate(it)}" }
            v.difficulty?.let { parts += "Nível ${it.label}" }
        }
        return if (parts.isEmpty()) v.vin else v.vin + " - " + parts.joinToString(" | ")
    }
}
