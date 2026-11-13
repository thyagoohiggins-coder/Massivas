package com.massivas.fai

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Modelos de veículos, na ordem em que aparecem na lista. `short` é o nome usado no WhatsApp. */
enum class CarModel(val code: String, val label: String, val short: String, val aliases: List<String>) {
    S202("S202", "Uni-T", "UNI-T", listOf("S202", "UNI-T", "UNI T", "UNIT")),
    C655("C655", "CS55", "CS55", listOf("C655", "CS55", "CS 55", "CS55 INFINITY", "CS55 PLUS")),
    C928("C928", "CS75", "C928", listOf("C928", "CS75", "CS 75", "CS75 PLUS")),
    T1E_ADAS("T1E ADAS", "Tiggo 7", "T1E ADAS", listOf("T1E ADAS", "T1E", "TIGGO 7", "TIGGO7", "TIGGO 7 ADAS", "TIGGO7 ADAS")),
    T1E_ICE_LOW("T1E ICE LOW", "Tiggo 7 Ice Low", "T1E ICE LOW", listOf("T1E ICE LOW", "T1E ICE", "T1E LOW", "TIGGO 7 ICE LOW", "TIGGO 7 ICE", "TIGGO7 ICE LOW", "TIGGO7 ICE")),
    T19_ICE_LOW("T19 ICE LOW", "Tiggo 5 Ice Low", "T19", listOf("T19 ICE LOW", "T19 ICE", "T19", "TIGGO 5", "TIGGO5", "TIGGO 5X", "TIGGO 5 ICE LOW", "TIGGO 5 ICE")),
    T18("T18", "Tiggo 8 Ice", "T18", listOf("T18", "TIGGO 8", "TIGGO8", "TIGGO 8 ICE"));

    companion object {
        /** Reconhece o modelo num cabeçalho como "CS55 INFINITY - 4" ou "UNI-T - 2". */
        fun fromHeader(line: String): CarModel? {
            val n = normalize(line).replace(Regex("[-–—:]\\s*\\d+\\s*$"), "").trim()
            if (n.isEmpty()) return null
            var best: CarModel? = null
            var bestLen = 0
            for (m in entries) for (a in m.aliases) {
                val alias = normalize(a)
                val matches = n == alias || n.startsWith("$alias ") || n.startsWith("$alias-") || n.startsWith("$alias(")
                if (matches && alias.length > bestLen) {
                    best = m; bestLen = alias.length
                }
            }
            return best
        }
    }
}

enum class CarColor(val label: String, val swatch: Color) {
    AZUL("Azul", Color(0xFF1565C0)),
    CINZA("Cinza", Color(0xFF9E9E9E)),
    PRETO("Preto", Color(0xFF111111)),
    BRANCO("Branco", Color(0xFFF5F5F5));
}

/** Estações de reparo — cada uma com sua cor. */
enum class Station(val label: String, val color: Color, val keywords: List<String>) {
    PINTURA("Pintura", Color(0xFF1E88E5), listOf("PINTURA", "PINT")),
    POLIMENTO("Polimento", Color(0xFF8E24AA), listOf("POLIMENTO", "POLIR")),
    EX_OK("EX-OK", Color(0xFF2E7D32), listOf("EX OK", "EX-OK", "EXOK", "EX.OK", "EX - OK")),
    SHOWER("Reparo de Shower", Color(0xFF00ACC1), listOf("REPARO DE SHOWER", "SHOWER")),
    PISTA("Reparo de Pista", Color(0xFF6D4C41), listOf("REPARO DE PISTA", "PISTA")),
    FUNILARIA("Funilaria", Color(0xFF607D8B), listOf("FUNILARIA", "FUNIL")),
    ELETRICA("Elétrica", Color(0xFF3949AB), listOf("ELETRICA", "ELETRICO")),
    ALINHAMENTO("Alinhamento", Color(0xFF00897B), listOf("ALINHAMENTO", "ALINH")),
    TESTE_ROLO("Teste de Rolo", Color(0xFFD81B60), listOf("TESTE DE ROLO", "ROLO")),
    UNDERBODY("Underbody", Color(0xFF827717), listOf("UNDERBODY", "UNDER BODY", "UNDER")),
    ADAS("Adas", Color(0xFF7C4DFF), listOf("CALIBRACAO ADAS", "ADAS"));
}

enum class Status(val label: String, val color: Color) {
    PROCESSO("No processo", Color(0xFF455A64)),
    TUNEL("Túnel de liberação", Color(0xFF0277BD)),
    LIBERADO("Liberado ✅", Color(0xFF2E7D32));
}

/** Probabilidade de liberação. */
enum class Difficulty(val label: String, val color: Color, val onColor: Color, val emoji: String) {
    FACIL("Fácil", Color(0xFFFBC02D), Color(0xFF212121), "🟡"),
    MEDIO("Médio", Color(0xFFFB8C00), Color.White, "🟠"),
    DIFICIL("Difícil", Color(0xFFE53935), Color.White, "🔴");
}

data class Repair(val station: Station, val note: String = "", val done: Boolean = false)

data class Vehicle(
    val id: String,
    val vin: String,
    val model: CarModel,
    val color: CarColor? = null,
    val status: Status = Status.PROCESSO,
    val repairs: List<Repair> = emptyList(),
    val forecast: Long? = null,
    val difficulty: Difficulty? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val pendingRepairs: List<Repair> get() = repairs.filter { !it.done }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("vin", vin)
        put("model", model.name)
        color?.let { put("color", it.name) }
        put("status", status.name)
        put("repairs", JSONArray().apply {
            repairs.forEach {
                put(JSONObject().put("station", it.station.name).put("note", it.note).put("done", it.done))
            }
        })
        forecast?.let { put("forecast", it) }
        difficulty?.let { put("difficulty", it.name) }
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Vehicle? {
            val model = enumOrNull<CarModel>(o.optString("model")) ?: return null
            val arr = o.optJSONArray("repairs") ?: JSONArray()
            val repairs = (0 until arr.length()).mapNotNull { i ->
                val r = arr.getJSONObject(i)
                enumOrNull<Station>(r.optString("station"))?.let {
                    Repair(it, r.optString("note"), r.optBoolean("done"))
                }
            }
            return Vehicle(
                id = o.optString("id"),
                vin = o.optString("vin"),
                model = model,
                color = enumOrNull<CarColor>(o.optString("color")),
                status = enumOrNull<Status>(o.optString("status")) ?: Status.PROCESSO,
                repairs = repairs,
                forecast = if (o.has("forecast")) o.getLong("forecast") else null,
                difficulty = enumOrNull<Difficulty>(o.optString("difficulty")),
                notes = o.optString("notes"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }
}

inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }

/** Maiúsculas e sem acentos, para comparar textos. */
fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()
        .replace(Regex("\\s+"), " ")
        .trim()

/** Igual a [normalize], mas mantém o tamanho do texto (posição a posição). */
fun fold(s: String): String = buildString(s.length) {
    for (c in s) {
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
        append(base.uppercaseChar())
    }
}

fun cleanVin(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

/** Chave de ordenação numérica do VIN (usa o último bloco de dígitos, ex.: VB002035 → 2035). */
fun vinSortKey(vin: String): Long =
    Regex("\\d+").findAll(vin).lastOrNull()?.value?.take(18)?.toLongOrNull() ?: Long.MAX_VALUE

val vinComparator: Comparator<Vehicle> =
    compareBy<Vehicle> { vinSortKey(it.vin) }.thenBy { it.vin }
