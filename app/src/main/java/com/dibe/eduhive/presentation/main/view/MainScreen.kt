package com.dibe.eduhive.presentation.main.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Chat, contentDescription = "Chat") },
                    label = { Text("Chat", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.MenuBook, contentDescription = "Study Now") },
                    label = { Text("Study Now", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        // Exam mode: navigate to flashcards first (quiz follows after)
                        onNavigateToFlashcardStudy()
                    }
                )
            }
        }
    }
}
