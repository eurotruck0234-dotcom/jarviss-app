package com.jarvis.assistant

import android.content.Context
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Picks which local model should answer, based on keywords in the user's message.
     */
    fun pickModel(message: String): String {
        val lower = message.lowercase()
        for (kw in Config.CODE_KEYWORDS) {
            if (lower.contains(kw)) {
                // Longer / more complex looking requests go to the smarter coder model
                return if (message.length > 200) Config.MODEL_CODER_SMART else Config.MODEL_CODER_FAST
            }
        }
        for (kw in Config.REASONING_KEYWORDS) {
            if (lower.contains(kw)) return Config.MODEL_REASONING
        }
        return Config.MODEL_CHAT
    }

    /**
     * Sends a chat request to Ollama. systemPrompt sets the Jarvis persona.
     * extraContext (optional) is injected as background info (e.g. web search results).
     */
    fun chat(
        userMessage: String,
        systemPrompt: String,
        extraContext: String? = null,
        modelOverride: String? = null,
        callback: (String?, String?) -> Unit // (response, error)
    ) {
        val model = modelOverride ?: pickModel(userMessage)
        val host = Config.getHost(context)

        val messages = JSONArray()
        val sys = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }
        messages.put(sys)

        if (!extraContext.isNullOrBlank()) {
            val ctx = JSONObject().apply {
                put("role", "system")
                put("content", "İnternet aramasından bulunan güncel bilgiler:\n$extraContext\n\nBu bilgileri kullanarak kullanıcının sorusunu yanıtla.")
            }
            messages.put(ctx)
        }

        val userMsg = JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        }
        messages.put(userMsg)

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", false)
        }

        val request = Request.Builder()
            .url("$host/api/chat")
            .post(RequestBody.create(JSON, body.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Bağlantı hatası: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null) {
                        callback(null, "Sunucu hatası: ${response.code}")
                        return
                    }
                    val json = JSONObject(bodyStr)
                    val content = json.optJSONObject("message")?.optString("content")
                    callback(content, null)
                } catch (e: Exception) {
                    callback(null, "Ayrıştırma hatası: ${e.message}")
                }
            }
        })
    }
}
