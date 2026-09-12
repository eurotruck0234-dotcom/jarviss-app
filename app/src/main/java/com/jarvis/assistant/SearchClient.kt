package com.jarvis.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class SearchClient {

    private val snippetPattern: Pattern =
        Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL)
    private val titlePattern: Pattern =
        Pattern.compile("class=\"result__a\"[^>]*>(.*?)</a>", Pattern.DOTALL)

    private fun stripTags(html: String): String {
        return html.replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .trim()
    }

    fun search(query: String, maxResults: Int = 5): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            val titles = mutableListOf<String>()
            val snippets = mutableListOf<String>()

            val tMatcher = titlePattern.matcher(html)
            while (tMatcher.find() && titles.size < maxResults) {
                titles.add(stripTags(tMatcher.group(1)))
            }
            val sMatcher = snippetPattern.matcher(html)
            while (sMatcher.find() && snippets.size < maxResults) {
                snippets.add(stripTags(sMatcher.group(1)))
            }

            val sb = StringBuilder()
            for (i in titles.indices) {
                sb.append("- ${titles[i]}")
                if (i < snippets.size) sb.append(": ${snippets[i]}")
                sb.append("\n")
            }
            sb.toString().ifBlank { "Sonuç bulunamadı." }
        } catch (e: Exception) {
            "Arama sırasında hata oluştu: ${e.message}"
        }
    }

    fun deepResearch(topic: String): String {
        val queries = listOf(topic, "$topic detaylı bilgi", "$topic güncel")
        val sb = StringBuilder()
        for (q in queries) {
            sb.append("### Arama: $q\n")
            sb.append(search(q, 4))
            sb.append("\n")
        }
        return sb.toString()
    }
}
