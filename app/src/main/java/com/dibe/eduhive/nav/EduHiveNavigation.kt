package com.dibe.eduhive.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.presentation.addMaterial.view.AddMaterialScreen
import com.dibe.eduhive.presentation.firstTimeSetup.view.FirstTimeSetupScreen
import com.dibe.eduhive.presentation.flashcardStudy.view.FlashcardStudyScreen
import com.dibe.eduhive.presentation.hiveList.view.HiveListScreen
import com.dibe.eduhive.presentation.screens.*

// Route definitions
sealed class Screen(val route: String) {
    object FirstTimeSetup : Screen("first_time_setup")
    object HiveList : Screen("hive_list")
    object HiveDashboard : Screen("hive_dashboard/{hiveId}") {
        fun createRoute(hiveId: String) = "hive_dashboard/$hiveId"
    }
    object AddMaterial : Screen("add_material/{hiveId}") {
        fun createRoute(hiveId: String) = "add_material/$hiveId"
    }
    object FlashcardStudy : Screen("flashcard_study/{hiveId}") {
        fun createRoute(hiveId: String) = "flashcard_study/$hiveId"
    }
}
@Composable
fun EduHiveNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: EduHiveNavViewModel = hiltViewModel()
) {
    val startDestination by viewModel.startDestination.collectAsState()

    // Still loading → show nothing or splash
    if (startDestination == null) return

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable(Screen.FirstTimeSetup.route) {
            FirstTimeSetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.HiveList.route) {
                        popUpTo(Screen.FirstTimeSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.HiveList.route) {
            HiveListScreen(
                onHiveSelected = { hiveId ->
                    navController.navigate(Screen.HiveDashboard.createRoute(hiveId))
                }
            )
        }

        composable(
            route = Screen.HiveDashboard.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hiveId = backStackEntry.arguments?.getString("hiveId") ?: return@composable

            HiveDashboardScreen(
                onNavigateToStudy = {
                    navController.navigate(Screen.FlashcardStudy.createRoute(hiveId))
                },
                onNavigateToAddMaterial = {
                    navController.navigate(Screen.AddMaterial.createRoute(hiveId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AddMaterial.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            AddMaterialScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FlashcardStudy.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            FlashcardStudyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
