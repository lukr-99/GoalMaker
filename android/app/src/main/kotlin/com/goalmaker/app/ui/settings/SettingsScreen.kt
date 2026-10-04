package com.goalmaker.app.ui.settings

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.application.planning.WhyFrequency
import com.goalmaker.app.R
import com.goalmaker.app.application.auth.UnlockAvailability
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.DndBreakthrough
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.update.InstallResult
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.application.update.UpdateRequest
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.ReviewReminder
import com.goalmaker.app.domain.planning.RitualReminder
import com.goalmaker.app.domain.problems.Problem
import com.goalmaker.app.domain.problems.ProblemRules
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.SettingsFieldRules
import com.goalmaker.app.domain.settings.SettingsPageRules
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings (docs/design/spec.md, Settings): one scrolling page of section cards, each a title, a
 * one-line description and rows from the row kit, with a sticky chip row to jump between them.
 * Every change saves at once and says so next to its control. A jump (a chip, or the update
 * notification landing on Updates) scrolls there and lights the card up; scrolling into a section
 * by hand gives a quieter hint once the page is still.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAreas: () -> Unit,
    onOpenConnector: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    problems: List<Problem> = emptyList(),
    onProblemsRead: () -> Unit = {},
    /** What the update notification asked for: Settings jumps to Updates, and Install starts. */
    updateRequest: UpdateRequest? = null,
    onUpdateRequestHandled: () -> Unit = {},
    /** The areas in use and the tags, shown in their own section with the way to the full manager. */
    areas: List<AreaItem> = emptyList(),
    tags: List<TagItem> = emptyList(),
    /** Opens the system page that lets important reminders through Do Not Disturb, or not. */
    onOpenDndSettings: (DndBreakthrough) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.counts.collectAsStateWithLifecycle()
    // Opening Settings is reading them, so the mark on the gear goes (docs/problems.md).
    LaunchedEffect(Unit) { onProblemsRead() }
    val sections = SettingsSections.visible(hasProblems = problems.isNotEmpty(), devBuild = state.appInfo.isDevBuild)
    val scroll = rememberScrollState()
    // Where each section's card starts in the scrolling column, measured as it is placed.
    val tops = remember { mutableStateMapOf<SettingsSection, Int>() }
    val density = LocalDensity.current
    val line = with(density) { SettingsPageRules.CURRENT_LINE.dp.roundToPx() }
    val reduced by rememberUpdatedState(AppTheme.reduceMotion)
    val highlighter = remember { SectionHighlighter<SettingsSection> { reduced } }
    val current by remember(sections) {
        derivedStateOf { SettingsSections.current(sections, tops, scroll.value, scroll.maxValue, line, highlighter.pinned) }
    }
    val titleFocus = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var jumpJob by remember { mutableStateOf<Job?>(null) }

    // Scrolls to [section] in 250 to 450 ms (instant with reduce motion), then lights it up and
    // moves focus to its title. A finger on the page stops it, and then no hint plays.
    suspend fun jumpTo(section: SettingsSection) {
        // The cards are measured on the first frame; a deep link waits for the one it wants.
        val top = tops[section] ?: snapshotFlow { tops[section] }.filterNotNull().first()
        val target = top.coerceIn(0, scroll.maxValue)
        val distance = with(density) { abs(target - scroll.value).toDp().value.roundToInt() }
        highlighter.jumpStarted(section)
        val millis = SettingsPageRules.jumpScrollMillis(distance, reduced)
        try {
            if (millis == 0) scroll.scrollTo(target) else scroll.animateScrollTo(target, tween(millis, easing = LinearOutSlowInEasing))
        } catch (stopped: CancellationException) {
            highlighter.jumpCancelled(section)
            throw stopped
        }
        highlighter.jumpLanded(section)
        runCatching { titleFocus.getValue(section).requestFocus() }
    }

    fun jump(section: SettingsSection) {
        jumpJob?.cancel()
        jumpJob = scope.launch { jumpTo(section) }
    }

    LaunchedEffect(updateRequest) {
        val target = SettingsSections.target(updateRequest) ?: return@LaunchedEffect
        jump(target)
        if (updateRequest == UpdateRequest.INSTALL) viewModel.installRequested()
        onUpdateRequestHandled()
    }
    LaunchedEffect(Unit) {
        highlighter.opened(snapshotFlow { current.takeIf { tops.isNotEmpty() } }.filterNotNull().first())
    }
    // The owner's own scrolling: it lets go of a landed jump, and once the page has been still for
    // a moment the section that ended current gets the scroll hint.
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.isScrollInProgress }.collectLatest { moving ->
            if (moving) {
                highlighter.scrolledByHand()
            } else {
                delay(SettingsPageRules.SCROLL_HINT_IDLE_MILLIS)
                highlighter.settled(current)
            }
        }
    }

    fun Modifier.section(section: SettingsSection) = onPlaced { tops[section] = it.positionInParent().y.roundToInt() }
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
                // The keyboard shrinks the page, so the field being typed in can scroll above it.
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (SettingsSections.showChips(sections)) {
                SectionChips(sections = sections, current = current, onJump = ::jump)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val hint = highlighter.hint
                sections.forEach { section ->
                    key(section) {
                        SettingsCard(section, hint, titleFocus.getValue(section), Modifier.section(section)) {
                            when (section) {
                                SettingsSection.PROBLEMS -> problems.forEachIndexed { index, problem ->
                                    if (index > 0) RowDivider()
                                    ProblemRow(problem)
                                }
                                SettingsSection.ACCOUNT -> AccountRows(state, saved, viewModel)
                                SettingsSection.APPEARANCE -> AppearanceRows(state, saved, viewModel)
                                SettingsSection.PLANNING -> PlanningRows(state, saved, viewModel, onOpenDndSettings)
                                SettingsSection.AREAS -> AreasAndTagsCard(areas = areas, tags = tags, onOpenAreas = onOpenAreas)
                                SettingsSection.CLAUDE -> {
                                    LinkRow(
                                        stringResource(R.string.connector_open),
                                        stringResource(R.string.connector_open_hint),
                                        external = false,
                                        onClick = onOpenConnector,
                                    )
                                    RowDivider()
                                    LinkRow(
                                        stringResource(R.string.activity_open),
                                        stringResource(R.string.activity_open_hint),
                                        external = false,
                                        onClick = onOpenActivity,
                                    )
                                }
                                SettingsSection.DATA -> BackupCard(viewModel, state.backup)
                                SettingsSection.UPDATES -> UpdatesRows(state, viewModel)
                                SettingsSection.ABOUT -> {
                                    InfoRow(stringResource(R.string.settings_version), state.appInfo.versionName)
                                    RowDivider()
                                    InfoRow(stringResource(R.string.settings_backend), state.appInfo.backend.url)
                                }
                                SettingsSection.DEVELOPER -> DeveloperRows(state, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The jump list under the title: a chip for each section, the one being read filled with primary
 * and kept in sight, and a tap jumps to that section. It stays put while the page scrolls under it.
 */
@Composable
private fun SectionChips(sections: List<SettingsSection>, current: SettingsSection?, onJump: (SettingsSection) -> Unit) {
    val list = rememberLazyListState()
    val label = stringResource(R.string.settings_sections)
    val reduced = AppTheme.reduceMotion
    LaunchedEffect(current, sections) {
        val index = sections.indexOf(current)
        if (index < 0) return@LaunchedEffect
        val info = list.layoutInfo
        val whole = info.visibleItemsInfo.any { it.index == index && it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
        if (whole) return@LaunchedEffect
        val to = (index - 1).coerceAtLeast(0)
        if (reduced) list.scrollToItem(to) else list.animateScrollToItem(to)
    }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections, key = { it.name }) { section ->
            val title = stringResource(section.title)
            val jumpTo = stringResource(R.string.settings_jump_to, title)
            FilterChip(
                selected = section == current,
                onClick = { onJump(section) },
                label = { Text(title) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppTheme.colors.primary,
                    selectedLabelColor = AppTheme.colors.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = section == current,
                    borderColor = AppTheme.colors.outline,
                    selectedBorderColor = AppTheme.colors.primary,
                ),
                modifier = Modifier.semantics { contentDescription = jumpTo },
            )
        }
    }
}

@Composable
private fun AccountRows(state: SettingsUiState, saved: Map<SettingKey, Int>, viewModel: SettingsViewModel) {
    // A dev build that stays on the phone has no account to show or leave (docs/sign-in.md).
    if (state.appInfo.localOnly) {
        Text(
            stringResource(R.string.settings_local_only),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    } else {
        InfoRow(stringResource(R.string.settings_signed_in_as), state.email)
    }
    RowDivider()
    // The owner can go and enrol a fingerprint and come straight back, which resumes this window
    // rather than building it again, so asking once would read stale.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkUnlock() }
    ToggleRow(
        title = stringResource(R.string.settings_app_lock),
        hint = stringResource(appLockHint(state.unlock)),
        checked = state.appLock,
        enabled = state.unlock == UnlockAvailability.READY || state.appLock,
        onCheckedChange = viewModel::setAppLock,
        saved = saved[SettingKey.APP_LOCK],
    )
    if (state.appInfo.localOnly) return
    RowDivider()
    val unsynced = state.unsyncedAtSignOut
    ButtonRow(
        title = stringResource(R.string.settings_sign_out),
        hint = stringResource(R.string.settings_sign_out_hint),
        button = stringResource(R.string.settings_sign_out),
        onClick = { viewModel.signOut() },
        result = unsynced?.let { pluralStringResource(R.plurals.settings_sign_out_unsynced, it, it) },
        trouble = true,
        busy = state.signingOut,
        busyLabel = stringResource(R.string.settings_signing_out),
    )
    if (unsynced != null) {
        var asking by remember { mutableStateOf(false) }
        DangerZone {
            DangerRow(
                title = stringResource(R.string.settings_sign_out_anyway),
                hint = stringResource(R.string.settings_sign_out_anyway_hint),
                button = stringResource(R.string.settings_sign_out_anyway),
                onClick = { asking = true },
                enabled = !state.signingOut,
            )
        }
        if (asking) {
            ConfirmDialog(
                title = stringResource(R.string.settings_sign_out_anyway_ask),
                text = pluralStringResource(R.plurals.settings_sign_out_anyway_lost, unsynced, unsynced),
                confirm = stringResource(R.string.settings_sign_out_anyway),
                onConfirm = {
                    asking = false
                    viewModel.signOut(discardUnsynced = true)
                },
                onDismiss = { asking = false },
            )
        }
    }
}

@Composable
private fun AppearanceRows(state: SettingsUiState, saved: Map<SettingKey, Int>, viewModel: SettingsViewModel) {
    // The mark takes on the theme's colors, and redraws itself when the theme changes.
    ChoiceCardsRow(
        title = stringResource(R.string.settings_theme),
        saved = saved[SettingKey.THEME],
        trailing = { GoalMakerLogo(size = 44.dp) },
    ) {
        ThemePicker(
            themes = state.themes,
            selectedId = state.themeId,
            dark = AppTheme.colors.isDark,
            onSelect = { if (it != state.themeId) viewModel.setTheme(it) },
        )
    }
    RowDivider()
    SegmentedRow(
        title = stringResource(R.string.settings_mode),
        hint = null,
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        ),
        selected = state.appearance.mode,
        onSelect = viewModel::setThemeMode,
        saved = saved[SettingKey.MODE],
    )
    RowDivider()
    // Pure black only shows in dark mode, so in light mode the row says why it can't be used.
    val light = state.appearance.mode == ThemeMode.LIGHT
    ToggleRow(
        title = stringResource(R.string.settings_pure_black),
        hint = stringResource(if (light) R.string.settings_pure_black_disabled else R.string.settings_pure_black_hint),
        checked = state.appearance.pureBlack,
        enabled = !light,
        onCheckedChange = viewModel::setPureBlack,
        saved = saved[SettingKey.PURE_BLACK],
    )
    RowDivider()
    SegmentedRow(
        title = stringResource(R.string.settings_reduce_motion),
        hint = stringResource(R.string.settings_reduce_motion_hint),
        options = listOf(
            ReduceMotion.SYSTEM to stringResource(R.string.settings_theme_system),
            ReduceMotion.ON to stringResource(R.string.settings_reduce_motion_on),
            ReduceMotion.OFF to stringResource(R.string.settings_reduce_motion_off),
        ),
        selected = state.appearance.reduceMotion,
        onSelect = viewModel::setReduceMotion,
        saved = saved[SettingKey.REDUCE_MOTION],
    )
    RowDivider()
    ToggleRow(
        title = stringResource(R.string.settings_completion_sound),
        hint = stringResource(R.string.settings_completion_sound_hint),
        checked = state.appearance.completionSound,
        onCheckedChange = viewModel::setCompletionSound,
        saved = saved[SettingKey.COMPLETION_SOUND],
    )
}

@Composable
private fun PlanningRows(
    state: SettingsUiState,
    saved: Map<SettingKey, Int>,
    viewModel: SettingsViewModel,
    onOpenDndSettings: (DndBreakthrough) -> Unit,
) {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val locale = LocalConfiguration.current.locales[0]
    DropdownRow(
        title = stringResource(R.string.settings_day_start),
        hint = stringResource(R.string.settings_day_start_hint),
        options = PlanningDay.START_HOURS.map { it to "%02d:00".format(it) },
        selected = state.dayStartHour,
        onSelect = viewModel::setDayStartHour,
        saved = saved[SettingKey.DAY_START],
    )
    RowDivider()
    // The evening Plan tomorrow reminder (docs/reminders.md); switching it back on starts at 20:00.
    val plan = state.planTomorrowReminder
    ReminderRows(
        title = stringResource(R.string.settings_plan_reminder),
        hint = if (plan == null) {
            stringResource(R.string.settings_plan_reminder_off)
        } else {
            stringResource(R.string.settings_plan_reminder_on, clock.format(plan))
        },
        time = plan,
        defaultTime = RitualReminder.DEFAULT_TIME,
        onTime = viewModel::setPlanTomorrowReminder,
        saved = saved[SettingKey.PLAN_REMINDER],
        savedTime = saved[SettingKey.PLAN_REMINDER_TIME],
    )
    RowDivider()
    // The review reminders (docs/reviews.md); switching one back on starts at 18:00.
    val weekly = state.weeklyReviewReminder
    ReminderRows(
        title = stringResource(R.string.settings_weekly_review),
        hint = weekly?.let { stringResource(R.string.settings_plan_reminder_on, clock.format(it)) }
            ?: stringResource(R.string.settings_weekly_review_hint),
        time = weekly,
        defaultTime = ReviewReminder.DEFAULT_TIME,
        onTime = viewModel::setWeeklyReviewReminder,
        saved = saved[SettingKey.WEEKLY_REVIEW],
        savedTime = saved[SettingKey.WEEKLY_REVIEW_TIME],
    )
    if (weekly != null) {
        RowDivider()
        DropdownRow(
            title = stringResource(R.string.settings_weekly_review_day),
            hint = null,
            options = DayOfWeek.entries.map { it.value to it.getDisplayName(TextStyle.FULL, locale) },
            selected = state.weeklyReviewWeekday,
            onSelect = viewModel::setWeeklyReviewWeekday,
            saved = saved[SettingKey.WEEKLY_REVIEW_DAY],
        )
    }
    RowDivider()
    val monthly = state.monthlyReviewReminder
    ReminderRows(
        title = stringResource(R.string.settings_monthly_review),
        hint = monthly?.let { stringResource(R.string.settings_plan_reminder_on, clock.format(it)) }
            ?: stringResource(R.string.settings_monthly_review_hint),
        time = monthly,
        defaultTime = ReviewReminder.DEFAULT_TIME,
        onTime = viewModel::setMonthlyReviewReminder,
        saved = saved[SettingKey.MONTHLY_REVIEW],
        savedTime = saved[SettingKey.MONTHLY_REVIEW_TIME],
    )
    RowDivider()
    val wants = state.wantsReadyReminder
    ReminderRows(
        title = stringResource(R.string.settings_wants_ready),
        hint = wants?.let { stringResource(R.string.settings_plan_reminder_on, clock.format(it)) }
            ?: stringResource(R.string.settings_wants_ready_hint),
        time = wants,
        defaultTime = ReviewReminder.DEFAULT_TIME,
        onTime = viewModel::setWantsReadyReminder,
        saved = saved[SettingKey.WANTS_READY],
        savedTime = saved[SettingKey.WANTS_READY_TIME],
    )
    RowDivider()
    // The why reminder (docs/life-goals.md): one life goal now and then, at a moment worked out per period.
    DropdownRow(
        title = stringResource(R.string.settings_why_reminder),
        hint = stringResource(R.string.settings_why_reminder_hint),
        options = listOf(
            WhyFrequency.OFF to stringResource(R.string.settings_why_off),
            WhyFrequency.WEEKLY to stringResource(R.string.settings_why_weekly),
            WhyFrequency.EVERY_3_DAYS to stringResource(R.string.settings_why_every_3_days),
            WhyFrequency.DAILY to stringResource(R.string.settings_why_daily),
        ),
        selected = state.whyReminder,
        onSelect = viewModel::setWhyReminder,
        saved = saved[SettingKey.WHY_REMINDER],
    )
    RowDivider()
    QuietHoursRows(state.quietHours, saved, viewModel::setQuietHours)
    RowDivider()
    // Important reminders through Do Not Disturb (docs/reminders.md): only the owner can allow it, on
    // the system page; coming back from there resumes this window, so it asks the phone again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkImportantDnd() }
    val dnd = state.importantDnd
    ButtonRow(
        title = stringResource(R.string.settings_important_dnd),
        hint = stringResource(
            when (dnd) {
                DndBreakthrough.ALLOWED -> R.string.settings_important_dnd_allowed
                DndBreakthrough.NOT_ALLOWED -> R.string.settings_important_dnd_not_allowed
                DndBreakthrough.CHANNEL_OFF -> R.string.settings_important_dnd_channel_off
                DndBreakthrough.NOTIFICATIONS_OFF -> R.string.settings_important_dnd_notifications_off
            },
        ),
        button = stringResource(if (dnd == DndBreakthrough.ALLOWED) R.string.settings_important_dnd_change else R.string.settings_important_dnd_allow),
        onClick = { onOpenDndSettings(dnd) },
    )
}

/** A reminder: a switch, and while it is on the time in half hours, saved when the slider is let go. */
@Composable
private fun ReminderRows(
    title: String,
    hint: String,
    time: LocalTime?,
    defaultTime: LocalTime,
    onTime: (LocalTime?) -> Unit,
    saved: Int?,
    savedTime: Int?,
) {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    ToggleRow(
        title = title,
        hint = hint,
        checked = time != null,
        onCheckedChange = { on -> onTime(if (on) defaultTime else null) },
        saved = saved,
    )
    if (time != null) {
        SliderRow(
            title = stringResource(R.string.settings_reminder_time),
            hint = null,
            value = time.toSecondOfDay() / HALF_HOUR,
            range = 0..47,
            valueText = { clock.format(LocalTime.ofSecondOfDay(it * HALF_HOUR.toLong())) },
            onRelease = { onTime(LocalTime.ofSecondOfDay(it * HALF_HOUR.toLong())) },
            saved = savedTime,
        )
    }
}

private const val HALF_HOUR = 1_800

/**
 * Quiet hours (docs/reminders.md): when they start and end, typed as times and saved on Enter or
 * when the field is left, and a way to switch them off. Equal times mean off.
 */
@Composable
private fun QuietHoursRows(window: QuietHours, saved: Map<SettingKey, Int>, onChange: (QuietHours) -> Unit) {
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val start = remember { CommittedField(clock.format(window.start), SettingsFieldRules::time, clock::format) }
    val end = remember { CommittedField(clock.format(window.end), SettingsFieldRules::time, clock::format) }
    LaunchedEffect(window) {
        start.stored(clock.format(window.start), editing = false)
        end.stored(clock.format(window.end), editing = false)
    }
    ButtonRow(
        title = stringResource(R.string.settings_quiet_hours),
        hint = if (window.off) {
            stringResource(R.string.settings_quiet_hours_off)
        } else {
            stringResource(R.string.settings_quiet_hours_window, clock.format(window.start), clock.format(window.end))
        },
        button = stringResource(R.string.settings_quiet_hours_clear),
        onClick = { onChange(QuietHours.OFF) },
        enabled = !window.off,
    )
    RowDivider()
    val invalid = stringResource(R.string.settings_time_invalid)
    TextFieldRow(
        title = stringResource(R.string.settings_quiet_hours_start),
        hint = stringResource(R.string.settings_quiet_hours_start_hint),
        field = start,
        error = invalid,
        onCommit = { onChange(window.copy(start = it)) },
        saved = saved[SettingKey.QUIET_START],
    )
    RowDivider()
    TextFieldRow(
        title = stringResource(R.string.settings_quiet_hours_end),
        hint = stringResource(R.string.settings_quiet_hours_end_hint),
        field = end,
        error = invalid,
        onCommit = { onChange(window.copy(end = it)) },
        saved = saved[SettingKey.QUIET_END],
    )
}

@Composable
private fun UpdatesRows(state: SettingsUiState, viewModel: SettingsViewModel) {
    val update = state.update
    val waiting = state.waitingUpdate
    val checkedAt = state.updatesCheckedAt
    if (waiting != null && update !is UpdateUiState.Checking && update !is UpdateUiState.Downloading) {
        UpdateWaitingRow(
            update = waiting,
            downloaded = state.updateDownloaded,
            postponedUntil = state.updatePostponedUntil,
            onInstall = { viewModel.installUpdate(waiting) },
            onLater = { viewModel.postponeUpdate(waiting) },
        )
        RowDivider()
    }
    when (update) {
        is UpdateUiState.Downloading -> {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearWavyProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_update_downloading, (update.progress * 100).toInt()))
            }
            RowDivider()
        }
        is UpdateUiState.Installing -> {
            Text(
                when (val result = update.result) {
                    InstallResult.InstallerOpened -> stringResource(R.string.settings_update_opened)
                    InstallResult.DownloadCorrupted -> stringResource(R.string.settings_update_corrupted)
                    is InstallResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            RowDivider()
        }
        else -> Unit
    }
    // GoalMaker also looks on its own once a day; the hint says when a check last got through.
    val lastChecked = if (viewModel.releasesPage != null && checkedAt != null) {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
        stringResource(R.string.settings_update_last_checked, formatter.format(checkedAt))
    } else {
        stringResource(R.string.settings_update_check_hint)
    }
    val result = (update as? UpdateUiState.Checked)?.result
    ButtonRow(
        title = stringResource(R.string.settings_check_updates),
        hint = lastChecked,
        button = stringResource(R.string.settings_check_now),
        onClick = viewModel::checkForUpdates,
        // An update found is said once, by the accent row above, with its install button.
        result = result?.takeIf { it !is UpdateCheckResult.Available }?.let { checkMessage(it) },
        trouble = result is UpdateCheckResult.Failed || result is UpdateCheckResult.Untrusted,
        busy = update is UpdateUiState.Checking || update is UpdateUiState.Downloading,
        busyLabel = stringResource(R.string.settings_update_checking),
    )
    viewModel.releasesPage?.let { page ->
        val links = LocalUriHandler.current
        RowDivider()
        LinkRow(
            title = stringResource(R.string.settings_update_download),
            hint = stringResource(R.string.settings_update_manual),
            external = true,
            onClick = { links.openUri(page) },
        )
    }
}

@Composable
private fun checkMessage(result: UpdateCheckResult): String = when (result) {
    UpdateCheckResult.NotConfigured -> stringResource(R.string.settings_update_not_configured)
    UpdateCheckResult.DevelopmentBuild -> stringResource(R.string.settings_update_dev_build)
    is UpdateCheckResult.UpToDate -> stringResource(R.string.settings_update_up_to_date, result.latest)
    is UpdateCheckResult.Available -> stringResource(R.string.settings_update_available, result.manifest.version.toString())
    UpdateCheckResult.Untrusted -> stringResource(R.string.settings_update_untrusted)
    is UpdateCheckResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
}

/**
 * Dev builds only: sign in or stay on the phone, and the backend to use. The address is checked
 * when the field is left or Enter is pressed, and Save and restart never saves a bad one.
 */
@Composable
private fun DeveloperRows(state: SettingsUiState, viewModel: SettingsViewModel) {
    ToggleRow(
        title = stringResource(R.string.settings_dev_sign_in),
        hint = stringResource(R.string.settings_dev_sign_in_hint),
        checked = !state.appInfo.localOnly,
        onCheckedChange = viewModel::setDevSignIn,
    )
    RowDivider()
    val url = remember { CommittedField(state.backendUrlDraft, { it.trim().takeIf(SettingsFieldRules::backendUrl) }, { it }) }
    val key = remember { CommittedField(state.backendKeyDraft, { it.trim().takeIf(String::isNotEmpty) }, { it }) }
    TextFieldRow(
        title = stringResource(R.string.settings_backend_url),
        hint = stringResource(R.string.settings_backend_hint),
        field = url,
        error = stringResource(R.string.settings_backend_url_invalid),
        onCommit = viewModel::onBackendUrlChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    RowDivider()
    TextFieldRow(
        title = stringResource(R.string.settings_backend_key),
        hint = stringResource(R.string.settings_backend_key_hint),
        field = key,
        error = stringResource(R.string.settings_backend_key_invalid),
        onCommit = viewModel::onBackendKeyChange,
    )
    RowDivider()
    ButtonRow(
        title = stringResource(R.string.settings_backend_save_title),
        hint = stringResource(R.string.settings_backend_save_hint),
        button = stringResource(R.string.settings_backend_save),
        onClick = {
            // A field still being typed in is checked first; a bad value stops the save.
            url.commit()?.let(viewModel::onBackendUrlChange)
            key.commit()?.let(viewModel::onBackendKeyChange)
            if (!url.invalid && !key.invalid) viewModel.saveBackend()
        },
        extra = { TextButton(onClick = viewModel::resetBackend) { Text(stringResource(R.string.settings_backend_reset)) } },
    )
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
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** A switch row for the other screens' dialogs and sheets; Settings itself uses [ToggleRow]. */
@Composable
internal fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

// Why the lock cannot be turned on, or what it does when it can.
private fun appLockHint(unlock: UnlockAvailability) = when (unlock) {
    UnlockAvailability.READY -> R.string.settings_app_lock_hint
    UnlockAvailability.NOTHING_ENROLLED -> R.string.settings_app_lock_nothing_enrolled
    UnlockAvailability.UNAVAILABLE -> R.string.settings_app_lock_unavailable
}

/**
 * The update the last check found, as the mark on the gear promised: an accent block with a
 * download icon, the version, the install button and Later. It stays until a check finds none or
 * the new version starts; Later only hides the mark and the notification, so Install stays here.
 */
@Composable
private fun UpdateWaitingRow(
    update: UpdateCheckResult.Available,
    downloaded: Boolean,
    postponedUntil: Instant?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val version = update.manifest.version.toString()
    val accent = AppTheme.colors.accent
    Column(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_update_available, version),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.text,
                )
                if (downloaded) {
                    Text(
                        stringResource(R.string.settings_update_downloaded),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
                if (postponedUntil != null) {
                    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                    Text(
                        stringResource(R.string.settings_update_postponed, formatter.format(postponedUntil)),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onInstall) {
                Text(stringResource(R.string.settings_install_update, version))
            }
            if (postponedUntil == null) {
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.settings_update_later))
                }
            }
        }
    }
}
