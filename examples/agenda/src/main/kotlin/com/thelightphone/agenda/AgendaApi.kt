package com.thelightphone.agenda

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal class AgendaApi {
    private val client = HttpClient(OkHttp)

    // "webcal://" is just a UI convention meaning "subscribe to this feed" - the feed itself
    // is always served over plain http(s), so rewrite it before fetching.
    private fun String.normalizedIcsUrl(): String =
        if (startsWith("webcal://", ignoreCase = true)) "https://" + removePrefix("webcal://") else this

    suspend fun fetchIcs(url: String): Result<String> = runCatching {
        val response = client.get(url.normalizedIcsUrl())
        if (!response.status.isSuccess()) {
            throw IllegalStateException("ICS fetch HTTP ${response.status.value}")
        }
        response.bodyAsText()
    }

    fun close() {
        client.close()
    }
}
