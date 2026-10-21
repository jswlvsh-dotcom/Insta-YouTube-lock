package com.jaivish.gowork.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.jaivish.gowork.engine.LockKind
import java.util.Locale

/** Speaks the reminder out loud ("Jaivish, go do your work."). */
class Voice(context: Context) : TextToSpeech.OnInitListener {

    private var ready = false
    private var queued: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.setSpeechRate(0.95f)
        ready = true
        queued?.let { speak(it) }
        queued = null
    }

    fun speak(text: String) {
        if (!ready) {
            queued = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gowork-${System.nanoTime()}")
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            // Engine already gone.
        }
    }
}

object Haptics {
    fun buzz(context: Context, kind: LockKind) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val pattern = when (kind) {
            LockKind.OPEN_PAUSE -> longArrayOf(0, 40)
            LockKind.LIMIT, LockKind.SHORT_FORM -> longArrayOf(0, 90, 70, 90, 70, 180)
            else -> longArrayOf(0, 70, 90, 150)
        }
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            // Some OEMs throw when vibration is disabled system-wide.
        }
    }
}

/**
 * Takes transient audio focus while a lock is up, so YouTube / Reels pause behind it
 * and resume when the user taps "Continue".
 */
class AudioFocusHolder(context: Context) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    fun pauseOthers() {
        val manager = audio ?: return
        if (request != null) return
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        val granted = try {
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) {
            false
        }
        if (granted) request = focusRequest
    }

    fun release() {
        val focusRequest = request ?: return
        request = null
        try {
            audio?.abandonAudioFocusRequest(focusRequest)
        } catch (e: Exception) {
            // Nothing to undo.
        }
    }
}
