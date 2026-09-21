package com.goalmaker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.R
import com.goalmaker.app.domain.planning.ReviewReminder
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.application.update.InstallResult
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.RitualReminder
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.problems.Problem
import com.goalmaker.app.domain.problems.ProblemRules
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAreas: () -> Unit,
    onOpenConnector: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    problems: List<Problem> = emptyList(),
    onProblemsRead: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Opening Settings is reading them, so the mark on the gear goes (docs/problems.md).
    LaunchedEffect(Unit) { onProblemsRead() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (problems.isNotEmpty()) {
                Section(stringResource(R.string.problems_title)) {
                    problems.forEach { problem -> ProblemRow(problem) }
                }
            }
            Section(stringResource(R.string.settings_appearance)) {
                // The mark takes on the theme's colors, and redraws itself when the theme changes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Label(stringResource(R.string.settings_theme)) }
                    GoalMakerLogo(size = 44.dp)
                }
                ThemePicker(
                    themes = state.themes,
                    selectedId = state.themeId,
                    dark = AppTheme.colors.isDark,
                    onSelect = viewModel::setTheme,
                )
                Label(stringResource(R.string.settings_mode))
                ConnectedChoice(
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ),
                    selected = state.appearance.mode,
                    onSelect = viewModel::setThemeMode,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_pure_black),
                    hint = stringResource(R.string.settings_pure_black_hint),
                    checked = state.appearance.pureBlack,
                    onCheckedChange = viewModel::setPureBlack,
                )
                Label(stringResource(R.string.settings_reduce_motion))
                ConnectedChoice(
                    options = listOf(
                        ReduceMotion.SYSTEM to stringResource(R.string.settings_theme_system),
                        ReduceMotion.ON to stringResource(R.string.settings_reduce_motion_on),
                        ReduceMotion.OFF to stringResource(R.string.settings_reduce_motion_off),
                    ),
                    selected = state.appearance.reduceMotion,
                    onSelect = viewModel::setReduceMotion,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_completion_sound),
                    hint = stringResource(R.string.settings_completion_sound_hint),
                    checked = state.appearance.completionSound,
                    onCheckedChange = viewModel::setCompletionSound,
                )
            }
            Section(stringResource(R.string.settings_planning)) {
                Label(stringResource(R.string.settings_day_start, "%02d:00".format(state.dayStartHour)))
                Slider(
                    value = state.dayStartHour.toFloat(),
                    onValueChange = { viewModel.setDayStartHour(it.roundToInt()) },
                    valueRange = PlanningDay.START_HOURS.first.toFloat()..PlanningDay.START_HOURS.last.toFloat(),
                    steps = PlanningDay.START_HOURS.last - PlanningDay.START_HOURS.first - 1,
                )
                Text(
                    stringResource(R.string.settings_day_start_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlanReminderRow(state.planTomorrowReminder, viewModel::setPlanTomorrowReminder)
                ReviewReminderRow(
                    title = stringResource(R.string.settings_weekly_review),
                    hint = stringResource(R.string.settings_weekly_review_hint),
                    time = state.weeklyReviewReminder,
                    weekday = state.weeklyReviewWeekday,
                    onTime = viewModel::setWeeklyReviewReminder,
                    onWeekday = viewModel::setWeeklyReviewWeekday,
                )
                ReviewReminderRow(
                    title = stringResource(R.string.settings_monthly_review),
                    hint = stringResource(R.string.settings_monthly_review_hint),
                    time = state.monthlyReviewReminder,
                    weekday = null,
                    onTime = viewModel::setMonthlyReviewReminder,
                    onWeekday = {},
                )
                QuietHoursRow(state.quietHours, viewModel::setQuietHours)
                OutlinedButton(onClick = onOpenAreas) { Text(stringResource(R.string.areas_open)) }
                Text(
                    stringResource(R.string.areas_open_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Section(stringResource(R.string.settings_claude)) {
                OutlinedButton(onClick = onOpenConnector) { Text(stringResource(R.string.connector_open)) }
                Text(
                    stringResource(R.string.connector_open_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenActivity) { Text(stringResource(R.string.activity_open)) }
                Text(
                    stringResource(R.string.activity_open_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Section(stringResource(R.string.settings_backup)) {
                BackupCard(viewModel, state.backup)
            }
            Section(stringResource(R.string.settings_account)) {
                Text(state.email, style = MaterialTheme.typography.bodyLarge)
                val unsynced = state.unsyncedAtSignOut
                if (unsynced == null) {
                    OutlinedButton(onClick = { viewModel.signOut() }, enabled = !state.signingOut) {
                        Text(stringResource(R.string.settings_sign_out))
                    }
                } else {
                    Text(
                        pluralStringResource(R.plurals.settings_sign_out_unsynced, unsynced, unsynced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.signOut() }, enabled = !state.signingOut) {
                            Text(stringResource(R.string.settings_sign_out))
                        }
                        TextButton(onClick = { viewModel.signOut(discardUnsynced = true) }, enabled = !state.signingOut) {
                            Text(stringResource(R.string.settings_sign_out_anyway))
                        }
                    }
                }
            }
            Section(stringResource(R.string.settings_updates)) {
                UpdatesContent(state.update, viewModel)
            }
            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, state.appInfo.versionName))
                Text(
                    stringResource(R.string.settings_backend, state.appInfo.backend.url),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.appInfo.isDevBuild) {
                Section(stringResource(R.string.settings_developer)) {
                    Text(
                        stringResource(R.string.settings_backend_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.backendUrlDraft,
                        onValueChange = viewModel::onBackendUrlChange,
                        label = { Text(stringResource(R.string.settings_backend_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.backendKeyDraft,
                        onValueChange = viewModel::onBackendKeyChange,
                        label = { Text(stringResource(R.string.settings_backend_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::saveBackend) { Text(stringResource(R.string.settings_backend_save)) }
                        TextButton(onClick = viewModel::resetBackend) { Text(stringResource(R.string.settings_backend_reset)) }
                    }
                }
            }
        }
    }
}

/** One problem: what happened in plain words, what to do, when, and the technical line folded away. */
@Composable
private fun ProblemRow(problem: Problem) {
    val titles = mapOf(
        ProblemRules.SYNC to (R.string.problems_sync to R.string.problems_sync_advice),
        ProblemRules.BACKUP to (R.string.problems_backup to R.string.problems_backup_advice),
        ProblemRules.UPDATE to (R.string.problems_update to R.string.problems_update_advice),
    )
    val (title, advice) = titles[problem.kind] ?: (R.string.problems_update to R.string.problems_update_advice)
    var open by rememberSaveable(problem.kind, problem.at) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(advice), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        Text(
            whenText(problem.at),
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textMuted,
        )
        problem.detail?.let { detail ->
            TextButton(onClick = { open = !open }) { Text(stringResource(R.string.problems_what_happened)) }
            if (open) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        }
    }
}

// The day and time a problem happened, as this phone writes them.
@Composable
private fun whenText(at: Instant): String {
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
    return format.format(at.atZone(ZoneId.systemDefault()))
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row of connected toggle buttons where exactly one option is on. */
@Composable
private fun <T> ConnectedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun UpdatesContent(update: UpdateUiState, viewModel: SettingsViewModel) {
    when (update) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> {
            LoadingIndicator(modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.settings_update_checking))
        }
        is UpdateUiState.Checked -> CheckResult(update.result, viewModel)
        is UpdateUiState.Downloading -> {
            LinearWavyProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.settings_update_downloading, (update.progress * 100).toInt()))
        }
        is UpdateUiState.Installing -> Text(
            when (val result = update.result) {
                InstallResult.InstallerOpened -> stringResource(R.string.settings_update_opened)
                InstallResult.DownloadCorrupted -> stringResource(R.string.settings_update_corrupted)
                is InstallResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
            },
        )
    }
    val busy = update is UpdateUiState.Checking || update is UpdateUiState.Downloading
    OutlinedButton(onClick = viewModel::checkForUpdates, enabled = !busy) {
        Text(stringResource(R.string.settings_check_updates))
    }
    Text(
        stringResource(R.string.settings_update_manual),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CheckResult(result: UpdateCheckResult, viewModel: SettingsViewModel) {
    val message = when (result) {
        UpdateCheckResult.NotConfigured -> stringResource(R.string.settings_update_not_configured)
        UpdateCheckResult.DevelopmentBuild -> stringResource(R.string.settings_update_dev_build)
        is UpdateCheckResult.UpToDate -> stringResource(R.string.settings_update_up_to_date, result.latest)
        is UpdateCheckResult.Available -> stringResource(R.string.settings_update_available, result.manifest.version.toString())
        UpdateCheckResult.Untrusted -> stringResource(R.string.settings_update_untrusted)
        is UpdateCheckResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
    }
    Text(message, style = MaterialTheme.typography.bodyLarge)
    if (result is UpdateCheckResult.Available) {
        Button(onClick = { viewModel.installUpdate(result) }) {
            Text(stringResource(R.string.settings_install_update, result.manifest.version.toString()))
        }
    }
}

/**
 * The evening Plan tomorrow reminder (docs/reminders.md): on or off, and the time in half hours.
 * Switching it back on starts again at 20:00.
 */
@Composable
private fun PlanReminderRow(time: LocalTime?, onChange: (LocalTime?) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    SwitchRow(
        title = stringResource(R.string.settings_plan_reminder),
        hint = if (time == null) {
            stringResource(R.string.settings_plan_reminder_off)
        } else {
            stringResource(R.string.settings_plan_reminder_on, formatter.format(time))
        },
        checked = time != null,
        onCheckedChange = { on -> onChange(if (on) RitualReminder.DEFAULT_TIME else null) },
    )
    if (time != null) {
        Slider(
            value = (time.toSecondOfDay() / HALF_HOUR).toFloat(),
            onValueChange = { onChange(LocalTime.ofSecondOfDay(it.roundToInt() * HALF_HOUR.toLong())) },
            valueRange = 0f..47f,
            steps = 46,
        )
    }
}

/**
 * A review reminder (docs/reviews.md): on or off, the time in half hours, and for the weekly one the
 * weekday it rings on. Switching it back on starts again at 18:00.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewReminderRow(
    title: String,
    hint: String,
    time: LocalTime?,
    weekday: Int?,
    onTime: (LocalTime?) -> Unit,
    onWeekday: (Int) -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val locale = LocalConfiguration.current.locales[0]
    SwitchRow(
        title = title,
        hint = if (time == null) hint else stringResource(R.string.settings_plan_reminder_on, formatter.format(time)),
        checked = time != null,
        onCheckedChange = { on -> onTime(if (on) ReviewReminder.DEFAULT_TIME else null) },
    )
    if (time != null) {
        Slider(
            value = (time.toSecondOfDay() / HALF_HOUR).toFloat(),
            onValueChange = { onTime(LocalTime.ofSecondOfDay(it.roundToInt() * HALF_HOUR.toLong())) },
            valueRange = 0f..47f,
            steps = 46,
        )
        if (weekday != null) {
            Label(stringResource(R.string.settings_weekly_review_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    ChoiceChip(
                        selected = weekday == day.value,
                        onClick = { onWeekday(day.value) },
                        label = day.getDisplayName(TextStyle.SHORT, locale),
                    )
                }
            }
        }
    }
}

private const val HALF_HOUR = 1_800

/**
 * Quiet hours (docs/reminders.md): an hour to start and an hour to end, and a way to switch them
 * off. Equal hours mean off, which is what the sliders say when they meet.
 */
@Composable
private fun QuietHoursRow(window: QuietHours, onChange: (QuietHours) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    Label(stringResource(R.string.settings_quiet_hours))
    Text(
        text = if (window.off) {
            stringResource(R.string.settings_quiet_hours_off)
        } else {
            stringResource(R.string.settings_quiet_hours_window, formatter.format(window.start), formatter.format(window.end))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Label(stringResource(R.string.settings_quiet_hours_start))
    Slider(
        value = window.start.hour.toFloat(),
        onValueChange = { onChange(window.copy(start = LocalTime.of(it.roundToInt(), 0))) },
        valueRange = 0f..23f,
        steps = 22,
    )
    Label(stringResource(R.string.settings_quiet_hours_end))
    Slider(
        value = window.end.hour.toFloat(),
        onValueChange = { onChange(window.copy(end = LocalTime.of(it.roundToInt(), 0))) },
        valueRange = 0f..23f,
        steps = 22,
    )
    if (!window.off) {
        OutlinedButton(onClick = { onChange(QuietHours.OFF) }) {
            Text(stringResource(R.string.settings_quiet_hours_clear))
        }
    }
}
