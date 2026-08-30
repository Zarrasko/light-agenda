package com.thelightphone.pulse

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class SummaryActivity(
    val id: String,
    val name: String,
    val type: String = "Workout",
    val distance: Double = 0.0,
    @SerialName("moving_time") val movingTimeSeconds: Int = 0,
    @SerialName("total_elevation_gain") val elevationGainMeters: Double = 0.0,
    @SerialName("start_date_local") val startDateLocal: String,
    @SerialName("has_heartrate") val hasHeartrate: Boolean = false,
    @SerialName("average_heartrate") val averageHeartrate: Int? = null,
    @SerialName("max_heartrate") val maxHeartrate: Int? = null,
    @SerialName("average_cadence") val averageCadence: Double? = null,
    val calories: Int? = null,
)

// Every field is nullable: intervals.icu returns a Wellness object even for a date with no
// data synced yet (e.g. today, before your watch has synced), just with everything null.
@Serializable
data class Wellness(
    val id: String? = null,
    val restingHR: Int? = null,
    val hrv: Double? = null,
    val sleepSecs: Int? = null,
    val sleepScore: Double? = null,
    val steps: Int? = null,
    val stress: Int? = null,
    val readiness: Double? = null,
    val ctl: Double? = null,
    val atl: Double? = null,
    val rampRate: Double? = null,
) {
    // Nothing worth showing if every field the UI cares about is empty.
    val hasAnyData: Boolean
        get() = restingHR != null || hrv != null || sleepSecs != null || steps != null
}

@Serializable
data class Gear(
    val id: String,
    val type: String = "Gear",
    val name: String,
    val distance: Double = 0.0,
    val retired: String? = null,
) {
    val isRetired: Boolean
        get() = retired != null
}

data class WeekSummary(
    val activityCount: Int,
    val distanceMeters: Double,
    val movingTimeSeconds: Int,
)

internal const val SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60

fun List<SummaryActivity>.weekSummary(nowEpochSeconds: Long): WeekSummary {
    val cutoff = nowEpochSeconds - SEVEN_DAYS_SECONDS
    val recent = filter { activity ->
        val startEpoch = runCatching { Instant.parse(activity.startDateLocal.withZ()) }.getOrNull()
        startEpoch != null && startEpoch.epochSeconds >= cutoff
    }
    return WeekSummary(
        activityCount = recent.size,
        distanceMeters = recent.sumOf { it.distance },
        movingTimeSeconds = recent.sumOf { it.movingTimeSeconds },
    )
}

// start_date_local from the API has no offset suffix (it's already local time), but
// kotlinx.datetime.Instant.parse requires one - it's only ever used here for a relative
// "was this within the last 7 days" comparison, so treating it as UTC is close enough.
private fun String.withZ(): String = if (endsWith("Z")) this else "${this}Z"

private const val METERS_PER_MILE = 1609.344

fun Double.formatMiles(): String {
    val miles = this / METERS_PER_MILE
    return "%.1f mi".format(miles)
}

fun Int.formatDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun String.formatActivityDate(): String {
    // "2026-08-28T07:15:32" - keep it to a short, glanceable date.
    val datePart = substringBefore("T")
    val parts = datePart.split("-")
    if (parts.size != 3) return datePart
    val (_, month, day) = parts
    return "$month/$day"
}

private val ACTIVITY_DATE_TIME_INPUT =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
private val ACTIVITY_DATE_TIME_OUTPUT =
    java.text.SimpleDateFormat("EEEE, MMMM d 'at' h:mm a", java.util.Locale.US)

// Fuller than formatActivityDate() - for the detail screen, where there's room for it.
fun String.formatActivityDateTime(): String {
    val parsed = runCatching { ACTIVITY_DATE_TIME_INPUT.parse(this) }.getOrNull() ?: return this
    return ACTIVITY_DATE_TIME_OUTPUT.format(parsed)
}

private const val METERS_PER_FOOT = 0.3048

fun Double.formatFeet(): String = "${(this / METERS_PER_FOOT).roundToInt()} ft"

// Sport types where runners/hikers think in minutes-per-mile rather than mph - everything
// else (rides, etc.) gets a speed instead, matching how each sport is usually reported.
private val PACE_SPORT_TYPES = setOf("Run", "TrailRun", "Walk", "Hike", "VirtualRun", "Snowshoe")

data class PaceOrSpeed(val label: String, val value: String)

fun SummaryActivity.paceOrSpeed(): PaceOrSpeed? {
    if (distance <= 0 || movingTimeSeconds <= 0) return null
    val miles = distance / METERS_PER_MILE

    return if (type in PACE_SPORT_TYPES) {
        val secondsPerMile = (movingTimeSeconds / miles).roundToInt()
        val minutes = secondsPerMile / 60
        val seconds = secondsPerMile % 60
        PaceOrSpeed("Pace", "%d:%02d /mi".format(minutes, seconds))
    } else {
        val mph = miles / (movingTimeSeconds / 3600.0)
        PaceOrSpeed("Avg Speed", "%.1f mph".format(mph))
    }
}

fun Int.formatSleepDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return "${hours}h ${minutes}m"
}

// Each field is independently nullable (a watch might sync sleep before HRV, etc.), so this
// only includes whatever actually came back - and, separately, whatever the user hasn't hidden
// via Customize Data Fields - rather than assuming all-or-nothing.
fun Wellness.summaryParts(
    showRestingHR: Boolean = true,
    showSleep: Boolean = true,
    showHRV: Boolean = true,
    showSteps: Boolean = true,
): List<String> = listOfNotNull(
    restingHR?.let { "$it bpm resting" }.takeIf { showRestingHR },
    sleepSecs?.let { "${it.formatSleepDuration()} sleep" }.takeIf { showSleep },
    hrv?.let { "${it.roundToInt()}ms HRV" }.takeIf { showHRV },
    steps?.let { "$it steps" }.takeIf { showSteps },
)

enum class FitnessTrend(val label: String) {
    BUILDING("Building"),
    MAINTAINING("Maintaining"),
    DETRAINING("Detraining"),
}

// Ramp Rate is intervals.icu's own weekly rate-of-change for CTL (Chronic Training Load, a
// rolling proxy for fitness): rising CTL means fitness is building, falling means it's
// declining. The +/-2-per-week thresholds are a rough glance-only bucketing, not a precise
// cutoff - the tap-through explanation shows the actual numbers.
private const val RAMP_RATE_BUILDING_THRESHOLD = 2.0
private const val RAMP_RATE_DETRAINING_THRESHOLD = -2.0

fun Wellness.fitnessTrend(): FitnessTrend? {
    val ramp = rampRate ?: return null
    return when {
        ramp > RAMP_RATE_BUILDING_THRESHOLD -> FitnessTrend.BUILDING
        ramp < RAMP_RATE_DETRAINING_THRESHOLD -> FitnessTrend.DETRAINING
        else -> FitnessTrend.MAINTAINING
    }
}

fun Wellness.fitnessTrendExplanation(): String {
    val trend = fitnessTrend() ?: return "Not enough training history yet to determine a trend."
    val ramp = rampRate!!
    val direction = when (trend) {
        FitnessTrend.BUILDING -> "climbing"
        FitnessTrend.MAINTAINING -> "roughly flat"
        FitnessTrend.DETRAINING -> "dropping"
    }
    val rampText = "%+.1f".format(ramp)
    val ctlPart = ctl?.let { " CTL is currently %.0f".format(it) }.orEmpty()
    val atlPart = atl?.let { ", ATL %.0f".format(it) }.orEmpty()
    return "Based on Ramp Rate: how fast your Chronic Training Load (CTL, a rolling measure " +
        "of training volume) is rising or falling week to week. Right now it's $direction " +
        "($rampText/week).$ctlPart$atlPart"
}
