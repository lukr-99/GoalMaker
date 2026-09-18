package com.goalmaker.app.ui.nav

import androidx.compose.runtime.Composable
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
import com.goalmaker.app.ui.areas.AreasKey
import com.goalmaker.app.ui.areas.AreasScreen
import com.goalmaker.app.ui.areas.AreasViewModel
import com.goalmaker.app.ui.lists.ListsScreen
import com.goalmaker.app.ui.lists.ListsViewModel
import com.goalmaker.app.ui.plan.PlanScreen
import com.goalmaker.app.ui.plan.PlanViewModel

/** The signed-in part of the app: a Navigation 3 back stack starting at Today. */
@Composable
fun SignedInNavigation(graph: AppGraph) {
    val backStack = rememberNavBackStack(TodayKey)
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
                )
            }
            entry<AreasKey> {
                val areasViewModel = viewModel { AreasViewModel(graph.areas, graph.tags, graph.io) }
                AreasScreen(viewModel = areasViewModel, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
