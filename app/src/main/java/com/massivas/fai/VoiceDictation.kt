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
 * Entre as alternativas do reconhecedor, escolhe a que forma mais VINs válidos.
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

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    private val bias = arrayListOf(
        "vírgula", "VB", "CS55", "C928", "CS75", "Uni-T", "Tiggo 7", "Tiggo 7 Ice Low", "Tiggo 5", "Tiggo 8",
        "T1E", "T18", "T19", "S202", "C655", "zero", "Ice Low", "ADAS",
    )

    fun start() {
        if (active) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply { setRecognitionListener(listener) }
        }
        active = true
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        onListening(false)
    }

    fun destroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", bias)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            active = false
            onListening(false)
            onError("Não foi possível usar o microfone: ${e.message}")
        }
    }

    private fun restartSoon(delay: Long = 150) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (active) listen() }, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListening(true)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            val options = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val best = options.withIndex().maxByOrNull { (i, t) -> QuickVin.score(t) * 10 - i }?.value
            onPartial("")
            if (!best.isNullOrBlank()) onFinal(best)
            if (active) restartSoon() else onListening(false)
        }

        override fun onError(error: Int) {
            onPartial("")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (active) restartSoon() else onListening(false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT ->
                    if (active) restartSoon(600) else onListening(false)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false; onListening(false); onError("Permita o uso do microfone para ditar os VINs.")
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    active = false; onListening(false)
                    onError("Sem conexão para o reconhecimento de voz. Verifique a internet ou instale o pacote de voz offline em português.")
                }
                else -> { active = false; onListening(false); onError("Erro no reconhecimento de voz ($error). Toque no microfone de novo.") }
            }
        }
    }
}
