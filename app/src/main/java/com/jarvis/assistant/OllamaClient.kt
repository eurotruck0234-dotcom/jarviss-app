package com.jarvis.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class OllamaClient(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun pickModel(message: String): String {
        val lower = message.lowercase()
        for (kw in Config.CODE_KEYWORDS) {
            if (lower.contains(kw)) {
                return if (message.length > 200) Config.MODEL_CODER_SMART else Config.MODEL_CODER_FAST
            }
        }
        for (kw in Config.REASONING_KEYWORDS) {
            if (lower.contains(kw)) return Config.MODEL_REASONING
        }
        return Config.MODEL_CHAT
    }

    fun chat(
        userMessage: String,
        systemPrompt: String,
        extraContext: String? = null,
        modelOverride: String? = null,
        callback: (String?, String?) -> Unit
    ) {
        val model = modelOverride ?: pickModel(userMessage)
        val host = Config.getHost(context)

        Thread {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                if (!extraContext.isNullOrBlank()) {
                    messages.put(JSONObject().apply {
                        put("role", "system")
                        put("content", "İnternet aramasından bulunan güncel bilgiler:\n$extraContext\n\nBu bilgileri kullanarak kullanıcının sorusunu yanıtla.")
                    })
                }
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })

                val payload = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("stream", false)
                }

                val url = URL("$host/api/chat")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.connectTimeout = 15000
                conn.readTimeout = 120000

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }

                if (code !in 200..299 || responseText == null) {
                    postResult(callback, null, "Sunucu hatası: $code")
                    return@Thread
                }

                val json = JSONObject(responseText)
                val content = json.optJSONObject("message")?.optString("content")
                postResult(callback, content, null)

            } catch (e: Exception) {
                postResult(callback, null, "Bağlantı hatası: ${e.message}")
            }
        }.start()
    }

    private fun postResult(callback: (String?, String?) -> Unit, result: String?, error: String?) {
        mainHandler.post { callback(result, error) }
    }
}
