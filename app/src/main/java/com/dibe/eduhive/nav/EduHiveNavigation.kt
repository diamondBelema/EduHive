package com.dibe.eduhive.nav

import com.dibe.eduhive.presentation.addMaterial.view.AddMaterialScreen
import com.dibe.eduhive.presentation.flashcardStudy.view.FlashcardStudyScreen
import com.dibe.eduhive.presentation.hiveList.view.HiveListScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dibe.eduhive.presentation.screens.*

// Route definitions
sealed class Screen(val route: String) {
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
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.HiveList.route
    ) {
        // Hive List Screen
        composable(Screen.HiveList.route) {
            HiveListScreen(
                onHiveSelected = { hiveId ->
                    navController.navigate(Screen.HiveDashboard.createRoute(hiveId))
                }
            )
        }

        // Hive Dashboard Screen
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

        // Add Material Screen
        composable(
            route = Screen.AddMaterial.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            AddMaterialScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Flashcard Study Screen
        composable(
            route = Screen.FlashcardStudy.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            FlashcardStudyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}