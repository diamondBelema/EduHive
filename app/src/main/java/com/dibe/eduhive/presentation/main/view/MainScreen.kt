package com.dibe.eduhive.presentation.main.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dibe.eduhive.presentation.documentChat.view.DocumentChatScreen
import com.dibe.eduhive.presentation.hiveList.view.HiveListScreen
import com.dibe.eduhive.presentation.studyNow.view.StudyNowScreen

@Composable
fun MainScreen(
    onHiveSelected: (String) -> Unit,
    onNavigateToFlashcardStudy: () -> Unit,
    onNavigateToQuizStudy: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Hide bottom bar when in Chat (Tab 1) for an immersive "Google Chat" experience
    val showBottomBar = selectedTab != 1

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = "Hives") },
                        label = { Text("Hives") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.Chat, contentDescription = "Tutor") },
                        label = { Text("Tutor") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.MenuBook, contentDescription = "Study") },
                        label = { Text("Study") }
                    )
                }
            }
        },
        // Remove default scaffold insets to prevent the top gap issue
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Apply bottom padding ONLY if the bar is showing
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            AnimatedVisibility(
                visible = selectedTab == 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                HiveListScreen(
                    onHiveSelected = onHiveSelected
                )
            }
            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                DocumentChatScreen(
                    onNavigateBack = { selectedTab = 0 }
                )
            }
            AnimatedVisibility(
                visible = selectedTab == 2,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                StudyNowScreen(
                    onStartFlashcards = onNavigateToFlashcardStudy,
                    onStartQuiz = onNavigateToQuizStudy,
                    onStartExamMode = {
                        onNavigateToFlashcardStudy()
                    }
                )
            }
        }
    }
}
