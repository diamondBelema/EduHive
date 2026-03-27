package com.dibe.eduhive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dibe.eduhive.manager.GlobalTaskManager
import com.dibe.eduhive.manager.TaskType
import com.dibe.eduhive.nav.EduHiveNavigation
import com.dibe.eduhive.ui.components.GlobalTaskOverlay
import com.dibe.eduhive.ui.theme.EduHiveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var globalTaskManager: GlobalTaskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EduHiveTheme {
                // Handle Notification Permissions for Android 13+
                RequestNotificationPermission()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val activeTasks by globalTaskManager.activeTasks.collectAsState(initial = emptyList())

                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    // Logic to determine if we should show the overlay
                    val showOverlay = remember(currentRoute, activeTasks) {
                        activeTasks.isNotEmpty() && !activeTasks.any { task ->
                            when (task.type) {
                                TaskType.MATERIAL -> currentRoute?.startsWith("add_material") == true
                                TaskType.FLASHCARD, TaskType.QUIZ -> currentRoute?.startsWith("concept_list") == true
                                else -> false
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        EduHiveNavigation(navController = navController)

                        if (showOverlay) {
                            GlobalTaskOverlay(
                                activeTasks = activeTasks,
                                onTaskClick = { task ->
                                    val route = when (task.type) {
                                        TaskType.MATERIAL -> "add_material/${task.hiveId}"
                                        TaskType.FLASHCARD, TaskType.QUIZ -> "concept_list/${task.hiveId}"
                                        else -> null
                                    }
                                    route?.let { 
                                        navController.navigate(it) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RequestNotificationPermission() {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var hasPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted -> hasPermission = isGranted }
            )

            LaunchedEffect(Unit) {
                if (!hasPermission) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
