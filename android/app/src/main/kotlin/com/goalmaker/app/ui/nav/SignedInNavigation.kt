package com.goalmaker.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.ui.home.HomeScreen
import com.goalmaker.app.ui.settings.SettingsScreen
import com.goalmaker.app.ui.settings.SettingsViewModel

/** The signed-in part of the app: a Navigation 3 back stack starting at Today. */
@Composable
fun SignedInNavigation(graph: AppGraph, email: String) {
    val backStack = rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(email = email, onOpenSettings = { backStack.add(SettingsKey) })
            }
            entry<SettingsKey> {
                val settingsViewModel = viewModel {
                    SettingsViewModel(
                        auth = graph.auth,
                        settings = graph.settings,
                        updates = graph.updates,
                        appInfo = graph.appInfo,
                        restartApp = graph.restartApp,
                    )
                }
                SettingsScreen(viewModel = settingsViewModel, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
