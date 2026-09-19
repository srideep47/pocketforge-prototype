package com.srideep.webstudio.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Dictation for the chat input box, and nothing more: tap the mic, speak, get text in the
 * field. No playback, no wake word, no voice activity detection — the transcript lands in
 * the text field and the user decides when to send it.
 *
 * Offline recognition is requested where the device supports it, with the online engine as
 * the fallback so dictation still works on phones without a downloaded language pack.
 */
class SpeechToText(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onEndOfSpeech()
    }

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Must be called on the main thread — [SpeechRecognizer] requires it. */
    fun start(listener: Listener) {
        if (!isAvailable) {
            listener.onError("speech recognition is not available on this device")
            return
        }
        stop()

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = listener.onEndOfSpeech()

            override fun onError(error: Int) {
                listener.onError(describe(error))
                release()
            }

            override fun onResults(results: Bundle?) {
                firstResult(results)?.let(listener::onFinal)
                release()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(listener::onPartial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer.startListening(buildIntent())
    }

    fun stop() {
        recognizer?.let { active ->
            runCatching { active.stopListening() }
            runCatching { active.cancel() }
        }
        release()
    }

    private fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Honoured as a hint: the recognizer falls back to its online engine when no
            // on-device language pack is installed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "microphone error"
        SpeechRecognizer.ERROR_CLIENT -> "recognizer client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "no offline language pack, and the network engine is unreachable"
        SpeechRecognizer.ERROR_NO_MATCH -> "did not catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer is busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech detected"
        else -> "speech recognition failed ($error)".also { Log.w(TAG, it) }
    }

    private companion object {
        const val TAG = "SpeechToText"
    }
}
