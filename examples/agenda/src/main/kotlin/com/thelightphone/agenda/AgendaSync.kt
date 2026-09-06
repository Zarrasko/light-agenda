package com.thelightphone.agenda

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

// Shared by AgendaViewModel (the foreground fetch-on-open path) and AgendaReminderJob (the
// background periodic check) so both agree on exactly how sources get merged into events.
internal data class FetchResult(val events: List<AgendaEvent>, val hadSourceErrors: Boolean)

// How far ahead a glanceable agenda on a minimalist device is worth showing - also how far the
// reminder job needs to look, since a "1 day before" alert on a far-out event has to be seen
// coming before the event itself is anywhere near due.
internal const val AGENDA_WINDOW_DAYS = 14L

internal suspend fun fetchAllEvents(
    api: AgendaApi,
    sources: List<CalendarSource>,
    windowStart: Instant,
    windowEnd: Instant,
): FetchResult {
    // Each source is fetched independently and a failure in one (an unreachable Outlook link,
    // say) never blocks the others from showing.
    val results = coroutineScope {
        sources.map { source -> async { source to api.fetchIcs(source.icsUrl) } }.awaitAll()
    }

    val hadSourceErrors = results.any { it.second.isFailure }
    val events = results.flatMap { (source, result) ->
        result.getOrNull()?.let { icsText ->
            runCatching {
                IcsParser.parseEvents(icsText, source.id, source.label, windowStart, windowEnd)
            }.getOrNull()
        }.orEmpty()
    }.sortedBy { it.startEpochMillis }

    return FetchResult(events, hadSourceErrors)
}
