package com.dibe.eduhive.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dibe.eduhive.presentation.addMaterial.view.AddMaterialScreen
import com.dibe.eduhive.presentation.conceptList.view.ConceptListScreen
import com.dibe.eduhive.presentation.firstTimeSetup.view.FirstTimeSetupScreen
import com.dibe.eduhive.presentation.flashcardStudy.view.FlashcardStudyScreen
import com.dibe.eduhive.presentation.hiveList.view.HiveListScreen
import com.dibe.eduhive.presentation.reviewList.view.ReviewListScreen
import com.dibe.eduhive.presentation.screens.HiveDashboardScreen
import com.dibe.eduhive.presentation.settings.view.SettingsScreen

sealed class Screen(val route: String) {
    object FirstTimeSetup : Screen("first_time_setup")
    object HiveList : Screen("hive_list")
    object Settings : Screen("settings")
    object ReviewList : Screen("review_list")
    object HiveDashboard : Screen("hive_dashboard/{hiveId}") {
        fun createRoute(hiveId: String) = "hive_dashboard/$hiveId"
    }
    object AddMaterial : Screen("add_material/{hiveId}") {
        fun createRoute(hiveId: String) = "add_material/$hiveId"
    }
    object FlashcardStudy : Screen("flashcard_study/{hiveId}") {
        fun createRoute(hiveId: String) = "flashcard_study/$hiveId"
    }
    object ConceptList : Screen("concept_list/{hiveId}") {
        fun createRoute(hiveId: String) = "concept_list/$hiveId"
    }
}

@Composable
fun EduHiveNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: EduHiveNavViewModel = hiltViewModel()
) {
    val startDestination by viewModel.startDestination.collectAsState()

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

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.HiveDashboard.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hiveId = backStackEntry.arguments?.getString("hiveId") ?: return@composable

            HiveDashboardScreen(
                hiveId = hiveId,
                onNavigateToStudy = {
                    navController.navigate(Screen.FlashcardStudy.createRoute(hiveId))
                },
                onNavigateToAddMaterial = {
                    navController.navigate(Screen.AddMaterial.createRoute(hiveId))
                },
                onNavigateToConcepts = {
                    navController.navigate(Screen.ConceptList.createRoute(hiveId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToReviews = {
                    navController.navigate(Screen.ReviewList.route)
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

        composable(
            route = Screen.ConceptList.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hiveId = backStackEntry.arguments?.getString("hiveId") ?: return@composable
            ConceptListScreen(  // ← was commented out
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ← was entirely missing
        composable(Screen.ReviewList.route) {
            ReviewListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}