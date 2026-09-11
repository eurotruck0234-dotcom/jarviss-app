package com.jarvis.assistant

import android.content.Context

object Config {
    private const val PREFS = "jarvis_prefs"
    private const val KEY_HOST = "ollama_host"
    private const val DEFAULT_HOST = "http://192.168.1.128:11434"

    fun getHost(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
    }

    fun setHost(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HOST, host).apply()
    }

    // Model routing
    const val MODEL_CHAT = "qwen2.5:1.5b"
    const val MODEL_CHAT_ALT = "gemma3:1b"
    const val MODEL_CODER_FAST = "qwen2.5-coder:1.5b"
    const val MODEL_CODER_SMART = "qwen2.5-coder:3b"
    const val MODEL_REASONING = "deepseek-r1:1.5b"

    val CODE_KEYWORDS = listOf(
        "kod yaz", "kod ", "fonksiyon", "algoritma", "python", "javascript",
        "java ", "kotlin", "html", "css", "sql", "debug", "hata ver",
        "script", "program yaz", "class yaz", "api yaz", "regex"
    )

    val REASONING_KEYWORDS = listOf(
        "mantık", "hesapla", "kanıtla", "ispat", "adım adım düşün", "neden-sonuç"
    )
}
