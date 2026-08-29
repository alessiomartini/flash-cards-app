package com.lexi.flashcards.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lexi.flashcards.ui.cards.AddEditCardScreen
import com.lexi.flashcards.ui.cards.CardListScreen
import com.lexi.flashcards.ui.home.HomeScreen
import com.lexi.flashcards.ui.importer.ImportScreen
import com.lexi.flashcards.ui.settings.SettingsScreen
import com.lexi.flashcards.ui.study.StudyScreen

private object Routes {
    const val HOME = "home"
    const val STUDY = "study"
    const val CARDS = "cards"
    const val ADD_CARD = "add_card"
    const val EDIT_CARD = "edit_card/{cardId}"
    const val IMPORT = "import"
    const val SETTINGS = "settings"

    fun editCard(cardId: Long) = "edit_card/$cardId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.CARDS, "Carte", Icons.Filled.Style),
    BottomTab(Routes.IMPORT, "Importa", Icons.Filled.FileUpload),
    BottomTab(Routes.SETTINGS, "Impostazioni", Icons.Filled.Settings),
)

@Composable
fun LexiApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onStudyClick = { navController.navigate(Routes.STUDY) })
            }
            composable(Routes.STUDY) {
                StudyScreen(onFinished = { navController.popBackStack() })
            }
            composable(Routes.CARDS) {
                CardListScreen(
                    onAddCard = { navController.navigate(Routes.ADD_CARD) },
                    onEditCard = { id -> navController.navigate(Routes.editCard(id)) },
                )
            }
            composable(Routes.ADD_CARD) {
                AddEditCardScreen(
                    cardId = null,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EDIT_CARD,
                arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val cardId = backStackEntry.arguments?.getLong("cardId")
                AddEditCardScreen(
                    cardId = cardId,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    onDone = {
                        navController.navigate(Routes.CARDS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
