package com.jarvis.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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

    /**
     * Runs a single search query and returns a short text summary of top results.
     */
    fun search(query: String, maxResults: Int = 5): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ""

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

    /**
     * Deep research: runs the original query plus a couple of related-angle queries,
     * then merges everything into one context block for the LLM.
     */
    fun deepResearch(topic: String): String {
        val queries = listOf(
            topic,
            "$topic detaylı bilgi",
            "$topic güncel"
        )
        val sb = StringBuilder()
        for (q in queries) {
            sb.append("### Arama: $q\n")
            sb.append(search(q, 4))
            sb.append("\n")
        }
        return sb.toString()
    }
}
