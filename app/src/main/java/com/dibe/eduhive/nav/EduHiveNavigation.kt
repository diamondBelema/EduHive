package com.dibe.eduhive.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dibe.eduhive.presentation.addMaterial.view.AddMaterialScreen
import com.dibe.eduhive.presentation.conceptList.view.ConceptListScreen
import com.dibe.eduhive.presentation.conceptList.viewmodel.ConceptListViewModel
import com.dibe.eduhive.presentation.conceptList.viewmodel.GenerationMode
import com.dibe.eduhive.presentation.firstTimeSetup.view.FirstTimeSetupScreen
import com.dibe.eduhive.presentation.flashcardStudy.view.FlashcardStudyScreen
import com.dibe.eduhive.presentation.generationPreview.view.GenerationPreviewScreen
import com.dibe.eduhive.presentation.generationPreview.viewmodel.GenerationPreviewViewModel
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
    // New: GenerationPreview receives hiveId + GenerationMode
    object GenerationPreview : Screen("generation_preview/{hiveId}/{mode}") {
        fun createRoute(hiveId: String, mode: GenerationMode) =
            "generation_preview/$hiveId/${mode.name}"
    }
}

@Composable
fun EduHiveNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: EduHiveNavViewModel = hiltViewModel(),
    sharedContent: SharedGenerationContent = androidx.hilt.navigation.compose.hiltViewModel<EduHiveNavViewModel>()
        .let {
            // We can't inject SharedGenerationContent directly here easily,
            // so we use a companion approach — see note below nav graph.
            SharedGenerationContent()
        }
) {
    val startDestination by viewModel.startDestination.collectAsState()
    if (startDestination == null) return

    // Single SharedGenerationContent instance created at the nav level
    // and passed down as a captured val — lives as long as the nav graph.
    val contentHolder = androidx.compose.runtime.remember { SharedGenerationContent() }

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
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ReviewList.route) {
            ReviewListScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.HiveDashboard.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hiveId = backStackEntry.arguments?.getString("hiveId") ?: return@composable
            HiveDashboardScreen(
                hiveId = hiveId,
                onNavigateToStudy = { navController.navigate(Screen.FlashcardStudy.createRoute(hiveId)) },
                onNavigateToAddMaterial = { navController.navigate(Screen.AddMaterial.createRoute(hiveId)) },
                onNavigateToConcepts = { navController.navigate(Screen.ConceptList.createRoute(hiveId)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToReviews = { navController.navigate(Screen.ReviewList.route) },
                onNavigateToMaterials = { navController.navigate(Screen.AddMaterial.createRoute(hiveId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddMaterial.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            AddMaterialScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.FlashcardStudy.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) {
            FlashcardStudyScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ConceptList.route,
            arguments = listOf(navArgument("hiveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hiveId = backStackEntry.arguments?.getString("hiveId") ?: return@composable
            // Get the ViewModel scoped to this back stack entry
            val conceptListViewModel: ConceptListViewModel = hiltViewModel(backStackEntry)

            ConceptListScreen(
                viewModel = conceptListViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPreview = { mode ->
                    // Stash generated content before navigating
                    val state = conceptListViewModel.state.value
                    contentHolder.set(state.generatedFlashcards, state.generatedQuizPairs)
                    // Clear from concept list VM so LaunchedEffect doesn't re-fire
                    conceptListViewModel.onEvent(
                        com.dibe.eduhive.presentation.conceptList.viewmodel.ConceptListEvent.ClearGenerated
                    )
                    navController.navigate(Screen.GenerationPreview.createRoute(hiveId, mode))
                }
            )
        }

        composable(
            route = Screen.GenerationPreview.route,
            arguments = listOf(
                navArgument("hiveId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) {
            val previewViewModel: GenerationPreviewViewModel = hiltViewModel()

            // Feed generated content into the preview VM on first composition
            val (flashcards, quizPairs) = contentHolder.consume()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                previewViewModel.setGeneratedContent(flashcards, quizPairs)
            }

            val hiveId = it.arguments?.getString("hiveId") ?: return@composable
            GenerationPreviewScreen(
                viewModel = previewViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStudy = {
                    navController.navigate(Screen.FlashcardStudy.createRoute(hiveId)) {
                        // Pop preview off the stack so back from study goes to concept list
                        popUpTo(Screen.GenerationPreview.route) { inclusive = true }
                    }
                }
            )
        }
    }
}