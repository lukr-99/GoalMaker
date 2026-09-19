package com.goalmaker.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.ReviewDigest
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.goals.GoalDialog
import com.goalmaker.app.ui.goals.GoalSummaryRow
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * The guided review (docs/reviews.md, spec stories 59 to 64): look back over the period, handle what
 * is still open, answer the prompts, rate mood and energy, and set the next period's goals.
 */
@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalItem?>(null) }

    // Whatever was typed is kept when the screen goes away.
    DisposableEffect(viewModel) {
        onDispose { viewModel.saveAnswers() }
    }
    BackHandler(enabled = true) {
        if (!viewModel.back()) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle(stringResource(if (state.kind == ReviewRules.MONTHLY) R.string.reviews_monthly else R.string.reviews_weekly)) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = AppTheme.density.pagePadding.dp, vertical = 12.dp),
            ) {
                Text(
                    periodText(state.digest),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (state.step == ReviewStep.DONE) {
                    Button(onClick = onClose) { Text(stringResource(R.string.reviews_close)) }
                } else {
                    Button(onClick = viewModel::next) {
                        Text(stringResource(if (state.step == ReviewStep.GOALS) R.string.reviews_finish else R.string.reviews_next))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (state.step.ordinal + 1f) / ReviewStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.loaded) return@Column
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.step) {
                    ReviewStep.LOOK_BACK -> lookBack(state)
                    ReviewStep.TASKS -> openTasks(state, viewModel)
                    ReviewStep.REFLECT -> reflect(state, viewModel)
                    ReviewStep.RATE -> rate(state, viewModel)
                    ReviewStep.GOALS -> nextGoals(state, viewModel) { editing = it }
                    ReviewStep.DONE -> done(state)
                }
            }
        }
    }

    editing?.let { goal ->
        GoalDialog(
            goal = goal,
            today = state.digest.periodEnd.plusDays(1),
            goals = state.goals,
            onSave = { draft -> viewModel.addGoal(draft) },
            onDelete = null,
            onDismiss = { editing = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lookBack(state: ReviewUiState) {
    val digest = state.digest
    item("done") {
        Column {
            SectionHeader(stringResource(R.string.reviews_look_back))
            Text(
                pluralStringResource(R.plurals.reviews_done, digest.done, digest.done),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(changeText(digest), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        }
    }
    if (digest.days.isNotEmpty()) item("days") { DayBars(digest) }
    digest.bestDay?.let { best ->
        item("best") {
            Highlight(
                stringResource(
                    R.string.reviews_best_day,
                    DateTimeFormatter.ofPattern("EEEE", LocalConfiguration.current.locales[0]).format(best.day),
                    best.done,
                ),
            )
        }
    }
    digest.strongestArea?.let { area ->
        item("area") { Highlight(stringResource(R.string.reviews_strongest_area, area.name, area.done)) }
    }
    digest.habits.maxByOrNull { it.streak }?.takeIf { it.streak > 0 }?.let { habit ->
        item("streak") {
            Highlight(stringResource(R.string.reviews_longest_streak, habit.name, habit.streak), fire = true)
        }
    }
    if (digest.goals.isNotEmpty()) {
        item("h-goals") { SectionHeader(stringResource(R.string.reviews_goals)) }
        items(digest.goals, key = { "goal-" + it.id }) { goal -> GoalLine(goal) }
    }
    if (digest.habits.isNotEmpty()) {
        item("h-habits") { SectionHeader(stringResource(R.string.reviews_habits)) }
        items(digest.habits, key = { "habit-" + it.id }) { habit -> HabitLine(habit) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.openTasks(state: ReviewUiState, viewModel: ReviewViewModel) {
    item("h-open") {
        Column {
            SectionHeader(stringResource(R.string.reviews_open_tasks))
            Text(
                stringResource(if (state.openTasks == 0) R.string.reviews_open_none else R.string.reviews_open_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
            )
        }
    }
    items(state.digest.openTasks, key = { "open-" + it.id }) { task ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface, AppTheme.shapes.row)
                .padding(12.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.decide(task, PlanDecision.TOMORROW) }) {
                    Text(stringResource(R.string.reviews_task_forward))
                }
                TextButton(onClick = { viewModel.decide(task, PlanDecision.DONE) }) {
                    Text(stringResource(R.string.reviews_task_done))
                }
                TextButton(onClick = { viewModel.decide(task, PlanDecision.DROPPED) }) {
                    Text(stringResource(R.string.reviews_task_drop), color = AppTheme.colors.danger)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reflect(state: ReviewUiState, viewModel: ReviewViewModel) {
    item("h-reflect") { SectionHeader(stringResource(R.string.reviews_reflect)) }
    items(state.questions, key = { "q-" + it.promptId }) { question ->
        Column(Modifier.padding(bottom = 8.dp)) {
            Text(question.text, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.answers[question.promptId].orEmpty(),
                onValueChange = { text -> viewModel.answer(question.promptId, text) },
                placeholder = { Text(stringResource(R.string.reviews_answer)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rate(state: ReviewUiState, viewModel: ReviewViewModel) {
    item("h-rate") { SectionHeader(stringResource(R.string.reviews_rate)) }
    item("mood") { Rating(stringResource(R.string.reviews_mood), state.mood, viewModel::setMood) }
    item("energy") { Rating(stringResource(R.string.reviews_energy), state.energy, viewModel::setEnergy) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.nextGoals(
    state: ReviewUiState,
    viewModel: ReviewViewModel,
    onAdd: (GoalItem) -> Unit,
) {
    item("h-next") {
        Column {
            SectionHeader(stringResource(R.string.reviews_next_goals))
            Text(
                stringResource(R.string.reviews_next_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
            )
        }
    }
    items(state.nextGoals, key = { "next-" + it.goal.id }) { row -> GoalSummaryRow(row, onClick = {}) }
    item("add") {
        Row {
            TextButton(onClick = {
                val draft = viewModel.nextGoalDraft()
                onAdd(GoalItem("", "", draft.horizon, draft.periodStart))
            }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.goals_add), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.canCopyGoals) {
                TextButton(onClick = viewModel::copyGoals) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.reviews_copy_goals), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.done(state: ReviewUiState) {
    item("done-text") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reviews_done_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                pluralStringResource(R.plurals.reviews_done_summary, state.digest.done, state.digest.done, state.answered),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textMuted,
            )
        }
    }
}

/** A small bar per day of the period, so a glance shows where the work happened. */
@Composable
private fun DayBars(digest: ReviewDigest) {
    val most = max(1, digest.days.maxOfOrNull { it.done } ?: 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        digest.days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + 48 * day.done / most).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (day.done > 0) AppTheme.colors.accent else AppTheme.colors.outline.copy(alpha = 0.3f)),
                )
                if (digest.days.size <= 31) {
                    Text(
                        day.day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Highlight(text: String, fire: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fire) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun GoalLine(goal: ReviewDigest.Goal) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ProgressRing(goal.fraction.toFloat(), size = 28.dp, stroke = 3.dp) {
            goal.emoji?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            goal.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (goal.hit) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HabitLine(habit: ReviewDigest.Habit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ProgressRing(
            fraction = if (habit.periods == 0) 0f else habit.met.toFloat() / habit.periods,
            size = 28.dp,
            stroke = 3.dp,
        ) {
            habit.emoji?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            habit.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            stringResource(R.string.reviews_habit_met, habit.met, habit.periods),
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textMuted,
        )
    }
}

/** Mood or energy from 1 to 5; tapping the chosen number again clears it. */
@Composable
private fun Rating(label: String, value: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            (1..5).forEach { number ->
                val chosen = value == number
                val description = "$label $number"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (chosen) AppTheme.colors.accent else AppTheme.colors.surface)
                        .clickable { onPick(number) }
                        .semantics { contentDescription = description },
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (chosen) AppTheme.colors.onAccent else AppTheme.colors.text,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun changeText(digest: ReviewDigest): String {
    val change = digest.change
    return when {
        digest.doneBefore == 0 -> stringResource(R.string.reviews_no_last_period)
        change > 0 -> pluralStringResource(R.plurals.reviews_more_than_before, change, change)
        change < 0 -> pluralStringResource(R.plurals.reviews_fewer_than_before, -change, -change)
        else -> stringResource(R.string.reviews_same_as_before)
    }
}

@Composable
private fun periodText(digest: ReviewDigest): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern("d MMM", locale)
    return stringResource(R.string.goals_range, format.format(digest.periodStart), format.format(digest.periodEnd))
}
