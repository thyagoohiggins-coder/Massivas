package com.massivas.fai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFormatTest {
    private val sample = """
        MASSIVA FAI - No Processo

        CS55 INFINITY - 4
        VB002035 - Liberado ✅
        VB000003 - Tunel de liberação 
        VB002207 - Ex Ok - Falta Delta, Pestana FR LH Am.
        VB001433 - Ex OK - PCH, Saia e Parabarro FR RH danificados
        UNI-T - 2
        VB006242 - Tunel de liberação
        VB006263 - Polimento
        C928 - 2
        VB003616 - Liberado ✅
        VB000013 - Ex Ok - Guarnição do QD PT FR Rh rasgado, Faltam batentes capô (O13, P13), Guarnição do capo 
        rasgada (H6)
    """.trimIndent()

    @Test
    fun parsesWhatsappList() {
        val p = TextFormat.parse(sample).associateBy { it.vin }
        assertEquals(8, p.size)
        assertEquals(CarModel.C655, p["VB002035"]!!.model)
        assertEquals(Status.LIBERADO, p["VB002035"]!!.status)
        assertEquals(Status.TUNEL, p["VB000003"]!!.status)
        assertEquals(Station.EX_OK, p["VB002207"]!!.repairs.single().station)
        assertEquals("Falta Delta, Pestana FR LH Am.", p["VB002207"]!!.repairs.single().note)
        assertEquals(CarModel.S202, p["VB006263"]!!.model)
        assertEquals(Station.POLIMENTO, p["VB006263"]!!.repairs.single().station)
        assertEquals(CarModel.C928, p["VB000013"]!!.model)
        assertTrue(p["VB000013"]!!.repairs.single().note.endsWith("rasgada (H6)"))
    }

    @Test
    fun headers() {
        assertEquals(CarModel.T1E_ICE_LOW, CarModel.fromHeader("Tiggo 7 Ice Low - 3"))
        assertEquals(CarModel.T1E_ADAS, CarModel.fromHeader("T1E ADAS - 1"))
        assertEquals(CarModel.T19_ICE_LOW, CarModel.fromHeader("T19 ICE LOW"))
        assertEquals(CarModel.T18, CarModel.fromHeader("TIGGO 8 ICE - 5"))
        assertEquals(null, CarModel.fromHeader("MASSIVA FAI - No Processo"))
    }

    @Test
    fun numericOrder() {
        val vins = listOf("VB006263", "VB000003", "VB002035", "VB000013")
        val sorted = vins.map { Vehicle(it, it, CarModel.C655) }.sortedWith(vinComparator).map { it.vin }
        assertEquals(listOf("VB000003", "VB000013", "VB002035", "VB006263"), sorted)
    }
}

class NumberedFormatTest {
    private val sample = """
        *MASSIVA FAI*

        1 - VB001778 - CS55 - SECANDO(terrão)⚠️
        2 - VB000433 - C928 - PINTURA(cab.04)
        3 - VB004163 - C928 - B. PINTURA
        4 - VB003940 - UNI-T  - PINTURA(avaliar no sol)
        5 - VB004037 - UNI-T - TUNEL DE LIBERAÇÃO
        6 - VB005555 - TIGGO 7 ICE LOW - EX OK - falta friso
        7 - VB005556 - T18 - LIBERADO ✅
    """.trimIndent()

    @Test
    fun parsesModelOnSameLine() {
        val p = TextFormat.parse(sample).associateBy { it.vin }
        assertEquals(7, p.size)
        assertEquals(CarModel.C655, p["VB001778"]!!.model)
        assertEquals("SECANDO(terrão)⚠️", p["VB001778"]!!.notes)
        assertEquals(Status.PROCESSO, p["VB001778"]!!.status)
        assertEquals(CarModel.C928, p["VB000433"]!!.model)
        assertEquals(Repair(Station.PINTURA, "cab.04"), p["VB000433"]!!.repairs.single())
        assertEquals(Repair(Station.PINTURA, ""), p["VB004163"]!!.repairs.single())
        assertEquals(CarModel.S202, p["VB003940"]!!.model)
        assertEquals("avaliar no sol", p["VB003940"]!!.repairs.single().note)
        assertEquals(Status.TUNEL, p["VB004037"]!!.status)
        assertEquals(CarModel.T1E_ICE_LOW, p["VB005555"]!!.model)
        assertEquals(Repair(Station.EX_OK, "falta friso"), p["VB005555"]!!.repairs.single())
        assertEquals(Status.LIBERADO, p["VB005556"]!!.status)
        assertEquals(CarModel.T18, p["VB005556"]!!.model)
    }

    @Test
    fun exportRoundTrip() {
        val vs = listOf(
            Vehicle("1", "VB000433", CarModel.C928, repairs = listOf(Repair(Station.PINTURA, "cab.04"), Repair(Station.POLIMENTO)),
                difficulty = Difficulty.DIFICIL),
            Vehicle("2", "VB003940", CarModel.S202, status = Status.TUNEL),
        )
        val text = TextFormat.export(vs, "Turno B", includeDate = false)
        assertTrue(text.startsWith("*Turno B*"))
        assertTrue(text.contains("1 - VB003940 - UNI-T - TÚNEL DE LIBERAÇÃO"))
        val back = TextFormat.parse(text).associateBy { it.vin }
        assertEquals(listOf(Repair(Station.PINTURA, "cab.04"), Repair(Station.POLIMENTO)), back["VB000433"]!!.repairs)
        assertEquals(Difficulty.DIFICIL, back["VB000433"]!!.difficulty)
        assertEquals(CarModel.C928, back["VB000433"]!!.model)
        assertEquals(Status.TUNEL, back["VB003940"]!!.status)
    }
}
