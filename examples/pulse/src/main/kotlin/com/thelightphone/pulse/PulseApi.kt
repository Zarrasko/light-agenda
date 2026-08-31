package com.thelightphone.pulse

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Base64

// intervals.icu parses a lightweight markdown-style syntax out of `description` into the
// structured workout steps it pushes to Garmin - see PulseScheduleWorkoutScreen for how that
// text gets built. Nothing here needs to know about workout_doc directly.
@Serializable
private data class CreateWorkoutRequest(
    val category: String = "WORKOUT",
    @SerialName("start_date_local") val startDateLocal: String,
    val type: String,
    val name: String,
    val description: String,
)

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
    // encodeDefaults matters for createWorkoutEvent: without it, CreateWorkoutRequest.category
    // ("WORKOUT", a default value) gets silently dropped from the outgoing JSON rather than sent
    // - intervals.icu then rejects the request with 422 "Category is required".
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
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

    suspend fun createWorkoutEvent(
        apiKey: String,
        startDateLocal: String,
        type: String,
        name: String,
        description: String,
    ): Result<Unit> = runCatching {
        val response = client.post("$INTERVALS_API_BASE/$SELF_ATHLETE_PATH/events") {
            header("Authorization", basicAuthHeader(apiKey))
            contentType(ContentType.Application.Json)
            setBody(
                CreateWorkoutRequest(
                    startDateLocal = startDateLocal,
                    type = type,
                    name = name,
                    description = description,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw IllegalStateException("Intervals.icu create workout HTTP ${response.status.value}: $body")
        }
    }

    fun close() {
        client.close()
    }
}
