package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var inputField: android.widget.EditText
    private lateinit var modeIndicator: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val ollama by lazy { OllamaClient(this) }
    private val search by lazy { SearchClient() }

    private val SYSTEM_PROMPT = """
        Sen Jarvis'sin: soğukkanlı, kısa ve net cevaplar veren, teknik konularda
        yetkin bir yapay zeka asistanısın. Türkçe konuşuyorsun. Gereksiz uzatma,
        doğrudan ve yardımsever ol.
    """.trimIndent()

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatContainer = findViewById(R.id.chatContainer)
        chatScroll = findViewById(R.id.chatScroll)
        inputField = findViewById(R.id.inputField)
        modeIndicator = findViewById(R.id.modeIndicator)

        findViewById<View>(R.id.sendButton).setOnClickListener { onSend() }
        findViewById<View>(R.id.micButton).setOnClickListener { startVoiceInput() }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("tr", "TR")
            }
        }

        addBubble("Merhaba, ben Jarvis. Nasıl yardımcı olabilirim?", fromUser = false)

        requestRuntimePermissions()
        maybeAskOverlayPermission()
        startWakeService()
    }

    // ---------- Permissions ----------

    private fun requestRuntimePermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102
                )
            }
        }
    }

    private fun maybeAskOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startWakeService() {
        val intent = Intent(this, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ---------- Chat UI ----------

    private fun addBubble(text: String, fromUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.jarvis_text))
            textSize = 15f
            setBackgroundResource(if (fromUser) R.drawable.bubble_user else R.drawable.bubble_ai)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 8, 0, 8)
        params.gravity = if (fromUser) Gravity.END else Gravity.START
        bubble.layoutParams = params

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = if (fromUser) Gravity.END else Gravity.START
            addView(bubble)
        }

        chatContainer.addView(row)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onSend() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        inputField.setText("")
        handleUserMessage(text)
    }

    // ---------- Command routing ----------

    private fun handleUserMessage(raw: String) {
        addBubble(raw, fromUser = true)

        val lower = raw.lowercase().trim()

        // 1) Deep research trigger
        val deepPrefixes = listOf("/derin", "#derinarastirma", "#derin araştırma", "@derinarastirma", "@derin araştırma")
        val deepHit = deepPrefixes.firstOrNull { lower.startsWith(it) }
        if (deepHit != null || lower.contains("derin araştırma")) {
            val topic = raw.replaceFirst(Regex("(?i)^(/derin|#derinarastirma|#derin araştırma|@derinarastirma|@derin araştırma)"), "").trim()
                .ifBlank { raw }
            modeIndicator.text = "🔎 DERİN ARAŞTIRMA MODU"
            runInBackground {
                val context = search.deepResearch(topic)
                askOllama(topic, context)
            }
            return
        }

        // 2) Normal web search trigger
        if (lower.startsWith("araştır") || lower.startsWith("ara:") || lower.contains("internetten bak")) {
            modeIndicator.text = "🔎 İNTERNET ARAMASI"
            runInBackground {
                val context = search.search(raw)
                askOllama(raw, context)
            }
            return
        }

        // 3) Call a contact
        val callTarget = ContactCaller.extractCallTarget(lower)
        if (callTarget != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                addBubble("Arama yapabilmem için rehber ve telefon izinlerini vermen gerekiyor.", fromUser = false)
                requestRuntimePermissions()
                return
            }
            val match = ContactCaller.findContact(this, callTarget)
            if (match != null) {
                addBubble("${match.name} aranıyor...", fromUser = false)
                ContactCaller.call(this, match.number)
                speak("${match.name} aranıyor")
            } else {
                addBubble("Rehberde \"$callTarget\" ile eşleşen bir kişi bulamadım.", fromUser = false)
            }
            return
        }

        // 4) Open an app / device command
        val appTarget = AppLauncher.extractAppTarget(lower)
        if (appTarget != null) {
            val opened = AppLauncher.openApp(this, appTarget)
            if (opened) {
                addBubble("$appTarget açılıyor...", fromUser = false)
                speak("Açılıyor")
            } else {
                addBubble("$appTarget için yüklü bir uygulama bulamadım.", fromUser = false)
            }
            return
        }

        // 5) Default: normal chat (model auto-routes to coder/reasoning if needed)
        modeIndicator.text = ""
        askOllama(raw, null)
    }

    private fun askOllama(userMessage: String, context: String?) {
        ollama.chat(userMessage, SYSTEM_PROMPT, context) { response, error ->
            runOnUiThread {
                modeIndicator.text = ""
                if (response != null) {
                    addBubble(response, fromUser = false)
                    speak(response)
                } else {
                    addBubble("Hata: $error", fromUser = false)
                }
            }
        }
    }

    private fun runInBackground(block: () -> Unit) {
        Thread { block() }.start()
    }

    // ---------- Voice ----------

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestRuntimePermissions()
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    handleUserMessage(text)
                }
            }
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Ses tanıma hatası", Toast.LENGTH_SHORT).show()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
