package com.thelightphone.pulse

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Base64

private const val INTERVALS_API_BASE = "https://intervals.icu/api/v1"

// "0" is intervals.icu's documented shorthand for "the athlete that owns this API key" - no
// separate athlete-id lookup needed.
private const val SELF_ATHLETE_PATH = "athlete/0"

private const val ACTIVITY_HISTORY_DAYS = 30

internal class PulseApi {
    // coerceInputValues matters here: a manually-created activity (e.g. from Mark Done on a
    // planned workout with no target) can come back with an explicit `"distance": null` rather
    // than omitting the field - a non-null field's default only covers a missing key, not an
    // explicit null, so without this every such activity would fail to parse and take the
    // whole activities fetch down with it.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun basicAuthHeader(apiKey: String): String {
        val credentials = Base64.getEncoder().encodeToString("API_KEY:$apiKey".toByteArray())
        return "Basic $credentials"
    }

    // Doubles as a credentials check: an invalid key fails this the same way it'd fail
    // fetchActivities, so callers can validate by just calling this once.
    suspend fun fetchActivities(apiKey: String): Result<List<SummaryActivity>> = runCatching {
        val oldest = dateFormat.format(Date(System.currentTimeMillis() - ACTIVITY_HISTORY_DAYS * 24L * 60 * 60 * 1000))
        val response = client.get("$INTERVALS_API_BASE/$SELF_ATHLETE_PATH/activities?oldest=$oldest&limit=30") {
            header("Authorization", basicAuthHeader(apiKey))
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Intervals.icu activities HTTP ${response.status.value}: $body")
        }

        response.body()
    }

    // Wellness for "today" is often still empty (nothing synced yet this morning) - that's a
    // normal, non-error outcome, so callers should treat a failure here as "no data to show"
    // rather than surfacing it the way an activities failure would be.
    suspend fun fetchTodayWellness(apiKey: String): Result<Wellness> = runCatching {
        val today = dateFormat.format(Date())
        val response = client.get("$INTERVALS_API_BASE/$SELF_ATHLETE_PATH/wellness/$today") {
            header("Authorization", basicAuthHeader(apiKey))
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Intervals.icu wellness HTTP ${response.status.value}: $body")
        }

        response.body()
    }

    suspend fun fetchGear(apiKey: String): Result<List<Gear>> = runCatching {
        val response = client.get("$INTERVALS_API_BASE/$SELF_ATHLETE_PATH/gear") {
            header("Authorization", basicAuthHeader(apiKey))
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Intervals.icu gear HTTP ${response.status.value}: $body")
        }

        response.body()
    }

    fun close() {
        client.close()
    }
}
