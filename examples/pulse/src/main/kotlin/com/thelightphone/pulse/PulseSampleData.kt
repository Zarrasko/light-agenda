package com.thelightphone.pulse

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Lets the setup screen offer a look at the Activities screen before anyone has an
// intervals.icu API key entered - dates are computed relative to "now" so the This Week
// filter in weekSummary() behaves the same way it would against real data (the oldest entry
// here is deliberately outside the 7-day window).
private fun daysAgo(days: Int, hour: Int): String {
    val date = Date(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)
    val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    return "%sT%02d:00:00".format(datePart, hour)
}

internal fun sampleActivities(): List<SummaryActivity> = listOf(
    SummaryActivity(
        id = "sample-1",
        name = "Morning Run",
        type = "Run",
        distance = 8046.7,
        movingTimeSeconds = 2760,
        elevationGainMeters = 45.0,
        startDateLocal = daysAgo(days = 0, hour = 7),
    ),
    SummaryActivity(
        id = "sample-2",
        name = "Evening Ride",
        type = "Ride",
        distance = 32186.9,
        movingTimeSeconds = 5400,
        elevationGainMeters = 210.0,
        startDateLocal = daysAgo(days = 2, hour = 18),
    ),
    SummaryActivity(
        id = "sample-3",
        name = "Easy 5K",
        type = "Run",
        distance = 4989.0,
        movingTimeSeconds = 1620,
        elevationGainMeters = 12.0,
        startDateLocal = daysAgo(days = 4, hour = 6),
    ),
    SummaryActivity(
        id = "sample-4",
        name = "Trail Hike",
        type = "Hike",
        distance = 9656.1,
        movingTimeSeconds = 7200,
        elevationGainMeters = 380.0,
        startDateLocal = daysAgo(days = 6, hour = 9),
    ),
    // Outside the 7-day window on purpose, to show the This Week total excludes it.
    SummaryActivity(
        id = "sample-5",
        name = "Long Run",
        type = "Run",
        distance = 12874.0,
        movingTimeSeconds = 4500,
        elevationGainMeters = 90.0,
        startDateLocal = daysAgo(days = 10, hour = 7),
    ),
)

internal fun sampleWellness(): Wellness = Wellness(
    id = "sample",
    restingHR = 52,
    hrv = 68.0,
    sleepSecs = 27180,
    sleepScore = 84.0,
    steps = 6420,
    stress = 28,
    readiness = 79.0,
    ctl = 62.0,
    atl = 71.0,
    rampRate = 4.2,
)

internal fun sampleGear(): List<Gear> = listOf(
    Gear(id = "sample-shoe", type = "Shoe", name = "Trail Runner", distance = 321869.0),
    Gear(id = "sample-bike", type = "Bike", name = "Road Bike", distance = 804670.0),
)
