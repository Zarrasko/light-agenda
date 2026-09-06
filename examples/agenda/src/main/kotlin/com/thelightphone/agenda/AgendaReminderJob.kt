package com.thelightphone.agenda

import androidx.datastore.preferences.core.edit
import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult
import com.thelightphone.sdk.LightOverlay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

// LightWork's periodic jobs have a 15-minute floor, so this is also the shortest lead time that
// can ever be honored - checking every 15 minutes for anything whose alert falls in the next 15
// minutes means nothing ever slips through a gap between two runs.
private const val CHECK_LEAD_MINUTES = 15L

// Only events with no VALARM at all fall back to this - anything with a real alert set uses
// that instead (see AgendaEvent.alertEpochMillis / IcsParser's VALARM/TRIGGER parsing).
private const val DEFAULT_LEAD_MINUTES = 15L

// Recurring events reuse the same UID across every occurrence, so the key has to include the
// occurrence's own start time - otherwise the second occurrence would never fire.
private fun AgendaEvent.reminderKey(): String = "$uid|$startEpochMillis"

private fun AgendaEvent.effectiveAlertEpochMillis(): Long =
    alertEpochMillis ?: (startEpochMillis - DEFAULT_LEAD_MINUTES * 60_000L)

@LightJob("agenda-reminder-check")
val agendaReminderJob: LightJobHandler = handler@{ lightContext, _ ->
    val dataStore = lightContext.dataStore
    val sources = AgendaSourcesRepository(dataStore).load()
    if (sources.isEmpty()) return@handler LightJobResult.Success()

    val json = Json { ignoreUnknownKeys = true }
    val api = AgendaApi()
    try {
        runCatching {
            val now = Instant.now()
            // Wide fetch window (matching the main agenda's own horizon) rather than just the
            // next 15 minutes - a "1 day before" alert on an event a day out needs that event to
            // already be visible to this run, long before the event itself is anywhere close.
            val result = fetchAllEvents(api, sources, now, now.plus(AGENDA_WINDOW_DAYS, ChronoUnit.DAYS))

            val notifiedKeys = dataStore.data.first()[AgendaPreferences.NOTIFIED_EVENT_KEYS_JSON]
                ?.let { runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull() }
                .orEmpty()

            val checkWindowEnd = now.plus(CHECK_LEAD_MINUTES, ChronoUnit.MINUTES).toEpochMilli()
            val due = result.events.filter { event ->
                event.reminderKey() !in notifiedKeys &&
                    event.effectiveAlertEpochMillis().let { it in now.toEpochMilli()..checkWindowEnd }
            }
            due.forEach { event ->
                LightOverlay.show(
                    lightContext = lightContext,
                    title = event.title,
                    text = "${event.timeRangeLabel()} · ${event.calendarLabel}",
                )
            }

            if (due.isNotEmpty()) {
                // Prune to the last day - anything older can never collide with a freshly
                // fetched window again, so there's no reason to keep growing this set forever.
                val cutoffMillis = now.minus(1, ChronoUnit.DAYS).toEpochMilli()
                val updatedKeys = (notifiedKeys + due.map { it.reminderKey() })
                    .filter { key -> (key.substringAfterLast('|').toLongOrNull() ?: 0L) >= cutoffMillis }
                    .toSet()
                dataStore.edit { it[AgendaPreferences.NOTIFIED_EVENT_KEYS_JSON] = json.encodeToString(updatedKeys) }
            }
        }.fold(
            onSuccess = { LightJobResult.Success() },
            onFailure = { LightJobResult.Retry },
        )
    } finally {
        api.close()
    }
}
