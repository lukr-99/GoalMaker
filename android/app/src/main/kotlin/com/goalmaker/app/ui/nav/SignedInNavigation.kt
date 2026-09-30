package com.goalmaker.app.ui.nav

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.time.LocalDate
import java.time.LocalDateTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.domain.navigation.PlaceRules
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
import com.goalmaker.app.ui.lists.ListTab
import com.goalmaker.app.ui.lists.ListsScreen
import com.goalmaker.app.ui.lists.ListsViewModel
import com.goalmaker.app.ui.places.PlacesScreen
import com.goalmaker.app.ui.places.PlacesViewModel
import com.goalmaker.app.ui.plan.PlanScreen
import com.goalmaker.app.ui.review.ReviewKey
import com.goalmaker.app.ui.review.ReviewScreen
import com.goalmaker.app.ui.review.ReviewViewModel
import com.goalmaker.app.ui.review.ReviewsKey
import com.goalmaker.app.ui.calendar.CalendarScreen
import com.goalmaker.app.ui.calendar.CalendarViewModel
import com.goalmaker.app.ui.projects.ProjectsScreen
import com.goalmaker.app.ui.projects.ProjectsViewModel
import com.goalmaker.app.ui.stats.StatsKey
import com.goalmaker.app.ui.stats.StatsScreen
import com.goalmaker.app.ui.stats.StatsViewModel
import com.goalmaker.app.ui.review.ReviewsScreen
import com.goalmaker.app.ui.review.ReviewsViewModel
import com.goalmaker.app.ui.plan.PlanViewModel
import com.goalmaker.app.ui.tally.TallyScreen
import com.goalmaker.app.ui.tally.TallyViewModel
import com.goalmaker.app.ui.task.TaskKey
import com.goalmaker.app.ui.task.TaskScreen
import com.goalmaker.app.ui.task.TaskViewModel
import com.goalmaker.app.ui.wants.WantsScreen
import com.goalmaker.app.ui.wants.WantsViewModel
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The signed-in part of the app: a Navigation 3 back stack starting at Today. Screens move as
 * [NavTransitions] says, and a task row and the task's details share their bounds.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SignedInNavigation(graph: AppGraph) {
    val backStack = rememberNavBackStack(TodayKey)
    // Every place is a tab of the start screen (ADR 0014): the pinned ones in the bottom bar, and the
    // rest reached from Places. Which one is on show, and the list the lists come back to.
    val pins by graph.settings.pins.collectAsStateWithLifecycle()
    val home = if (PlaceRules.TODAY in pins) PlaceRules.TODAY else pins.first()
    var place by rememberSaveable { mutableStateOf(home) }
    var listTab by rememberSaveable { mutableStateOf(ListTab.TODAY) }
    fun select(destination: String) {
        while (backStack.size > 1) backStack.removeLastOrNull()
        place = destination
        PlaceLook.tab(destination)?.let { listTab = it }
    }
    // A place opened from Places goes back there; any other place goes back home (Today, unless it
    // was unpinned), and only home's Back leaves the app. Registered before the NavDisplay, so a
    // screen on top of the tabs always gets Back first.
    val fromHub = place != PlaceLook.HUB && place !in pins
    BackHandler(enabled = place != home && backStack.size == 1) { select(if (fromHub) PlaceLook.HUB else home) }
    val backToHub: (() -> Unit)? = if (fromHub) ({ select(PlaceLook.HUB) }) else null
    // A `/want` line opens the Wants place with its add sheet, the title filled in.
    var wantTitle by rememberSaveable { mutableStateOf<String?>(null) }
    // What went wrong while nobody was watching: the mark on the gear, and the card in Settings.
    val problems by graph.problems.problems.collectAsStateWithLifecycle()
    // The evening Plan tomorrow reminder opens the ritual on top of whatever was open.
    val planRequested by graph.planRequested.collectAsState()
    LaunchedEffect(planRequested) {
        if (planRequested) {
            if (backStack.lastOrNull() != PlanKey) backStack.add(PlanKey)
            graph.planOpened()
        }
    }
    // The wants notification opens the Wants place, pinned or not.
    val wantsRequested by graph.wantsRequested.collectAsState()
    LaunchedEffect(wantsRequested) {
        if (wantsRequested) {
            select(PlaceRules.WANTS)
            graph.wantsOpened()
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
                        val projectsViewModel = viewModel { ProjectsViewModel(graph.projects, graph.tasks, graph.io) }
                        val placesViewModel = viewModel {
                            PlacesViewModel(
                                graph.tasks,
                                graph.habits,
                                graph.goals,
                                graph.reviews,
                                graph.wants,
                                graph.tally,
                                graph.tallyDefaults.categories,
                                graph.settings,
                                graph.io,
                                LocalDateTime::now,
                            )
                        }
                        val placesState by placesViewModel.uiState.collectAsStateWithLifecycle()
                        val calendarViewModel = viewModel {
                            CalendarViewModel(graph.tasks, graph.reminderList, graph.settings, graph.io, LocalDateTime::now)
                        }
                        val syncStatus by graph.sync.status.collectAsStateWithLifecycle()
                        // Every place wears the same top bar actions.
                        val actions: @Composable () -> Unit = {
                            MainActions(
                                sync = syncStatus,
                                onSyncNow = listsViewModel::refresh,
                                hasProblems = problems.any { it.unread },
                                onOpenPlan = { backStack.add(PlanKey) },
                                onOpenSettings = { backStack.add(SettingsKey) },
                            )
                        }
                        // Leaving Places ends pin editing, so it never waits half done.
                        LaunchedEffect(place) { if (place != PlaceLook.HUB) placesViewModel.stopEditing() }
                        MainScreen(
                            current = place,
                            pins = pins,
                            placesCount = placesState.count,
                            onSelect = ::select,
                        ) { screen ->
                            when (screen) {
                                PlaceLook.LISTS -> ListsScreen(
                                    viewModel = listsViewModel,
                                    onOpenPlan = { backStack.add(PlanKey) },
                                    onOpenTask = { id -> backStack.add(TaskKey(id)) },
                                    onOpenGoals = { backStack.add(GoalsKey) },
                                    onOpenHabits = { backStack.add(HabitsKey) },
                                    tab = listTab,
                                    actions = actions,
                                    onBack = backToHub,
                                    onOpenWant = { title ->
                                        wantTitle = title
                                        select(PlaceRules.WANTS)
                                    },
                                )
                                PlaceRules.PROJECTS -> ProjectsScreen(
                                    viewModel = projectsViewModel,
                                    onOpenTask = { id -> backStack.add(TaskKey(id)) },
                                    actions = actions,
                                    onBack = backToHub,
                                )
                                PlaceRules.CALENDAR -> CalendarScreen(
                                    viewModel = calendarViewModel,
                                    onOpenTask = { id -> backStack.add(TaskKey(id)) },
                                    actions = actions,
                                    onBack = backToHub,
                                )
                                PlaceRules.HABITS -> {
                                    val habitsViewModel = viewModel(key = "habits-tab") {
                                        HabitsViewModel(graph.habits, graph.goals, graph.settings.dayStartHour, graph.io, LocalDateTime::now)
                                    }
                                    HabitsScreen(viewModel = habitsViewModel, onBack = backToHub, actions = actions)
                                }
                                PlaceRules.GOALS -> {
                                    val goalsViewModel = viewModel(key = "goals-tab") {
                                        GoalsViewModel(graph.goals, graph.tasks, graph.habits, graph.settings.dayStartHour, graph.io, LocalDateTime::now)
                                    }
                                    GoalsScreen(viewModel = goalsViewModel, onBack = backToHub, actions = actions)
                                }
                                PlaceRules.REVIEWS -> {
                                    val reviewsViewModel = viewModel(key = "reviews-tab") {
                                        ReviewsViewModel(graph.reviews, graph.settings, graph.io, LocalDateTime::now)
                                    }
                                    ReviewsScreen(
                                        viewModel = reviewsViewModel,
                                        onBack = backToHub,
                                        onOpen = { kind, start -> backStack.add(ReviewKey(kind, start.toString())) },
                                        actions = actions,
                                    )
                                }
                                PlaceRules.STATS -> {
                                    val statsViewModel = viewModel(key = "stats-tab") {
                                        StatsViewModel(
                                            tasks = graph.tasks,
                                            goals = graph.goals,
                                            habits = graph.habits,
                                            reviews = graph.reviews,
                                            wants = graph.wants,
                                            tally = graph.tally,
                                            tallyCategories = graph.tallyDefaults.categories,
                                            settings = graph.settings,
                                            io = graph.io,
                                            clock = LocalDateTime::now,
                                        )
                                    }
                                    StatsScreen(viewModel = statsViewModel, onBack = backToHub, actions = actions)
                                }
                                PlaceRules.ARCHIVE -> {
                                    val archiveViewModel = viewModel(key = "archive-tab") { ArchiveViewModel(graph.tasks, graph.io) }
                                    ArchiveScreen(
                                        viewModel = archiveViewModel,
                                        onBack = backToHub,
                                        onOpenTask = { id -> backStack.add(TaskKey(id)) },
                                        actions = actions,
                                    )
                                }
                                PlaceRules.WANTS -> {
                                    val wantsViewModel = viewModel(key = "wants-tab") {
                                        WantsViewModel(graph.wants, graph.settings.dayStartHour, graph.io, LocalDateTime::now)
                                    }
                                    WantsScreen(
                                        viewModel = wantsViewModel,
                                        onBack = backToHub,
                                        actions = actions,
                                        addTitle = wantTitle,
                                        onAddShown = { wantTitle = null },
                                    )
                                }
                                PlaceRules.TALLY -> {
                                    val tallyViewModel = viewModel(key = "tally-tab") {
                                        TallyViewModel(
                                            tracker = graph.tallyTracker,
                                            tally = graph.tally,
                                            projects = graph.projects,
                                            defaults = graph.tallyDefaults.categories,
                                            dayStartHour = graph.settings.dayStartHour,
                                            io = graph.io,
                                            clock = LocalDateTime::now,
                                        )
                                    }
                                    TallyScreen(viewModel = tallyViewModel, onBack = backToHub, actions = actions)
                                }
                                else -> PlacesScreen(viewModel = placesViewModel, onOpen = ::select, actions = actions)
                            }
                        }
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
                    entry<StatsKey> {
                        val statsViewModel = viewModel {
                            StatsViewModel(
                                tasks = graph.tasks,
                                goals = graph.goals,
                                habits = graph.habits,
                                reviews = graph.reviews,
                                wants = graph.wants,
                                tally = graph.tally,
                                tallyCategories = graph.tallyDefaults.categories,
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
                                tally = graph.tally,
                                tallyCategories = graph.tallyDefaults.categories,
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
                                releasesPage = graph.releaseChannel?.releasesPage,
                                appInfo = graph.appInfo,
                                backup = graph.backup,
                                requestSync = graph.sync::request,
                                restartApp = graph.restartApp,
                                unlockAvailability = graph.deviceUnlock::availability,
                                appLockTurned = graph.appLock::turned,
                            )
                        }
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAreas = { backStack.add(AreasKey) },
                            onOpenConnector = { backStack.add(ConnectorKey) },
                            onOpenActivity = { backStack.add(ActivityKey) },
                            problems = problems,
                            onProblemsRead = graph.problems::read,
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
                            TaskViewModel(key.id, graph.tasks, graph.areas, graph.tags, graph.steps, graph.goals, graph.projects, graph.io, graph::today)
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
