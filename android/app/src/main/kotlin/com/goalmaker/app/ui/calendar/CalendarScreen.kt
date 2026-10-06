package com.goalmaker.app.ui.calendar

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.EventItem
import com.goalmaker.app.application.planning.EventRules
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.ui.components.AppSnackbarHost
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProjectChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.composer.BottomComposer
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.habits.AmountDialog
import com.goalmaker.app.ui.habits.HabitCard
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.habits.HabitSheet
import com.goalmaker.app.ui.lists.ListFilterRow
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.lists.UndoEvent
import com.goalmaker.app.ui.nav.AppMark
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A week or a month of planned tasks, deadlines and reminders, with events drawn as bars across their
 * days (docs/calendar.md). An event on the open day opens the event sheet. The bottom bar adds a task
 * or an event to the open day; a long-press on a day starts picking several, which taps and a drag
 * add to, and Back or Done leaves. The bar's switch to the quick chat is [chat]'s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    chat: ChatViewModel,
    onOpenTask: (String) -> Unit,
    actions: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenProject: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf<HabitRow?>(null) }
    var habitMenu by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EventItem?>(null) }
    var newEvent by remember { mutableStateOf(false) }
    val composer = rememberTextFieldState()
    val chatState by chat.uiState.collectAsStateWithLifecycle()
    // Where each day's cell sits on the screen, so a drag that starts on one cell can pick the ones it crosses.
    val cells = remember { mutableMapOf<LocalDate, Rect>() }
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // A newer undo takes the place of the one showing, so the snackbar always takes back the last add.
    LaunchedEffect(viewModel) {
        viewModel.undo.collectLatest { event ->
            val message = resources.getString(
                when (event.kind) {
                    UndoEvent.Kind.DONE -> R.string.lists_done_message
                    UndoEvent.Kind.ADDED -> R.string.calendar_added_message
                    else -> R.string.lists_deleted_message
                },
                event.title,
            )
            val result = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.lists_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }

    // A habit's button on the open day: undo a skip or a fail, or check in; an amount asks for its value.
    fun checkIn(row: HabitRow, day: LocalDate) {
        when {
            row.skipped -> viewModel.skipHabit(row.habit.id, day, false)
            row.failed -> viewModel.failHabit(row.habit.id, day, false)
            else -> scope.launch { if (!viewModel.tapHabit(row.habit.id, day)) logging = row }
        }
    }

    // Back leaves picking first, going back to one day.
    BackHandler(enabled = state.picking) { viewModel.stopPicking() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AppSnackbarHost(snackbars) },
        bottomBar = {
            if (state.loaded) {
                val line = composer.text.toString()
                val draft = remember(line, state.several) { viewModel.preview(line) }
                val event = state.addsEvent
                // Under MainScreen's bar the navigation bar is already taken, so this adds nothing there.
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    if (!chatState.chatting) {
                        AddKindRow(
                            picking = state.picking,
                            days = state.addDays.size,
                            event = event,
                            tooWide = event && state.eventSpan == null,
                            onChoose = viewModel::chooseEvent,
                            onDone = viewModel::stopPicking,
                        )
                    }
                    BottomComposer(
                        state = composer,
                        chat = chat,
                        // An event's line is only its title, so nothing in it is read as a shortcut.
                        chips = if (event) emptyList() else composerChips(line, draft, viewModel.today(), state.areas, state.filter.tags.map { it.name }, state.projects),
                        canAdd = if (event) {
                            line.isNotBlank() && line.trim().length <= EventRules.MAX_TITLE && state.eventSpan != null
                        } else {
                            draft.title.isNotBlank() && draft.command == null
                        },
                        onAdd = { if (viewModel.add(line)) composer.clearText() },
                        onRemove = if (event) null else { chip -> composer.setTextAndPlaceCursorAtEnd(removeParts(line, chip.spans)) },
                        placeholder = stringResource(if (event) R.string.calendar_event_placeholder else R.string.today_composer_placeholder),
                        addLabel = stringResource(if (event) R.string.calendar_add_event_send else R.string.today_add),
                        formLabel = stringResource(R.string.event_add),
                        // The plus opens the new event sheet over the picked days; a task has no form of its own here.
                        onOpenForm = if (event) ({ newEvent = true }) else null,
                    )
                }
            }
        },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.calendar_title)) },
                subtitle = { Text(period(state, locale), maxLines = 1) },
                navigationIcon = { PlaceNavigationIcon(onBack) { AppMark() } },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // The period's own controls sit over it, since the top bar carries what every place does.
            item("kind") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChip(
                        selected = state.kind == CalendarRules.WEEK,
                        onClick = { viewModel.show(CalendarRules.WEEK) },
                        label = stringResource(R.string.calendar_week),
                    )
                    ChoiceChip(
                        selected = state.kind == CalendarRules.MONTH,
                        onClick = { viewModel.show(CalendarRules.MONTH) },
                        label = stringResource(R.string.calendar_month),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.calendar_back))
                    }
                    IconButton(onClick = { viewModel.today(true) }) {
                        Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.calendar_today))
                    }
                    IconButton(onClick = viewModel::forward) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.calendar_forward))
                    }
                }
            }

            // The area and tag filter narrows what the grid counts and the day lists, as on the lists.
            item("filter") {
                ListFilterRow(state.filter, onArea = viewModel::filterByArea, onTag = viewModel::filterByTag)
            }

            item("weekdays") { WeekdayRow(locale) }

            state.weeks.forEachIndexed { index, week ->
                item("week-$index") {
                    val bars = state.bars.getOrElse(index) { emptyList() }
                    val space = eventSpace(bars)
                    // A week's days share one height, the tallest a cell needs at the system's text size.
                    // The row's event bars lie over the bottom of its cells, which grow to hold them.
                    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                DayCell(
                                    day = day,
                                    today = day.day == state.today,
                                    tomorrow = day.day == state.today.plusDays(1),
                                    inPeriod = state.kind == CalendarRules.WEEK || day.day.month == state.anchor.month,
                                    selected = day.day == state.selected,
                                    picked = day.day in state.picked,
                                    onClick = { viewModel.open(day.day) },
                                    onLongClick = { viewModel.startPicking(day.day) },
                                    onDragTo = { point ->
                        cells.entries.firstOrNull { it.value.contains(point) }?.let { viewModel.pickRun(day.day, it.key) }
                                    },
                                    onPlaced = { bounds -> cells[day.day] = bounds },
                                    onDropTask = { id -> viewModel.plan(id, day.day) },
                                    modifier = Modifier.weight(1f),
                                    eventSpace = space,
                                )
                            }
                        }
                        EventLanes(
                            bars = bars,
                            areaOf = state::areaOf,
                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 4.dp),
                        )
                    }
                }
            }

            val open = state.openDay
            if (open == null) {
                item("hint") {
                    Text(
                        stringResource(R.string.calendar_pick_a_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                item("day-header") {
                    SectionHeader(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(open.day))
                }
                if (open.empty) {
                    item("day-empty") {
                        Text(
                            stringResource(R.string.calendar_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textMuted,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                item("drag-hint") {
                    Text(
                        stringResource(R.string.calendar_drag_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // The day's events come first: they take the whole day, and a tap opens the event sheet.
                open.events.forEach { event ->
                    item("event-" + event.id) { EventRow(event, state.areaOf(event.areaId)) { editing = event } }
                }
                if (state.dayHabits.isNotEmpty()) {
                    item("h-habits") { SectionHeader(stringResource(R.string.calendar_habits)) }
                    state.dayHabits.forEach { row ->
                        item("habit-" + row.habit.id) {
                            HabitCard(
                                row = row,
                                today = open.day,
                                full = false,
                                onCheckIn = { checkIn(row, open.day) },
                                onMenu = { habitMenu = row.habit.id },
                                onSkip = { viewModel.skipHabit(row.habit.id, open.day, true) },
                            )
                        }
                    }
                }
                val onDone = { task: TaskItem, done: Boolean -> viewModel.setDone(task, done, open.day) }
                open.planned.forEach { task ->
                    item("planned-" + task.id) {
                        DayRow(task, R.string.calendar_planned, onOpenTask, state.projectOf(task), onOpenProject, draggable = true, onDone = onDone)
                    }
                }
                open.deadlines.forEach { task ->
                    item("deadline-" + task.id) { DayRow(task, R.string.calendar_deadline, onOpenTask, state.projectOf(task), onOpenProject, onDone = onDone) }
                }
                open.repeats.forEach { task ->
                    item("repeat-" + task.id) { DayRow(task, R.string.calendar_repeat, onOpenTask, state.projectOf(task), onOpenProject) }
                }
                if (open.reminders > 0) {
                    item("reminders") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(
                                Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(R.string.calendar_reminders, open.reminders),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textMuted,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (newEvent) {
        val span = state.eventSpan
        val first = span?.start ?: state.addDays.firstOrNull() ?: state.today
        EventSheet(
            initial = null,
            firstDay = first,
            lastDay = span?.endInclusive ?: first,
            areas = state.areas,
            onSave = { draft -> viewModel.saveEvent(null, draft) },
            onDelete = null,
            onDismiss = { newEvent = false },
        )
    }

    editing?.let { event ->
        EventSheet(
            initial = event,
            firstDay = event.startsOn,
            areas = state.areas,
            onSave = { draft -> viewModel.saveEvent(event, draft) },
            onDelete = { viewModel.deleteEvent(event) },
            onDismiss = { editing = null },
        )
    }

    val day = state.openDay?.day
    logging?.let { row ->
        if (day != null) AmountDialog(row.habit, onLog = { amount -> viewModel.checkInHabit(row.habit.id, day, amount) }, onDismiss = { logging = null })
    }
    // A habit's menu on the open day: check in, skip, fail or clear there; pausing and editing stay on Habits.
    habitMenu?.let { id ->
        val row = state.dayHabits.firstOrNull { it.habit.id == id }
        if (row != null && day != null) {
            HabitSheet(
                row = row,
                onDismiss = { habitMenu = null },
                onCheckIn = { checkIn(row, day) },
                onLog = { logging = row },
                onSkip = { skipped -> viewModel.skipHabit(id, day, skipped) },
                onFail = { failed -> viewModel.failHabit(id, day, failed) },
                onClear = { viewModel.clearHabit(id, day) },
            )
        }
    }
}

@Composable
private fun WeekdayRow(locale: java.util.Locale) {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { weekday ->
            Text(
                weekday.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One day of the grid: what it holds as a number, with the day open filled. Today stands out most, with
 * an accent tint, a strong accent outline, its number in an accent pill and a "Today" label; tomorrow
 * gets a softer outline and a "Tomorrow" label, so the eye finds where it is at once. A task
 * dragged from the day's list below lands on it, which plans it for that day (docs/calendar.md).
 * A long-press ([onLongClick]) starts picking several days, and a drag that follows it reports where
 * the finger is on the screen ([onDragTo]), for the days it crosses; a picked day gets a thick outline
 * in the text colour, so it still reads as picked on today or tomorrow.
 * [onPlaced] tells where the cell is on the screen. A screen reader hears the whole date and what is
 * on it. The cell keeps the grid's shape and grows taller when large text needs the room.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayCell(
    day: CalendarDay,
    today: Boolean,
    inPeriod: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDropTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    eventSpace: Dp = 0.dp,
    picked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onDragTo: ((Offset) -> Unit)? = null,
    onPlaced: ((Rect) -> Unit)? = null,
    tomorrow: Boolean = false,
) {
    var hovered by remember { mutableStateOf(false) }
    // Set by the long-press, so the moves after it in the same gesture pick days rather than scroll.
    var holding by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dragTo by rememberUpdatedState(onDragTo)
    val target = remember(day.day) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovered = false
                val id = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString().orEmpty()
                if (id.isEmpty()) return false
                onDropTask(id)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                hovered = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovered = false
            }
        }
    }
    val background = when {
        hovered -> AppTheme.colors.accent.copy(alpha = 0.4f)
        selected -> AppTheme.colors.accent
        today -> AppTheme.colors.accent.copy(alpha = TODAY_TINT)
        day.empty -> AppTheme.colors.surface.copy(alpha = if (inPeriod) 1f else 0.4f)
        else -> AppTheme.colors.surface
    }
    val description = dayDescription(day, today, tomorrow, selected, picked)
    // Picking wins the outline, in a colour of its own; then today's strong one, then tomorrow's soft one.
    val outline = when {
        picked -> BorderStroke(3.dp, AppTheme.colors.text)
        today -> BorderStroke(2.dp, AppTheme.colors.accent)
        tomorrow -> BorderStroke(1.5.dp, AppTheme.colors.accent.copy(alpha = TOMORROW_OUTLINE))
        else -> BorderStroke(2.dp, Color.Transparent)
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(2.dp)
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val least = (constraints.maxWidth / CELL_RATIO).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = least))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(shape)
            .background(background)
            // Always there, so picking a day keeps the gesture going on it rather than starting the chain anew.
            .border(outline, shape)
            .onGloballyPositioned { coordinates ->
                placed = coordinates
                onPlaced?.invoke(coordinates.boundsInRoot())
            }
            .pointerInput(day.day) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val coordinates = placed
                        // The click handler has already consumed the move after its long-press, so the move is read regardless.
                        if (holding && coordinates != null && change.positionChangeIgnoreConsumed() != Offset.Zero) {
                            dragTo?.invoke(coordinates.localToRoot(change.position))
                            change.consume()
                        }
                    }
                    holding = false
                }
            }
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = stringResource(R.string.calendar_pick_several),
                        onLongClick = {
                            holding = true
                            onLongClick()
                        },
                    )
                },
            )
            .semantics { contentDescription = description }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
            .padding(2.dp),
    ) {
        Text(
            day.day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = when {
                // Today's number sits in a pill: accent on a plain cell, turned round on the open one.
                today && selected -> AppTheme.colors.accent
                today || selected -> AppTheme.colors.onAccent
                inPeriod -> AppTheme.colors.text
                else -> AppTheme.colors.textMuted
            },
            // The description already says the date.
            modifier = Modifier
                .clearAndSetSemantics {}
                .then(
                    if (today) {
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) AppTheme.colors.onAccent else AppTheme.colors.accent)
                            .padding(horizontal = 8.dp)
                    } else {
                        Modifier
                    },
                ),
        )
        if (day.count > 0) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .height(4.dp)
                    .size(width = (6 + 4 * minOf(day.count, 4)).dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (selected) AppTheme.colors.onAccent else AppTheme.colors.accent),
            )
        }
        if (today || tomorrow) {
            Text(
                stringResource(if (today) R.string.calendar_cell_today_label else R.string.calendar_cell_tomorrow_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) AppTheme.colors.onAccent else AppTheme.colors.accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                // The description says it.
                modifier = Modifier.clearAndSetSemantics {}.padding(top = 1.dp),
            )
        }
        // The row's event bars are drawn over this room.
        if (eventSpace > 0.dp) Spacer(Modifier.height(eventSpace))
    }
}

/** "Saturday 3 October, today, 2 planned, 1 due" for a cell, said where a sighted owner sees the bar. */
@Composable
private fun dayDescription(day: CalendarDay, today: Boolean, tomorrow: Boolean, selected: Boolean, picked: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val parts = mutableListOf(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.day))
    if (today) parts += stringResource(R.string.calendar_cell_today)
    if (tomorrow) parts += stringResource(R.string.calendar_cell_tomorrow)
    if (selected) parts += stringResource(R.string.calendar_cell_open)
    if (picked) parts += stringResource(R.string.calendar_cell_picked)
    if (day.empty) parts += stringResource(R.string.calendar_cell_empty)
    if (day.planned.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_planned, day.planned.size, day.planned.size)
    if (day.deadlines.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_due, day.deadlines.size, day.deadlines.size)
    if (day.repeats.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_repeats, day.repeats.size, day.repeats.size)
    if (day.reminders > 0) parts += pluralStringResource(R.plurals.calendar_cell_reminders, day.reminders, day.reminders)
    if (day.events.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_events, day.events.size, day.events.size)
    return parts.joinToString(", ")
}

/** One of the open day's events: its area's colour, its title and its days; a tap opens the event sheet. */
@Composable
private fun EventRow(event: EventItem, area: AreaItem?, onOpen: () -> Unit) {
    val palette = area?.let { chosen -> AppTheme.areaColors.firstOrNull { it.id == chosen.colorId } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onOpen)
            .padding(end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            Modifier
                .padding(start = 8.dp, end = 10.dp)
                .size(width = 4.dp, height = 32.dp)
                .clip(CircleShape)
                .background(palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.accent),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text, maxLines = 2)
            Text(
                eventDays(event.startsOn, event.endsOn),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
            )
        }
    }
}

/**
 * One line of what a day holds, with its [project]'s chip when it is a project item; a planned task
 * can be dragged onto another day of the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayRow(
    task: TaskItem,
    label: Int,
    onOpenTask: (String) -> Unit,
    project: ProjectItem?,
    onOpenProject: ((String) -> Unit)?,
    draggable: Boolean = false,
    onDone: ((TaskItem, Boolean) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable { onOpenTask(task.id) }
            .then(
                if (!draggable) {
                    Modifier
                } else {
                    Modifier.dragAndDropSource(
                        transferData = {
                            DragAndDropTransferData(ClipData.newPlainText(task.title, task.id))
                        },
                    )
                },
            )
            .padding(start = if (onDone == null) 12.dp else 0.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        // A planned task or a deadline is ticked off here on the open day; a repeat has no row to tick yet.
        if (onDone != null && task.state != TaskState.DROPPED) {
            GoalMakerCheckbox(checked = task.state == TaskState.DONE, onCheckedChange = { done -> onDone(task, done) })
        }
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.state == TaskState.DONE) AppTheme.colors.textMuted else AppTheme.colors.text,
                maxLines = 2,
            )
            Text(
                listOfNotNull(stringResource(label), task.plannedTime?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
            )
            project?.let { item -> ProjectChip(item, task.itemType, onOpenProject?.let { open -> { open(item.id) } }, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** "September 2026" for a month, "14 to 20 Sep" for a week. */
@Composable
private fun period(state: CalendarUiState, locale: java.util.Locale): String {
    if (!state.loaded) return ""
    if (state.kind == CalendarRules.MONTH) {
        return DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.anchor)
    }
    val first = CalendarRules.start(CalendarRules.WEEK, state.anchor)
    val last = CalendarRules.end(CalendarRules.WEEK, state.anchor)
    val day = DateTimeFormatter.ofPattern("d", locale)
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    return stringResource(
        R.string.goals_range,
        if (first.month == last.month) day.format(first) else dayMonth.format(first),
        dayMonth.format(last),
    )
}

// A day cell's width to its height, unless large text needs it taller.
private const val CELL_RATIO = 0.9f

// How strongly today's cell is tinted with the accent, and how strong tomorrow's outline is.
private const val TODAY_TINT = 0.16f
private const val TOMORROW_OUTLINE = 0.55f
