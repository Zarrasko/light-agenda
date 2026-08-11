package com.thelightphone.kaginews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val NEWS_API_BASE = "https://kite.kagi.com"

internal class NewsApi {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun fetchIndex(): Result<List<NewsCategory>> = runCatching {
        val response = client.get("$NEWS_API_BASE/kite.json")

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Kite index HTTP ${response.status.value}: $body")
        }

        val index: NewsIndex = response.body()
        index.categories.ifEmpty { throw IllegalStateException("No categories returned.") }
    }

    suspend fun fetchCategory(file: String): Result<List<NewsCluster>> = runCatching {
        val response = client.get("$NEWS_API_BASE/$file")

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Kite category HTTP ${response.status.value}: $body")
        }

        val feed: CategoryFeed = response.body()
        // Kite already orders clusters by rank; keep it to a handful of top stories per
        // category rather than an endless feed.
        feed.clusters.take(TOP_STORIES_PER_CATEGORY)
    }

    fun close() {
        client.close()
    }
}
