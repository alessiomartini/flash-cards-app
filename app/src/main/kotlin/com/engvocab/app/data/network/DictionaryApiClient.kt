package com.engvocab.app.data.network

import com.engvocab.core.dictionary.DictionaryLookupResult
import com.engvocab.core.dictionary.FreeDictionaryResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** Looks up an English word's definition/example via the free dictionaryapi.dev API (no key required). */
class DictionaryApiClient(private val client: OkHttpClient = HttpClientProvider.client) {

    suspend fun lookup(word: String): DictionaryLookupResult? = withContext(Dispatchers.IO) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return@withContext null

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val request = Request.Builder()
            .url("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                FreeDictionaryResponseParser.parse(body)
            }
        } catch (e: IOException) {
            null
        }
    }
}
