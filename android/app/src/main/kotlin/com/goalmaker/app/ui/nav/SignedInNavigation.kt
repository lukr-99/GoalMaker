package com.goalmaker.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import com.goalmaker.app.ui.lists.ListsScreen
import com.goalmaker.app.ui.lists.ListsViewModel
import com.goalmaker.app.ui.plan.PlanScreen
import com.goalmaker.app.ui.plan.PlanViewModel
import com.goalmaker.app.ui.task.TaskKey
import com.goalmaker.app.ui.task.TaskScreen
import com.goalmaker.app.ui.task.TaskViewModel

/** The signed-in part of the app: a Navigation 3 back stack starting at Today. */
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
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<TodayKey> {
                val listsViewModel = viewModel {
                    ListsViewModel(
                        tasks = graph.tasks,
                        areas = graph.areas,
                        tags = graph.tags,
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
                )
            }
            entry<PlanKey> {
                val planViewModel = viewModel {
                    PlanViewModel(
                        tasks = graph.tasks,
                        areas = graph.areas,
                        tags = graph.tags,
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
            entry<TaskKey> { key ->
                val taskViewModel = viewModel(key = key.id) { TaskViewModel(key.id, graph.tasks, graph.areas, graph.tags, graph.steps, graph.io) }
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
