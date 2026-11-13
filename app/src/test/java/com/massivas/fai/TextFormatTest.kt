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
