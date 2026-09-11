package com.jarvis.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

/**
 * Keeps a lightweight speech-recognition loop running in the background so the
 * phone reacts when it hears "jarvis". This uses Android's built-in recognizer
 * restarted in a loop (no external wake-word account/service needed).
 *
 * Note: this is more battery-hungry than a dedicated low-power wake-word chip
 * (which this budget phone doesn't have), but works fully offline-capable and
 * needs zero third-party sign-ups.
 */
class WakeService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    companion object {
        const val CHANNEL_ID = "jarvis_wake_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        startListenLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis Dinleme", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis dinliyor")
            .setContentText("\"Jarvis\" demeni bekliyorum")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun startListenLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        listening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase() ?: ""
                if (text.contains("jarvis")) {
                    launchMainActivity()
                }
                restartListening()
            }
            override fun onError(error: Int) {
                restartListening()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listenOnce()
    }

    private fun listenOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            restartListening()
        }
    }

    private fun restartListening() {
        if (!listening) return
        handler.postDelayed({ listenOnce() }, 400)
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        super.onDestroy()
    }
}
