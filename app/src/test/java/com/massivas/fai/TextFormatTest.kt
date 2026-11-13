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
                difficulty = Difficulty.DIFICIL, color = CarColor.AZUL),
            Vehicle("2", "VB003940", CarModel.S202, status = Status.TUNEL),
        )
        val text = TextFormat.export(vs, "Turno B", includeDate = false)
        assertTrue(text.startsWith("*Turno B*"))
        assertTrue(text.contains("1 - VB003940 - UNI-T - TÚNEL DE LIBERAÇÃO"))
        val back = TextFormat.parse(text).associateBy { it.vin }
        assertEquals(listOf(Repair(Station.PINTURA, "cab.04"), Repair(Station.POLIMENTO)), back["VB000433"]!!.repairs)
        assertTrue(!text.contains("🔴"))
        assertTrue(text.contains("VB000433 - C928 - AZUL - PINTURA (cab.04) + POLIMENTO"))
        assertTrue(text.contains("Previsão de reparos"))
        assertEquals(CarColor.AZUL, back["VB000433"]!!.color)
        assertEquals(CarModel.C928, back["VB000433"]!!.model)
        assertEquals(Status.TUNEL, back["VB003940"]!!.status)
    }
}

class QuickVinTest {
    @Test
    fun typedList() {
        val r = QuickVin.parse("VB001778 cs55, VB000433 C928, vb004163, 3940 uni-t")
        assertEquals(listOf("VB001778", "VB000433", "VB004163", "VB003940").take(3), r.entries.take(3).map { it.vin })
        assertEquals(CarModel.C655, r.entries[0].model)
        assertEquals(CarModel.C928, r.entries[1].model)
        assertEquals(null, r.entries[2].model)
        assertEquals("VB3940", r.entries[3].vin)
        assertTrue(r.entries[3].prefixAdded)
        assertEquals(CarModel.S202, r.entries[3].model)
    }

    @Test
    fun spokenDigits() {
        val r = QuickVin.parse("vê bê zero zero um sete sete oito cs cinquenta e cinco vírgula VB zero zero zero quatro três três c novecentos e vinte e oito vírgula")
        assertEquals(2, r.entries.size)
        assertEquals("VB001778", r.entries[0].vin)
        assertEquals(CarModel.C655, r.entries[0].model)
        assertEquals("VB000433", r.entries[1].vin)
        assertEquals(CarModel.C928, r.entries[1].model)
    }

    @Test
    fun spokenComposite() {
        val r = QuickVin.parse("VB zero zero mil setecentos e setenta e oito tiggo sete ice low, VB zero zero dois mil e trinta e cinco T dezoito, VB 006263 uni tê")
        assertEquals("VB001778", r.entries[0].vin)
        assertEquals(CarModel.T1E_ICE_LOW, r.entries[0].model)
        assertEquals("VB002035", r.entries[1].vin)
        assertEquals(CarModel.T18, r.entries[1].model)
        assertEquals("VB006263", r.entries[2].vin)
        assertEquals(CarModel.S202, r.entries[2].model)
    }

    @Test
    fun normalize() {
        assertEquals("VB001778 CS55, VB000433", QuickVin.normalizeSpeech("v b 001778 CS 55 vírgula vb 000433"))
    }
}

class VoiceFixesTest {
    @Test
    fun misheardPrefix() {
        val r = QuickVin.parse("BB405757, VB1257, BEBE1299, bebê 001300", "VB", forcePrefix = true)
        assertEquals(listOf("VB405757", "VB1257", "VB1299", "VB001300"), r.entries.map { it.vin })
        assertTrue(r.entries[0].prefixFixed)
    }

    @Test
    fun confusableWithoutForce() {
        val r = QuickVin.parse("BB405757, DV001234", "VB")
        assertEquals(listOf("VB405757", "VB001234"), r.entries.map { it.vin })
    }

    @Test
    fun pauseDoesNotSplitVin() {
        // três trechos falados com pausa; só "vírgula" separa
        var text = ""
        for (said in listOf("vê bê zero zero", "um dois cinco sete", "CS 55 vírgula", "zero zero um três")) {
            text = QuickVin.tidy(text.trimEnd() + " " + QuickVin.convertSpeech(said), "VB", true, 6)
        }
        assertEquals("VB001257 CS55, VB0013", text)
        val r = QuickVin.parse(text, "VB", true, 6)
        assertEquals(listOf("VB001257", "VB0013"), r.entries.map { it.vin })
        assertEquals(CarModel.C655, r.entries[0].model)
    }

    @Test
    fun sequenceWithoutComma() {
        // como no print: VINs falados em sequência, sem "vírgula", alguns ouvidos como BB
        val r = QuickVin.parse("VB003090 VB003940 BB004645 VB004987 BB005706 BB005821 BB005964 005982", "VB", true, 6)
        assertEquals(
            listOf("VB003090", "VB003940", "VB004645", "VB004987", "VB005706", "VB005821", "VB005964", "VB005982"),
            r.entries.map { it.vin },
        )
        assertEquals("VB003090, VB003940, VB004645, VB004987, VB005706, VB005821, VB005964, VB005982", r.canonical)
    }

    @Test
    fun sequenceWithModelsAndPauses() {
        var text = ""
        for (said in listOf("vê bê zero zero três", "zero nove zero C 928 bebê", "zero zero", "três nove quatro zero uni tê", "005964005982")) {
            text = QuickVin.tidy(text.trimEnd() + " " + QuickVin.convertSpeech(said), "VB", true, 6)
        }
        val r = QuickVin.parse(text, "VB", true, 6)
        assertEquals(listOf("VB003090", "VB003940", "VB005964", "VB005982"), r.entries.map { it.vin })
        assertEquals(CarModel.C928, r.entries[0].model)
        assertEquals(CarModel.S202, r.entries[1].model)
    }
}

class BolsaoTest {
    @Test
    fun parsesBolsaoAndNewStations() {
        val p = TextFormat.parse("""
            1 - VB000111 - CS55 - BOLSÃO PISTA
            2 - VB000222 - C928 - Bolsão do Refeitório - PINTURA (risco)
            3 - VB000333 - UNI-T - VAGA DE SHOWER
            4 - VB000444 - T18 - ILHA SING OFF (faltando etiqueta)
            5 - VB000555 - T18 - Heliponto
        """.trimIndent()).associateBy { it.vin }
        assertEquals(Bolsao.PISTA, p["VB000111"]!!.bolsao)
        assertEquals(Status.BOLSAO, p["VB000111"]!!.status)
        assertEquals(Status.BOLSAO, p["VB000222"]!!.status)
        assertEquals(Status.PROCESSO, p["VB000333"]!!.status)
        assertTrue(p["VB000111"]!!.repairs.isEmpty())
        assertEquals(Bolsao.REFEITORIO, p["VB000222"]!!.bolsao)
        assertEquals(Repair(Station.PINTURA, "risco"), p["VB000222"]!!.repairs.single())
        assertEquals(Station.AVALIACAO_QA, p["VB000333"]!!.repairs.single().station)
        assertEquals(Repair(Station.ILHA_SING_OFF, "faltando etiqueta"), p["VB000444"]!!.repairs.single())
        assertEquals(Bolsao.HELIPONTO, p["VB000555"]!!.bolsao)
    }

    @Test
    fun exportsBolsao() {
        val text = TextFormat.export(listOf(Vehicle("1", "VB000111", CarModel.C655, status = Status.BOLSAO, bolsao = Bolsao.HELIPONTO)), "X", includeDate = false)
        assertTrue(text.contains("1 - VB000111 - CS55 - BOLSÃO HELIPONTO"))
        assertTrue(text.contains("Bolsão: 1"))
        assertEquals(Bolsao.HELIPONTO, TextFormat.parse(text).single().bolsao)
    }
}

class TwoPrefixesTest {
    @Test
    fun picksClosestPrefix() {
        val r = QuickVin.parse("BB004645 VC001234 BC000777 003333 VB005555 006666", listOf("VB", "VC"), forcePrefix = true)
        assertEquals(
            listOf("VB004645", "VC001234", "VC000777", "VC003333", "VB005555", "VB006666"),
            r.entries.map { it.vin },
        )
    }

    @Test
    fun differentLetters() {
        val r = QuickVin.parse("LV001111, VB002222, LB003333", listOf("VB", "LV"), forcePrefix = true)
        assertEquals(listOf("LV001111", "VB002222", "LV003333"), r.entries.map { it.vin })
    }
}

class CompareTest {
    @Test
    fun diffLists() {
        val d = VinCompare.compare(listOf("VB000003", "VB000068", "VB000386"), listOf("VB000068", "VB000594", "VB000003"))
        assertEquals(listOf("VB000386"), d.onlyMine)
        assertEquals(listOf("VB000003", "VB000068"), d.both)
        assertEquals(listOf("VB000594"), d.onlyOther)
    }

    @Test
    fun readsWhatsappOrCommaList() {
        assertEquals(listOf("VB001778", "VB000433"), VinCompare.vinsFromText("1 - VB001778 - CS55 - PINTURA\n2 - VB000433 - C928", listOf("VB"), true))
        assertEquals(listOf("VB001778", "VB000433"), VinCompare.vinsFromText("vb001778, 000433", listOf("VB"), true))
    }
}

class SpokenColorTest {
    @Test
    fun detectsColor() {
        val r = QuickVin.parse("VB000013 C928, VB001433 CS55 azul, VB003940 UNI-T branco, VB004000 preta T18, VB005000 cinza", "VB", true)
        val byVin = r.entries.associateBy { it.vin }
        assertEquals(null, byVin["VB000013"]!!.color)
        assertEquals(CarColor.AZUL, byVin["VB001433"]!!.color)
        assertEquals(CarModel.C655, byVin["VB001433"]!!.model)
        assertEquals(CarColor.BRANCO, byVin["VB003940"]!!.color)
        assertEquals(CarModel.S202, byVin["VB003940"]!!.model)
        assertEquals(CarColor.PRETO, byVin["VB004000"]!!.color)
        assertEquals(CarModel.T18, byVin["VB004000"]!!.model)
        assertEquals(CarColor.CINZA, byVin["VB005000"]!!.color)
        assertTrue(r.canonical.contains("VB003940 UNI-T BRANCO"))
    }

    @Test
    fun spokenWithoutComma() {
        var text = ""
        for (said in listOf("VB zero zero três nove quatro zero uni tê branco", "VB zero zero quatro seis quatro cinco azul")) {
            text = QuickVin.tidy(text.trimEnd() + " " + QuickVin.convertSpeech(said), "VB", true, 6)
        }
        val r = QuickVin.parse(text, "VB", true, 6)
        assertEquals(listOf("VB003940", "VB004645"), r.entries.map { it.vin })
        assertEquals(CarColor.BRANCO, r.entries[0].color)
        assertEquals(CarColor.AZUL, r.entries[1].color)
    }
}

class SpokenStationTest {
    @Test
    fun detectsStationsBolsaoAndStatus() {
        var text = ""
        for (said in listOf(
            "VB zero zero três nove quatro zero uni tê branco pintura e polimento",
            "VB zero zero quatro seis quatro cinco ex ok",
            "VB zero zero cinco sete zero seis teste de rolo",
            "VB zero zero cinco oito dois um bolsão heliponto",
            "VB zero zero cinco nove seis quatro tiggo sete adas túnel",
            "VB zero zero seis zero zero zero avaliação QA ilha sing off",
        )) {
            text = QuickVin.tidy(text.trimEnd() + " " + QuickVin.convertSpeech(said), "VB", true, 6)
        }
        val r = QuickVin.parse(text, "VB", true, 6).entries.associateBy { it.vin }
        assertEquals(listOf(Station.PINTURA, Station.POLIMENTO), r["VB003940"]!!.stations)
        assertEquals(CarColor.BRANCO, r["VB003940"]!!.color)
        assertEquals(CarModel.S202, r["VB003940"]!!.model)
        assertEquals(listOf(Station.EX_OK), r["VB004645"]!!.stations)
        assertEquals(listOf(Station.TESTE_ROLO), r["VB005706"]!!.stations)
        assertEquals(Bolsao.HELIPONTO, r["VB005821"]!!.bolsao)
        assertTrue(r["VB005821"]!!.stations.isEmpty())
        assertEquals(CarModel.T1E_ADAS, r["VB005964"]!!.model)
        assertTrue(r["VB005964"]!!.stations.isEmpty())
        assertEquals(Status.TUNEL, r["VB005964"]!!.status)
        assertEquals(listOf(Station.AVALIACAO_QA, Station.ILHA_SING_OFF), r["VB006000"]!!.stations)
    }
}
