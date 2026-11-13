package com.massivas.fai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Ditado contínuo em português: continua ouvindo depois de cada frase até o usuário tocar em Parar.
 *
 * O reconhecedor do Google costuma "travar" depois de um silêncio quando é reaproveitado, então:
 * - a cada nova escuta ele é recriado do zero;
 * - um vigia reinicia se ficar alguns segundos sem resposta;
 * - o que já tinha sido ouvido (resultado parcial) não é perdido num reinício.
 */
class VoiceDictation(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    /** Cada escuta tem um número; callbacks de escutas antigas são ignorados. */
    private var session = 0
    private var lastPartial = ""
    private var failures = 0

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Prefixos escolhidos na tela, enviados como dica ao reconhecedor. */
    var extraBias: List<String> = emptyList()

    private val baseBias = listOf(
        "vírgula", "VB", "CS55", "C928", "CS75", "Uni-T", "Tiggo 7", "Tiggo 7 Ice Low", "Tiggo 5", "Tiggo 8",
        "T1E", "T18", "T19", "S202", "C655", "zero", "Ice Low", "ADAS",
        "pintura", "polimento", "ex ok", "funilaria", "elétrica", "alinhamento", "teste de rolo", "underbody",
        "shower", "pista", "ilha sing off", "avaliação QA", "bolsão", "heliponto", "refeitório", "túnel", "liberado",
        "azul", "cinza", "preto", "branco",
    )
    private val bias: ArrayList<String> get() = ArrayList(baseBias + extraBias)

    fun start() {
        if (active) return
        active = true
        failures = 0
        onListening(true)
        restart(0)
    }

    fun stop() {
        active = false
        session++
        handler.removeCallbacksAndMessages(null)
        deliverPartial()
        try { recognizer?.stopListening() } catch (_: Exception) {}
        destroyRecognizer()
        onListening(false)
    }

    fun destroy() {
        active = false
        session++
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    /** Entrega o que já foi ouvido (se a escuta for interrompida no meio). */
    private fun deliverPartial() {
        val p = lastPartial
        lastPartial = ""
        onPartial("")
        if (p.isNotBlank()) onFinal(p)
    }

    /** Recria o reconhecedor e volta a ouvir. */
    private fun restart(delay: Long) {
        session++
        val my = session
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        handler.postDelayed({ if (active && my == session) listen(my) }, delay)
    }

    /** Se nada acontecer por [ms], considera travado e reinicia. */
    private fun watchdog(my: Int, ms: Long) {
        handler.removeCallbacksAndMessages(WATCHDOG)
        handler.postAtTime({
            if (active && my == session) {
                deliverPartial()
                restart(100)
            }
        }, WATCHDOG, android.os.SystemClock.uptimeMillis() + ms)
    }

    private fun listen(my: Int) {
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } catch (e: Exception) {
            fail("Não foi possível usar o reconhecimento de voz: ${e.message}")
            return
        }
        recognizer = r
        r.setRecognitionListener(Listener(my))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", bias)
        }
        try {
            r.startListening(intent)
            watchdog(my, 10_000) // tem que ficar pronto/ouvir algo em até 10 s
        } catch (e: Exception) {
            retryAfterFailure()
        }
    }

    private fun retryAfterFailure() {
        failures++
        if (failures > 8) {
            fail("O reconhecimento de voz parou de responder. Toque no microfone de novo.")
        } else {
            restart(minOf(300L * failures, 1500L))
        }
    }

    private fun fail(msg: String) {
        active = false
        session++
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        onListening(false)
        onError(msg)
    }

    private inner class Listener(private val my: Int) : RecognitionListener {
        private fun current() = active && my == session

        override fun onReadyForSpeech(params: Bundle?) {
            if (!current()) return
            failures = 0
            watchdog(my, 15_000)
        }

        override fun onBeginningOfSpeech() {
            if (current()) watchdog(my, 15_000)
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onEndOfSpeech() {
            // depois que a fala termina, o resultado tem que chegar logo
            if (current()) watchdog(my, 6_000)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (p.isNotBlank()) {
                lastPartial = p
                onPartial(p)
            }
            watchdog(my, 8_000)
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            val options = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                .filter { it.isNotBlank() }
            val best = options.withIndex().maxByOrNull { (i, t) -> QuickVin.score(t) * 10 - i }?.value
            lastPartial = ""
            onPartial("")
            if (!best.isNullOrBlank()) onFinal(best)
            failures = 0
            restart(150)
        }

        override fun onError(error: Int) {
            if (!current()) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    lastPartial = ""
                    fail("Permita o uso do microfone para ditar os VINs.")
                }
                // silêncio ou nada entendido: não é falha, só volta a ouvir
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    deliverPartial()
                    restart(150)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    deliverPartial()
                    failures++
                    if (failures > 4) {
                        fail("Sem conexão para o reconhecimento de voz. Verifique a internet ou instale o pacote de voz offline em português.")
                    } else restart(1000)
                }
                else -> {
                    // ocupado, cliente, servidor desconectado etc.: recria e tenta de novo
                    deliverPartial()
                    retryAfterFailure()
                }
            }
        }
    }

    private companion object {
        val WATCHDOG = Any()
    }
}
