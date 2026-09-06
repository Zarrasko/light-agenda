package com.thelightphone.agenda

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// One configured feed - a label the user picks plus the read-only ICS URL it's fetched from.
// Outlook's "publish a calendar" link and Proton Calendar's share link are both plain ICS,
// same as Google/iCloud's subscribe links - there's nothing provider-specific to model here.
@Serializable
data class CalendarSource(
    val id: String,
    val label: String,
    val icsUrl: String,
)

// A single occurrence to display - for a recurring VEVENT this is one expanded instance, not
// the series itself, so uid alone isn't a unique key (use uid+startEpochMillis for that).
@Serializable
data class AgendaEvent(
    val sourceId: String,
    val calendarLabel: String,
    val uid: String,
    val title: String,
    val location: String? = null,
    val description: String? = null,
    val url: String? = null,
    val allDay: Boolean,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    // When the calendar app's own "remind me" setting says to alert, if the user set one -
    // null means the event has no VALARM at all, not that it's due "now".
    val alertEpochMillis: Long? = null,
)

private val DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

// "Today"/"Tomorrow" reads better than a bare date for the two rows a glanceable agenda
// actually needs to distinguish quickly - everything further out gets the short date.
fun AgendaEvent.dayLabel(): String {
    val date = Instant.ofEpochMilli(startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DAY_LABEL_FORMAT)
    }
}

fun AgendaEvent.timeRangeLabel(): String {
    if (allDay) return "All day"
    val start = Instant.ofEpochMilli(startEpochMillis).atZone(ZoneId.systemDefault())
    if (endEpochMillis <= startEpochMillis) return start.format(TIME_FORMAT)
    val end = Instant.ofEpochMilli(endEpochMillis).atZone(ZoneId.systemDefault())
    return "${start.format(TIME_FORMAT)} – ${end.format(TIME_FORMAT)}"
}

private val URL_REGEX = Regex("""https?://[^\s<>"']+""")
private val TRAILING_PUNCTUATION = Regex("""[.,;:)>\]]+$""")

// Meeting links (Teams, Zoom, Meet, a FaceTime web-join link) show up embedded in plain
// DESCRIPTION/LOCATION text, not as a separate structured field - this just finds them so the
// UI can offer them as something tappable instead of inert text.
fun AgendaEvent.links(): List<String> {
    val text = listOfNotNull(description, location).joinToString("\n")
    val embedded = URL_REGEX.findAll(text).map { it.value.replace(TRAILING_PUNCTUATION, "") }
    return (listOfNotNull(url) + embedded).distinct().toList()
}
