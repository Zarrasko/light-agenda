package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Mirrors the underline LightTextField draws for its own value, so a picker's selected row
// looks like it belongs to the same design language - see LightTextField.kt.
private const val UNDERLINE_THICKNESS_PX = 3f
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

private enum class WorkoutSport(val label: String) {
    RUN("Run"), RIDE("Ride"), WALK("Walk"),
}

private enum class WorkoutWhen(val label: String) {
    TOMORROW("Tomorrow"), TODAY("Today"),
}

private enum class WorkoutStructure(val label: String) {
    BASE("Base"), INTERVALS("Intervals"),
}

// "press lap" is intervals.icu's syntax for a step that waits for the athlete to press the lap
// button on the watch rather than auto-advancing after a fixed duration - confirmed on-device
// against a real Garmin push, see the Pulse workout-scheduling test history.
private enum class RestType(val label: String) {
    TIMED("Timed"), PRESS_LAP("Until lap press"),
}

// Three target kinds intervals.icu's text syntax supports, confirmed against the live API:
// "Z2" -> power zone, "Z2 HR" -> heart-rate zone, "7:30/mi Pace" -> absolute pace (the literal
// word "Pace" is required, otherwise the value is silently dropped).
private enum class TargetType(val label: String) {
    NONE("No target"), POWER_ZONE("Power Zone"), HR_ZONE("Heart Rate Zone"), PACE("Pace"),
}

private enum class PaceUnit(val label: String, val syntax: String) {
    PER_MILE("Per Mile", "/mi"), PER_KM("Per Km", "/km"),
}

private data class TargetState(
    val type: TargetType = TargetType.NONE,
    val zone: Int = 2,
    val paceMinutes: Int = 7,
    val paceSeconds: Int = 30,
    val paceUnit: PaceUnit = PaceUnit.PER_MILE,
) {
    fun syntax(): String = when (type) {
        TargetType.NONE -> ""
        TargetType.POWER_ZONE -> " Z$zone"
        TargetType.HR_ZONE -> " Z$zone HR"
        TargetType.PACE -> " %d:%02d%s Pace".format(paceMinutes, paceSeconds, paceUnit.syntax)
    }

    fun displayLabel(): String = when (type) {
        TargetType.NONE -> "No target"
        TargetType.POWER_ZONE -> "Power Zone $zone"
        TargetType.HR_ZONE -> "Heart Rate Zone $zone"
        TargetType.PACE -> "%d:%02d%s".format(paceMinutes, paceSeconds, paceUnit.syntax)
    }
}

private enum class NumberField(val title: String) {
    SIMPLE_DURATION("Duration (minutes)"),
    WARMUP("Warmup (minutes)"),
    WORK("Work (minutes)"),
    REST("Rest (minutes)"),
    REPEATS("Repeats"),
    COOLDOWN("Cooldown (minutes)"),
    SIMPLE_PACE_MINUTES("Pace Minutes"),
    SIMPLE_PACE_SECONDS("Pace Seconds"),
    WORK_PACE_MINUTES("Pace Minutes"),
    WORK_PACE_SECONDS("Pace Seconds"),
}

private enum class PickerField(val title: String) {
    SPORT("Sport"),
    WHEN("When"),
    STRUCTURE("Structure"),
    REST_TYPE("Rest Ends"),
    SIMPLE_TARGET_TYPE("Target"),
    SIMPLE_ZONE("Zone"),
    SIMPLE_PACE_UNIT("Pace Unit"),
    WORK_TARGET_TYPE("Work Target"),
    WORK_ZONE("Zone"),
    WORK_PACE_UNIT("Pace Unit"),
}

// Self-sufficient like PulseGearScreen - talks to PulseApi directly rather than through
// PulseViewModel, since it's reached by navigateTo and doesn't affect the home screen's state.
class PulseScheduleWorkoutScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    private val api = PulseApi()
    private val credentialsRepository = PulseCredentialsRepository(lightContext.dataStore)

    override fun onScreenDestroy() {
        super.onScreenDestroy()
        api.close()
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val scope = rememberCoroutineScope()

        var sport by remember { mutableStateOf(WorkoutSport.RUN) }
        var whenChoice by remember { mutableStateOf(WorkoutWhen.TOMORROW) }
        var structure by remember { mutableStateOf(WorkoutStructure.BASE) }

        var simpleDuration by remember { mutableStateOf(45) }
        var simpleTarget by remember { mutableStateOf(TargetState()) }

        var warmup by remember { mutableStateOf(10) }
        var work by remember { mutableStateOf(3) }
        var workTarget by remember { mutableStateOf(TargetState(type = TargetType.POWER_ZONE, zone = 4)) }
        var rest by remember { mutableStateOf(2) }
        var restType by remember { mutableStateOf(RestType.PRESS_LAP) }
        var repeats by remember { mutableStateOf(4) }
        var cooldown by remember { mutableStateOf(10) }

        var editingNumber by remember { mutableStateOf<NumberField?>(null) }
        var editSession by remember { mutableStateOf(0) }
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        var activePicker by remember { mutableStateOf<PickerField?>(null) }

        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        fun numberValue(field: NumberField): Int = when (field) {
            NumberField.SIMPLE_DURATION -> simpleDuration
            NumberField.WARMUP -> warmup
            NumberField.WORK -> work
            NumberField.REST -> rest
            NumberField.REPEATS -> repeats
            NumberField.COOLDOWN -> cooldown
            NumberField.SIMPLE_PACE_MINUTES -> simpleTarget.paceMinutes
            NumberField.SIMPLE_PACE_SECONDS -> simpleTarget.paceSeconds
            NumberField.WORK_PACE_MINUTES -> workTarget.paceMinutes
            NumberField.WORK_PACE_SECONDS -> workTarget.paceSeconds
        }

        fun setNumberValue(field: NumberField, value: Int) {
            when (field) {
                NumberField.SIMPLE_DURATION -> simpleDuration = value
                NumberField.WARMUP -> warmup = value
                NumberField.WORK -> work = value
                NumberField.REST -> rest = value
                NumberField.REPEATS -> repeats = value
                NumberField.COOLDOWN -> cooldown = value
                NumberField.SIMPLE_PACE_MINUTES -> simpleTarget = simpleTarget.copy(paceMinutes = value)
                NumberField.SIMPLE_PACE_SECONDS -> simpleTarget = simpleTarget.copy(paceSeconds = value.coerceAtMost(59))
                NumberField.WORK_PACE_MINUTES -> workTarget = workTarget.copy(paceMinutes = value)
                NumberField.WORK_PACE_SECONDS -> workTarget = workTarget.copy(paceSeconds = value.coerceAtMost(59))
            }
        }

        fun openNumberEditor(field: NumberField) {
            editingNumber = field
            editSession += 1
        }

        fun pickerOptions(field: PickerField): List<String> = when (field) {
            PickerField.SPORT -> WorkoutSport.entries.map { it.label }
            PickerField.WHEN -> WorkoutWhen.entries.map { it.label }
            PickerField.STRUCTURE -> WorkoutStructure.entries.map { it.label }
            PickerField.REST_TYPE -> RestType.entries.map { it.label }
            PickerField.SIMPLE_TARGET_TYPE, PickerField.WORK_TARGET_TYPE -> TargetType.entries.map { it.label }
            PickerField.SIMPLE_ZONE, PickerField.WORK_ZONE -> (1..5).map { "Zone $it" }
            PickerField.SIMPLE_PACE_UNIT, PickerField.WORK_PACE_UNIT -> PaceUnit.entries.map { it.label }
        }

        fun pickerSelectedLabel(field: PickerField): String = when (field) {
            PickerField.SPORT -> sport.label
            PickerField.WHEN -> whenChoice.label
            PickerField.STRUCTURE -> structure.label
            PickerField.REST_TYPE -> restType.label
            PickerField.SIMPLE_TARGET_TYPE -> simpleTarget.type.label
            PickerField.SIMPLE_ZONE -> "Zone ${simpleTarget.zone}"
            PickerField.SIMPLE_PACE_UNIT -> simpleTarget.paceUnit.label
            PickerField.WORK_TARGET_TYPE -> workTarget.type.label
            PickerField.WORK_ZONE -> "Zone ${workTarget.zone}"
            PickerField.WORK_PACE_UNIT -> workTarget.paceUnit.label
        }

        fun selectPickerOption(field: PickerField, label: String) {
            when (field) {
                PickerField.SPORT -> sport = WorkoutSport.entries.first { it.label == label }
                PickerField.WHEN -> whenChoice = WorkoutWhen.entries.first { it.label == label }
                PickerField.STRUCTURE -> structure = WorkoutStructure.entries.first { it.label == label }
                PickerField.REST_TYPE -> restType = RestType.entries.first { it.label == label }
                PickerField.SIMPLE_TARGET_TYPE ->
                    simpleTarget = simpleTarget.copy(type = TargetType.entries.first { it.label == label })
                PickerField.SIMPLE_ZONE ->
                    simpleTarget = simpleTarget.copy(zone = label.removePrefix("Zone ").trim().toInt())
                PickerField.SIMPLE_PACE_UNIT ->
                    simpleTarget = simpleTarget.copy(paceUnit = PaceUnit.entries.first { it.label == label })
                PickerField.WORK_TARGET_TYPE ->
                    workTarget = workTarget.copy(type = TargetType.entries.first { it.label == label })
                PickerField.WORK_ZONE ->
                    workTarget = workTarget.copy(zone = label.removePrefix("Zone ").trim().toInt())
                PickerField.WORK_PACE_UNIT ->
                    workTarget = workTarget.copy(paceUnit = PaceUnit.entries.first { it.label == label })
            }
            activePicker = null
        }

        fun buildEventDescription(): String = if (structure == WorkoutStructure.BASE) {
            "- ${simpleDuration}m${simpleTarget.syntax()}"
        } else {
            val restSuffix = if (restType == RestType.PRESS_LAP) " press lap" else ""
            "- ${warmup}m Z1\n\n${repeats}x\n- ${work}m${workTarget.syntax()}\n- ${rest}m Z1$restSuffix\n\n- ${cooldown}m Z1"
        }

        fun buildEventName(): String = if (structure == WorkoutStructure.BASE) {
            val targetSuffix = if (simpleTarget.type != TargetType.NONE) " (${simpleTarget.displayLabel()})" else ""
            "${simpleDuration}m ${sport.label}$targetSuffix"
        } else {
            "${repeats}x ${work}m ${workTarget.displayLabel()} / ${rest}m rest ${sport.label}"
        }

        fun isValid(): Boolean = if (structure == WorkoutStructure.BASE) {
            simpleDuration > 0
        } else {
            work > 0 && rest > 0 && repeats > 0
        }

        fun startDateLocal(): String {
            val calendar = Calendar.getInstance()
            if (whenChoice == WorkoutWhen.TOMORROW) calendar.add(Calendar.DAY_OF_YEAR, 1)
            val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            return "${datePart}T06:00:00"
        }

        fun save() {
            if (!isValid()) {
                errorMessage = "Enter a duration greater than zero."
                return
            }
            isSaving = true
            scope.launch {
                val apiKey = credentialsRepository.load()
                if (apiKey == null) {
                    errorMessage = "Connect your intervals.icu account first."
                    isSaving = false
                    return@launch
                }
                api.createWorkoutEvent(
                    apiKey = apiKey,
                    startDateLocal = startDateLocal(),
                    type = sport.label,
                    name = buildEventName(),
                    description = buildEventDescription(),
                ).fold(
                    onSuccess = { goBack() },
                    onFailure = {
                        errorMessage = "Couldn't schedule workout. Check your connection and try again."
                        isSaving = false
                    },
                )
            }
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                val numberField = editingNumber
                val pickerField = activePicker

                when {
                    numberField != null -> {
                        LaunchedEffect(editSession) {
                            textFieldState.setTextAndPlaceCursorAtEnd(numberValue(numberField).toString())
                        }
                        LightTextInputEditor(
                            title = numberField.title,
                            editorKey = editSession,
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            state = textFieldState,
                            singleLine = true,
                            onSubmit = { raw ->
                                val digits = raw.toString().filter { it.isDigit() }
                                setNumberValue(numberField, digits.toIntOrNull()?.coerceAtLeast(0) ?: 0)
                                editingNumber = null
                            },
                            onBack = { editingNumber = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    pickerField != null -> PickerContent(
                        field = pickerField,
                        options = pickerOptions(pickerField),
                        selected = pickerSelectedLabel(pickerField),
                        onSelect = { selectPickerOption(pickerField, it) },
                        onBack = { activePicker = null },
                    )

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        LightTopBar(
                            center = LightTopBarCenter.Text("Schedule a Workout"),
                            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                        )

                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        ) {
                            LightTextField(
                                label = "Sport",
                                value = sport.label,
                                placeholder = "",
                                onClick = { activePicker = PickerField.SPORT },
                                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                            )
                            LightTextField(
                                label = "When",
                                value = whenChoice.label,
                                placeholder = "",
                                onClick = { activePicker = PickerField.WHEN },
                                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                            )
                            LightTextField(
                                label = "Structure",
                                value = structure.label,
                                placeholder = "",
                                onClick = { activePicker = PickerField.STRUCTURE },
                                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                            )

                            if (structure == WorkoutStructure.BASE) {
                                LightTextField(
                                    label = "Duration (minutes)",
                                    value = simpleDuration.toString(),
                                    placeholder = "45",
                                    onClick = { openNumberEditor(NumberField.SIMPLE_DURATION) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                TargetFields(
                                    label = "Target",
                                    target = simpleTarget,
                                    onTypeClick = { activePicker = PickerField.SIMPLE_TARGET_TYPE },
                                    onZoneClick = { activePicker = PickerField.SIMPLE_ZONE },
                                    onPaceMinutesClick = { openNumberEditor(NumberField.SIMPLE_PACE_MINUTES) },
                                    onPaceSecondsClick = { openNumberEditor(NumberField.SIMPLE_PACE_SECONDS) },
                                    onPaceUnitClick = { activePicker = PickerField.SIMPLE_PACE_UNIT },
                                )
                            } else {
                                LightTextField(
                                    label = "Warmup (minutes)",
                                    value = warmup.toString(),
                                    placeholder = "10",
                                    onClick = { openNumberEditor(NumberField.WARMUP) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                LightTextField(
                                    label = "Repeats",
                                    value = repeats.toString(),
                                    placeholder = "4",
                                    onClick = { openNumberEditor(NumberField.REPEATS) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                LightTextField(
                                    label = "Work (minutes)",
                                    value = work.toString(),
                                    placeholder = "3",
                                    onClick = { openNumberEditor(NumberField.WORK) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                TargetFields(
                                    label = "Work Target",
                                    target = workTarget,
                                    onTypeClick = { activePicker = PickerField.WORK_TARGET_TYPE },
                                    onZoneClick = { activePicker = PickerField.WORK_ZONE },
                                    onPaceMinutesClick = { openNumberEditor(NumberField.WORK_PACE_MINUTES) },
                                    onPaceSecondsClick = { openNumberEditor(NumberField.WORK_PACE_SECONDS) },
                                    onPaceUnitClick = { activePicker = PickerField.WORK_PACE_UNIT },
                                )
                                LightTextField(
                                    label = "Rest (minutes)",
                                    value = rest.toString(),
                                    placeholder = "2",
                                    onClick = { openNumberEditor(NumberField.REST) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                LightTextField(
                                    label = "Rest Ends",
                                    value = restType.label,
                                    placeholder = "",
                                    onClick = { activePicker = PickerField.REST_TYPE },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                                LightTextField(
                                    label = "Cooldown (minutes)",
                                    value = cooldown.toString(),
                                    placeholder = "10",
                                    onClick = { openNumberEditor(NumberField.COOLDOWN) },
                                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                )
                            }

                            LightText(
                                text = buildEventName(),
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                            )
                        }

                        LightBottomBar(
                            items = listOf(
                                LightBarButton.LightIcon(
                                    icon = LightIcons.CLOSE,
                                    onClick = { goBack() },
                                    contentDescription = "Cancel",
                                ),
                                LightBarButton.Text(
                                    text = if (isSaving) "SAVING..." else "SAVE",
                                    onClick = { if (!isSaving) save() },
                                ),
                            ),
                        )
                    }
                }

                errorMessage?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = { errorMessage = null },
                    )
                }
            }
        }
    }
}

// Shared by the Base-mode "Target" and Intervals-mode "Work Target" rows - the type picker plus
// whichever follow-up field that type needs (a 1-5 zone, or a pace value + unit).
@Composable
private fun TargetFields(
    label: String,
    target: TargetState,
    onTypeClick: () -> Unit,
    onZoneClick: () -> Unit,
    onPaceMinutesClick: () -> Unit,
    onPaceSecondsClick: () -> Unit,
    onPaceUnitClick: () -> Unit,
) {
    LightTextField(
        label = label,
        value = target.displayLabel(),
        placeholder = "",
        onClick = onTypeClick,
        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
    )
    when (target.type) {
        TargetType.POWER_ZONE, TargetType.HR_ZONE -> LightTextField(
            label = "Zone",
            value = "Zone ${target.zone}",
            placeholder = "",
            onClick = onZoneClick,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        TargetType.PACE -> {
            LightTextField(
                label = "Pace Minutes",
                value = target.paceMinutes.toString(),
                placeholder = "7",
                onClick = onPaceMinutesClick,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightTextField(
                label = "Pace Seconds",
                value = target.paceSeconds.toString(),
                placeholder = "30",
                onClick = onPaceSecondsClick,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightTextField(
                label = "Pace Unit",
                value = target.paceUnit.label,
                placeholder = "",
                onClick = onPaceUnitClick,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            // intervals.icu correctly parses pace targets into workout_doc, but drops them
            // somewhere in its own push-to-Garmin export - confirmed against a real device and
            // matches multiple open reports on the intervals.icu forum. Power/HR zone targets
            // aren't affected, so this is worth surfacing rather than looking like our bug.
            LightText(
                text = "May not display on Garmin (known intervals.icu limitation)",
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
        }

        TargetType.NONE -> Unit
    }
}

// Matches the "Repeat" field in Light Calendar's own Add Event screen: a full-screen list with
// a back chevron, the current choice underlined the same way LightTextField underlines a value.
@Composable
private fun PickerContent(
    field: PickerField,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(field.title),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        LightScrollView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            options.forEach { option ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onSelect(option) })
                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                ) {
                    LightText(text = option, variant = LightTextVariant.Heading)
                    if (option == selected) {
                        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
                                .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
                                .background(colors.content),
                        )
                    }
                }
            }
        }
    }
}
