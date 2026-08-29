package com.engvocab.app.data.network

import com.engvocab.core.dictionary.MyMemoryResponseParser
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** Translates text to Italian via the free MyMemory API (no key required, rate-limited). */
class TranslationApiClient(private val client: OkHttpClient = HttpClientProvider.client) {

    suspend fun translateToItalian(text: String, sourceLanguage: TargetLanguage): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext null

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val request = Request.Builder()
            .url("https://api.mymemory.translated.net/get?q=$encoded&langpair=${sourceLanguage.apiCode}|it")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                MyMemoryResponseParser.parse(body)
            }
        } catch (e: IOException) {
            null
        }
    }
}
