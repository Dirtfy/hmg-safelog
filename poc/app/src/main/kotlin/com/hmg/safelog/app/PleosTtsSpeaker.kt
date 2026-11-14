package com.hmg.safelog.app

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.hmg.safelog.core.Speaker
import java.util.Locale

/** Pleos TextToSpeech (HYBRID). Korean by default (Locale.KOREA = ko-KR). */
class PleosTtsSpeaker(context: Context) : Speaker {
    private val tts = TextToSpeech(context, Mode.HYBRID)

    @Volatile
    private var ready = false
    val isReady: Boolean get() = ready

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                ready = true
                tts.setLocale(Locale.KOREA)
            }

            override fun onFailure(error: ErrorType) {
                Log.w("SafeLog", "TTS init failed: $error")
            }
        })
    }

    override fun speak(text: String, languageTag: String) {
        if (!ready) {
            Log.w("SafeLog", "TTS not ready, dropped: $text")
            return
        }
        tts.setLocale(Locale.forLanguageTag(languageTag))
        tts.speak(text, false)
    }

    fun release() = tts.release()
}
