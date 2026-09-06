package com.thelightphone.agenda

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsParserTest {
    private val windowStart = ZonedDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant()
    private val windowEnd = windowStart.plusSeconds(14 * 24 * 60 * 60L)

    private fun parse(body: String): List<AgendaEvent> {
        val ics = "BEGIN:VCALENDAR\nVERSION:2.0\n$body\nEND:VCALENDAR"
        return IcsParser.parseEvents(ics, "src1", "Work", windowStart, windowEnd)
    }

    @Test
    fun `single UTC event within window is parsed`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt1
            SUMMARY:Standup
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("Standup", event.title)
        assertEquals(false, event.allDay)
        assertEquals(ZonedDateTime.of(2026, 9, 8, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(), event.startEpochMillis)
        assertEquals(30 * 60 * 1000L, event.endEpochMillis - event.startEpochMillis)
    }

    @Test
    fun `event outside window is excluded`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt2
            SUMMARY:Next month
            DTSTART:20261101T150000Z
            DTEND:20261101T153000Z
            END:VEVENT
            """.trimIndent(),
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `all-day event is flagged and spans a day`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt3
            SUMMARY:Offsite
            DTSTART;VALUE=DATE:20260909
            DTEND;VALUE=DATE:20260910
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(1, events.size)
        assertTrue(events.single().allDay)
    }

    @Test
    fun `TZID datetime resolves using the named zone`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt4
            SUMMARY:Local meeting
            DTSTART;TZID=America/New_York:20260908T090000
            DTEND;TZID=America/New_York:20260908T093000
            END:VEVENT
            """.trimIndent(),
        )
        val event = events.single()
        val expected = ZonedDateTime.of(2026, 9, 8, 9, 0, 0, 0, ZoneId.of("America/New_York")).toInstant()
        assertEquals(expected.toEpochMilli(), event.startEpochMillis)
    }

    @Test
    fun `weekly BYDAY recurrence expands one occurrence per listed weekday`() {
        // Series starts Tue 2026-09-08, recurring Mon/Wed/Fri - the two-week window
        // (2026-09-07 through 2026-09-21) should contain 5 occurrences: this week's
        // Wed+Fri (Mon 9-07 falls before DTSTART, so it's not a real occurrence) plus
        // next week's Mon/Wed/Fri (9-14, 9-16, 9-18).
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt5
            SUMMARY:Standup
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(5, events.size)
        assertTrue(events.all { it.title == "Standup" })
        assertEquals(events.size, events.map { it.startEpochMillis }.distinct().size)
    }

    @Test
    fun `RRULE COUNT stops after the requested number of occurrences`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt6
            SUMMARY:Limited
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            RRULE:FREQ=DAILY;COUNT=3
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(3, events.size)
    }

    @Test
    fun `EXDATE removes a specific occurrence`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt7
            SUMMARY:Daily
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            RRULE:FREQ=DAILY;COUNT=4
            EXDATE:20260909T150000Z
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(3, events.size)
        val excludedMillis = ZonedDateTime.of(2026, 9, 9, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertTrue(events.none { it.startEpochMillis == excludedMillis })
    }

    @Test
    fun `VALARM inside VEVENT is not mistaken for event properties`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt8
            SUMMARY:With reminder
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Reminder
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(1, events.size)
        assertEquals("With reminder", events.single().title)
    }

    @Test
    fun `URL property is parsed and normalized with a scheme`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt9
            SUMMARY:Test event
            DTSTART:20260908T150000Z
            DTEND:20260908T160000Z
            URL;VALUE=URI:www.kagi.com
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals("https://www.kagi.com", events.single().url)
        assertEquals(listOf("https://www.kagi.com"), events.single().links())
    }

    @Test
    fun `VALARM TRIGGER sets alertEpochMillis relative to start`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt10
            SUMMARY:With alarm
            DTSTART:20260908T150000Z
            DTEND:20260908T160000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Reminder
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            """.trimIndent(),
        )
        val event = events.single()
        val expectedAlert = ZonedDateTime.of(2026, 9, 8, 14, 45, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(expectedAlert, event.alertEpochMillis)
    }

    @Test
    fun `VALARM TRIGGER with RELATED=END is relative to the event end`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt11
            SUMMARY:End-relative alarm
            DTSTART:20260908T150000Z
            DTEND:20260908T160000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER;RELATED=END:PT10M
            END:VALARM
            END:VEVENT
            """.trimIndent(),
        )
        val event = events.single()
        val expectedAlert = ZonedDateTime.of(2026, 9, 8, 16, 10, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(expectedAlert, event.alertEpochMillis)
    }

    @Test
    fun `event with no VALARM has a null alertEpochMillis`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt12
            SUMMARY:No alarm
            DTSTART:20260908T150000Z
            DTEND:20260908T160000Z
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(null, events.single().alertEpochMillis)
    }

    @Test
    fun `VALARM TRIGGER on a recurring event is offset per occurrence`() {
        val events = parse(
            """
            BEGIN:VEVENT
            UID:evt13
            SUMMARY:Daily with alarm
            DTSTART:20260908T150000Z
            DTEND:20260908T153000Z
            RRULE:FREQ=DAILY;COUNT=3
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT30M
            END:VALARM
            END:VEVENT
            """.trimIndent(),
        )
        assertEquals(3, events.size)
        events.forEach { event ->
            assertEquals(event.startEpochMillis - 30 * 60 * 1000L, event.alertEpochMillis)
        }
    }
}
