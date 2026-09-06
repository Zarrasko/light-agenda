package com.thelightphone.agenda

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

// A pragmatic RFC 5545 subset - enough to read real-world Google/Outlook/Proton ICS feeds
// (SUMMARY/DTSTART/DTEND, a simple RRULE, EXDATE, a VALARM's TRIGGER) without pulling in a full
// iCalendar library, which the SDK's permission/dependency allowlist wouldn't clear anyway.
// Known gaps: no VTIMEZONE-defined custom zones (TZID is looked up as a plain IANA zone id,
// which is what every provider tested actually emits), no RECURRENCE-ID overrides for a single
// modified instance of a series, BYDAY is only honored for FREQ=WEEKLY, and a VALARM's TRIGGER
// is only read in its common relative-duration form (an absolute VALUE=DATE-TIME trigger is
// ignored).
internal object IcsParser {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // Generous cap on recurrence steps per event - cheap date arithmetic, so this only exists
    // to bound a pathological RRULE (e.g. no COUNT/UNTIL on a years-old daily series), not
    // because the happy path gets anywhere close to it.
    private const val MAX_RECURRENCE_ITERATIONS = 20_000

    private data class RawProperty(val name: String, val params: Map<String, String>, val value: String)
    private data class ParsedInstant(val zoned: ZonedDateTime, val allDay: Boolean)

    fun parseEvents(
        icsText: String,
        sourceId: String,
        calendarLabel: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<AgendaEvent> {
        val events = mutableListOf<AgendaEvent>()
        val componentStack = ArrayDeque<String>()
        var currentProps = mutableListOf<RawProperty>()
        // The alert(s) a calendar app's own "remind me" setting attaches to the event, as a
        // VALARM sub-component - reset alongside currentProps, but collected separately since
        // a VALARM's own properties (its TRIGGER included) must never be mistaken for the
        // parent VEVENT's properties.
        var currentAlarmTriggers = mutableListOf<RawProperty>()

        for (line in unfold(icsText)) {
            val (nameAndParams, value) = splitProperty(line) ?: continue
            val propName = nameAndParams.substringBefore(';').uppercase()

            when (propName) {
                "BEGIN" -> {
                    componentStack.addLast(value.trim().uppercase())
                    if (componentStack.last() == "VEVENT") {
                        currentProps = mutableListOf()
                        currentAlarmTriggers = mutableListOf()
                    }
                }
                "END" -> {
                    val closed = componentStack.removeLastOrNull()
                    if (closed == "VEVENT") {
                        events += expandEvent(currentProps, currentAlarmTriggers, sourceId, calendarLabel, windowStart, windowEnd)
                    }
                }
                "TRIGGER" -> {
                    if (componentStack.lastOrNull() == "VALARM") {
                        currentAlarmTriggers += RawProperty(propName, parseParams(nameAndParams), value)
                    }
                }
                else -> {
                    if (componentStack.lastOrNull() == "VEVENT") {
                        currentProps += RawProperty(propName, parseParams(nameAndParams), value)
                    }
                }
            }
        }

        return events
    }

    private fun unfold(icsText: String): List<String> {
        val rawLines = icsText.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val result = mutableListOf<String>()
        for (raw in rawLines) {
            if (raw.isEmpty()) continue
            if ((raw[0] == ' ' || raw[0] == '\t') && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + raw.substring(1)
            } else {
                result += raw
            }
        }
        return result
    }

    private fun splitProperty(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx < 0) return null
        return line.substring(0, idx) to line.substring(idx + 1)
    }

    private fun parseParams(nameAndParams: String): Map<String, String> {
        val tokens = nameAndParams.split(';')
        if (tokens.size <= 1) return emptyMap()
        return tokens.drop(1).mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq < 0) return@mapNotNull null
            token.substring(0, eq).uppercase() to token.substring(eq + 1).trim('"')
        }.toMap()
    }

    // "-PT15M", "-PT1H", "-P1D", "PT0S" (at time of event) cover what Apple/Google/Outlook's own
    // "remind me" pickers actually emit. Weeks are included since they're valid ISO8601 even
    // though no mainstream calendar UI offers a "weeks before" option.
    private val DURATION_REGEX = Regex("""^([+-]?)P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    private fun parseIsoDurationMillis(value: String): Long? {
        val groups = DURATION_REGEX.matchEntire(value.trim())?.groupValues ?: return null
        val weeks = groups[2].toLongOrNull() ?: 0L
        val days = groups[3].toLongOrNull() ?: 0L
        val hours = groups[4].toLongOrNull() ?: 0L
        val minutes = groups[5].toLongOrNull() ?: 0L
        val seconds = groups[6].toLongOrNull() ?: 0L
        val totalSeconds = weeks * 7 * 86400 + days * 86400 + hours * 3600 + minutes * 60 + seconds
        return if (groups[1] == "-") -totalSeconds * 1000 else totalSeconds * 1000
    }

    private fun unescapeText(value: String): String =
        value.replace("\\n", "\n").replace("\\N", "\n")
            .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseDateOrDateTime(value: String, params: Map<String, String>): ParsedInstant? {
        val v = value.trim()
        return when {
            params["VALUE"] == "DATE" || (v.length == 8 && v.all { it.isDigit() }) -> {
                val date = runCatching { LocalDate.parse(v, DATE_FMT) }.getOrNull() ?: return null
                ParsedInstant(date.atStartOfDay(ZoneId.systemDefault()), allDay = true)
            }
            v.endsWith("Z") -> {
                val ldt = runCatching { LocalDateTime.parse(v.dropLast(1), DATETIME_FMT) }.getOrNull() ?: return null
                ParsedInstant(ldt.atZone(ZoneOffset.UTC), allDay = false)
            }
            else -> {
                val ldt = runCatching { LocalDateTime.parse(v, DATETIME_FMT) }.getOrNull() ?: return null
                val zone = params["TZID"]?.let { tzid -> runCatching { ZoneId.of(tzid) }.getOrNull() }
                    ?: ZoneId.systemDefault()
                ParsedInstant(ldt.atZone(zone), allDay = false)
            }
        }
    }

    private fun expandEvent(
        props: List<RawProperty>,
        alarmTriggers: List<RawProperty>,
        sourceId: String,
        calendarLabel: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<AgendaEvent> {
        val dtstartProp = props.firstOrNull { it.name == "DTSTART" } ?: return emptyList()
        val dtstart = parseDateOrDateTime(dtstartProp.value, dtstartProp.params) ?: return emptyList()

        val dtendProp = props.firstOrNull { it.name == "DTEND" }
        val dtend = dtendProp?.let { parseDateOrDateTime(it.value, it.params) }
        val durationMillis = when {
            dtend != null -> Duration.between(dtstart.zoned, dtend.zoned).toMillis().coerceAtLeast(0)
            dtstart.allDay -> Duration.ofDays(1).toMillis()
            else -> 0L
        }

        val uid = props.firstOrNull { it.name == "UID" }?.value ?: "${dtstart.zoned}-${props.hashCode()}"
        val title = props.firstOrNull { it.name == "SUMMARY" }?.value?.let(::unescapeText) ?: "(untitled event)"
        val location = props.firstOrNull { it.name == "LOCATION" }?.value?.let(::unescapeText)?.takeIf { it.isNotBlank() }
        val description = props.firstOrNull { it.name == "DESCRIPTION" }?.value?.let(::unescapeText)?.takeIf { it.isNotBlank() }
        // Apple Calendar's dedicated "Add Video Call/URL" field maps to its own URL property,
        // separate from DESCRIPTION - and often has no scheme (e.g. "www.kagi.com"), unlike a
        // link embedded in free text, so it needs its own normalization rather than the plain
        // regex extraction links() does over description/location.
        val url = props.firstOrNull { it.name == "URL" }?.value?.let(::unescapeText)?.takeIf { it.isNotBlank() }
            ?.let { if (it.contains("://")) it else "https://$it" }

        // The calendar app's own "remind me" setting - a duration relative to the event's start
        // (or end, if RELATED=END) rather than a fixed lead time, so a reminder can honor
        // whatever the user actually configured per-event ("15 minutes before", "1 day before",
        // at time of event, etc.) instead of a single blanket rule. Only the common relative-
        // duration form is handled; an absolute VALUE=DATE-TIME trigger (rare in practice, and
        // not meaningful per-occurrence for a recurring event anyway) is left unsupported.
        val firstAlarm = alarmTriggers.firstOrNull()
        val alarmOffsetMillis = firstAlarm?.let { parseIsoDurationMillis(it.value) }
        val alarmRelatedToEnd = firstAlarm?.params?.get("RELATED") == "END"

        fun toAgendaEvent(start: ZonedDateTime): AgendaEvent {
            val startMillis = start.toInstant().toEpochMilli()
            val endMillis = startMillis + durationMillis
            val alertEpochMillis = alarmOffsetMillis?.plus(if (alarmRelatedToEnd) endMillis else startMillis)
            return AgendaEvent(
                sourceId = sourceId,
                calendarLabel = calendarLabel,
                uid = uid,
                title = title,
                location = location,
                description = description,
                url = url,
                allDay = dtstart.allDay,
                startEpochMillis = startMillis,
                endEpochMillis = endMillis,
                alertEpochMillis = alertEpochMillis,
            )
        }

        val rruleValue = props.firstOrNull { it.name == "RRULE" }?.value
        if (rruleValue == null) {
            val startMillis = dtstart.zoned.toInstant().toEpochMilli()
            val endMillis = startMillis + durationMillis
            return if (endMillis > windowStart.toEpochMilli() && startMillis < windowEnd.toEpochMilli()) {
                listOf(toAgendaEvent(dtstart.zoned))
            } else {
                emptyList()
            }
        }

        val exdates = props.filter { it.name == "EXDATE" }
            .flatMap { prop ->
                prop.value.split(',').mapNotNull { parseDateOrDateTime(it, prop.params)?.zoned?.toInstant()?.toEpochMilli() }
            }
            .toSet()

        val rrule = parseRRule(rruleValue)
        return expandRecurrence(dtstart.zoned, rrule, windowStart, windowEnd)
            .filterNot { exdates.contains(it.toInstant().toEpochMilli()) }
            .map(::toAgendaEvent)
    }

    private fun parseRRule(value: String): Map<String, String> =
        value.split(';').mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq < 0) return@mapNotNull null
            token.substring(0, eq).uppercase() to token.substring(eq + 1)
        }.toMap()

    private fun parseWeekday(token: String): DayOfWeek? = when (token.trim().takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun Instant.within(start: Instant, end: Instant): Boolean = !isBefore(start) && !isAfter(end)

    private fun expandRecurrence(
        dtstart: ZonedDateTime,
        rrule: Map<String, String>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<ZonedDateTime> {
        val freq = rrule["FREQ"] ?: return if (dtstart.toInstant().within(windowStart, windowEnd)) {
            listOf(dtstart)
        } else {
            emptyList()
        }
        val interval = rrule["INTERVAL"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val count = rrule["COUNT"]?.toIntOrNull()
        val until = rrule["UNTIL"]?.let { parseDateOrDateTime(it, emptyMap())?.zoned?.toInstant() }
        val byDay = rrule["BYDAY"]?.split(',')?.mapNotNull(::parseWeekday).orEmpty()

        val results = mutableListOf<ZonedDateTime>()
        var produced = 0
        var iterations = 0

        fun withinLimits(candidate: ZonedDateTime): Boolean {
            if (count != null && produced >= count) return false
            if (until != null && candidate.toInstant().isAfter(until)) return false
            return true
        }

        when (freq) {
            "DAILY", "MONTHLY", "YEARLY" -> {
                var current = dtstart
                while (iterations < MAX_RECURRENCE_ITERATIONS) {
                    iterations++
                    if (!withinLimits(current)) break
                    produced++
                    if (current.toInstant().within(windowStart, windowEnd)) results += current
                    if (current.toInstant().isAfter(windowEnd)) break
                    current = when (freq) {
                        "DAILY" -> current.plusDays(interval.toLong())
                        "MONTHLY" -> current.plusMonths(interval.toLong())
                        else -> current.plusYears(interval.toLong())
                    }
                }
            }
            "WEEKLY" -> {
                var weekAnchor = dtstart
                outer@ while (iterations < MAX_RECURRENCE_ITERATIONS) {
                    val weekMonday = weekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val candidates = if (byDay.isEmpty()) {
                        listOf(weekAnchor)
                    } else {
                        byDay.map { dow -> weekMonday.with(TemporalAdjusters.nextOrSame(dow)) }
                            .distinct()
                            .sortedBy { it.toInstant() }
                    }
                    for (candidate in candidates) {
                        if (candidate.toInstant().isBefore(dtstart.toInstant())) continue
                        iterations++
                        if (!withinLimits(candidate)) break@outer
                        produced++
                        if (candidate.toInstant().within(windowStart, windowEnd)) results += candidate
                    }
                    if (weekAnchor.toInstant().isAfter(windowEnd)) break
                    weekAnchor = weekAnchor.plusWeeks(interval.toLong())
                }
            }
            else -> return emptyList()
        }

        return results
    }
}
