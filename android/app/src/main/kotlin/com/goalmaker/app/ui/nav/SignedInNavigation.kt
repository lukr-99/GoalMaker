package com.goalmaker.app.ui.nav

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.time.LocalDate
import java.time.LocalDateTime
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.ui.settings.SettingsScreen
import com.goalmaker.app.ui.settings.SettingsViewModel
import com.goalmaker.app.ui.activity.ActivityKey
import com.goalmaker.app.ui.activity.ActivityScreen
import com.goalmaker.app.ui.activity.ActivityViewModel
import com.goalmaker.app.ui.archive.ArchiveKey
import com.goalmaker.app.ui.archive.ArchiveScreen
import com.goalmaker.app.ui.archive.ArchiveViewModel
import com.goalmaker.app.ui.areas.AreasKey
import com.goalmaker.app.ui.areas.AreasScreen
import com.goalmaker.app.ui.areas.AreasViewModel
import com.goalmaker.app.ui.connector.ConnectorKey
import com.goalmaker.app.ui.connector.ConnectorScreen
import com.goalmaker.app.ui.connector.ConnectorViewModel
import com.goalmaker.app.ui.goals.GoalsKey
import com.goalmaker.app.ui.goals.GoalsScreen
import com.goalmaker.app.ui.goals.GoalsViewModel
import com.goalmaker.app.ui.habits.HabitsKey
import com.goalmaker.app.ui.habits.HabitsScreen
import com.goalmaker.app.ui.habits.HabitsViewModel
import com.goalmaker.app.ui.lists.ListsScreen
import com.goalmaker.app.ui.lists.ListsViewModel
import com.goalmaker.app.ui.plan.PlanScreen
import com.goalmaker.app.ui.review.ReviewKey
import com.goalmaker.app.ui.review.ReviewScreen
import com.goalmaker.app.ui.review.ReviewViewModel
import com.goalmaker.app.ui.review.ReviewsKey
import com.goalmaker.app.ui.calendar.CalendarKey
import com.goalmaker.app.ui.calendar.CalendarScreen
import com.goalmaker.app.ui.calendar.CalendarViewModel
import com.goalmaker.app.ui.projects.ProjectsKey
import com.goalmaker.app.ui.projects.ProjectsScreen
import com.goalmaker.app.ui.projects.ProjectsViewModel
import com.goalmaker.app.ui.stats.StatsKey
import com.goalmaker.app.ui.stats.StatsScreen
import com.goalmaker.app.ui.stats.StatsViewModel
import com.goalmaker.app.ui.review.ReviewsScreen
import com.goalmaker.app.ui.review.ReviewsViewModel
import com.goalmaker.app.ui.plan.PlanViewModel
import com.goalmaker.app.ui.task.TaskKey
import com.goalmaker.app.ui.task.TaskScreen
import com.goalmaker.app.ui.task.TaskViewModel
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The signed-in part of the app: a Navigation 3 back stack starting at Today. Screens move as
 * [NavTransitions] says, and a task row and the task's details share their bounds.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SignedInNavigation(graph: AppGraph) {
    val backStack = rememberNavBackStack(TodayKey)
    // The evening Plan tomorrow reminder opens the ritual on top of whatever was open.
    val planRequested by graph.planRequested.collectAsState()
    LaunchedEffect(planRequested) {
        if (planRequested) {
            if (backStack.lastOrNull() != PlanKey) backStack.add(PlanKey)
            graph.planOpened()
        }
    }
    // A review reminder opens the review it asked for, on top of whatever was open.
    val reviewRequested by graph.reviewRequested.collectAsState()
    LaunchedEffect(reviewRequested) {
        reviewRequested?.let { (kind, start) ->
            val key = ReviewKey(kind, start.toString())
            if (backStack.lastOrNull() != key) backStack.add(key)
            graph.reviewOpened()
        }
    }
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val transitions = remember(motion, reduced) { NavTransitions(motion, reduced) }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sharedTransitionScope = this,
                transitionSpec = { transitions.forward() },
                popTransitionSpec = { transitions.back() },
                predictivePopTransitionSpec = { transitions.backSwipe() },
                entryProvider = entryProvider {
                    entry<TodayKey> {
                        val listsViewModel = viewModel {
                            ListsViewModel(
                                tasks = graph.tasks,
                                areas = graph.areas,
                                tags = graph.tags,
                                projects = graph.projects,
                                goals = graph.goals,
                                habits = graph.habits,
                                settings = graph.settings,
                                reminders = graph.reminders,
                                sync = graph.sync,
                                io = graph.io,
                                clock = LocalDateTime::now,
                            )
                        }
                        ListsScreen(
                            viewModel = listsViewModel,
                            onOpenPlan = { backStack.add(PlanKey) },
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onOpenTask = { id -> backStack.add(TaskKey(id)) },
                            onOpenArchive = { backStack.add(ArchiveKey) },
                            onOpenGoals = { backStack.add(GoalsKey) },
                            onOpenHabits = { backStack.add(HabitsKey) },
                            onOpenReviews = { backStack.add(ReviewsKey) },
                            onOpenStats = { backStack.add(StatsKey) },
                            onOpenProjects = { backStack.add(ProjectsKey) },
                            onOpenCalendar = { backStack.add(CalendarKey) },
                        )
                    }
                    entry<GoalsKey> {
                        val goalsViewModel = viewModel { GoalsViewModel(graph.goals, graph.tasks, graph.habits, graph.settings.dayStartHour, graph.io, LocalDateTime::now) }
                        GoalsScreen(viewModel = goalsViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<ReviewsKey> {
                        val reviewsViewModel = viewModel { ReviewsViewModel(graph.reviews, graph.settings, graph.io, LocalDateTime::now) }
                        ReviewsScreen(
                            viewModel = reviewsViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpen = { kind, start -> backStack.add(ReviewKey(kind, start.toString())) },
                        )
                    }
                    entry<CalendarKey> {
                        val calendarViewModel = viewModel {
                            CalendarViewModel(graph.tasks, graph.reminderList, graph.settings, graph.io, LocalDateTime::now)
                        }
                        CalendarScreen(
                            viewModel = calendarViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenTask = { id -> backStack.add(TaskKey(id)) },
                        )
                    }
                    entry<ProjectsKey> {
                        val projectsViewModel = viewModel { ProjectsViewModel(graph.projects, graph.tasks, graph.io) }
                        ProjectsScreen(
                            viewModel = projectsViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenTask = { id -> backStack.add(TaskKey(id)) },
                        )
                    }
                    entry<StatsKey> {
                        val statsViewModel = viewModel {
                            StatsViewModel(
                                tasks = graph.tasks,
                                goals = graph.goals,
                                habits = graph.habits,
                                reviews = graph.reviews,
                                settings = graph.settings,
                                io = graph.io,
                                clock = LocalDateTime::now,
                            )
                        }
                        StatsScreen(viewModel = statsViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<ReviewKey> { key ->
                        val reviewViewModel = viewModel(key = "${key.kind}-${key.periodStart}") {
                            ReviewViewModel(
                                kind = key.kind,
                                periodStart = LocalDate.parse(key.periodStart),
                                reviews = graph.reviews,
                                tasks = graph.tasks,
                                areas = graph.areas,
                                goals = graph.goals,
                                habits = graph.habits,
                                prompts = graph.prompts,
                                rituals = graph.rituals,
                                io = graph.io,
                                today = graph::today,
                            )
                        }
                        ReviewScreen(viewModel = reviewViewModel, onClose = { backStack.removeLastOrNull() })
                    }
                    entry<HabitsKey> {
                        val habitsViewModel = viewModel { HabitsViewModel(graph.habits, graph.goals, graph.settings.dayStartHour, graph.io, LocalDateTime::now) }
                        HabitsScreen(viewModel = habitsViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<PlanKey> {
                        val planViewModel = viewModel {
                            PlanViewModel(
                                tasks = graph.tasks,
                                areas = graph.areas,
                                tags = graph.tags,
                                projects = graph.projects,
                                settings = graph.settings,
                                io = graph.io,
                                clock = LocalDateTime::now,
                                onFinished = graph::planTomorrowFinished,
                            )
                        }
                        PlanScreen(viewModel = planViewModel, onClose = { backStack.removeLastOrNull() })
                    }
                    entry<SettingsKey> {
                        val settingsViewModel = viewModel {
                            SettingsViewModel(
                                auth = graph.auth,
                                sync = graph.sync,
                                settings = graph.settings,
                                reminders = graph.reminders,
                                io = graph.io,
                                design = graph.design,
                                updates = graph.updates,
                                appInfo = graph.appInfo,
                                backup = graph.backup,
                                requestSync = graph.sync::request,
                                restartApp = graph.restartApp,
                            )
                        }
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAreas = { backStack.add(AreasKey) },
                            onOpenConnector = { backStack.add(ConnectorKey) },
                            onOpenActivity = { backStack.add(ActivityKey) },
                        )
                    }
                    entry<ConnectorKey> {
                        val connectorViewModel = viewModel { ConnectorViewModel(graph.connectorLinks, graph.appInfo.backend.url, graph.io) }
                        ConnectorScreen(viewModel = connectorViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<ActivityKey> {
                        val activityViewModel = viewModel { ActivityViewModel(graph.activity, graph.sync::request, graph.io) }
                        ActivityScreen(viewModel = activityViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<TaskKey>(metadata = transitions.task()) { key ->
                        val taskViewModel = viewModel(key = key.id) {
                            TaskViewModel(key.id, graph.tasks, graph.areas, graph.tags, graph.steps, graph.goals, graph.io, graph::today)
                        }
                        TaskScreen(viewModel = taskViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<ArchiveKey> {
                        val archiveViewModel = viewModel { ArchiveViewModel(graph.tasks, graph.io) }
                        ArchiveScreen(
                            viewModel = archiveViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenTask = { id -> backStack.add(TaskKey(id)) },
                        )
                    }
                    entry<AreasKey> {
                        val areasViewModel = viewModel { AreasViewModel(graph.areas, graph.tags, graph.io) }
                        AreasScreen(viewModel = areasViewModel, onBack = { backStack.removeLastOrNull() })
                    }
                },
            )
        }
    }
}
