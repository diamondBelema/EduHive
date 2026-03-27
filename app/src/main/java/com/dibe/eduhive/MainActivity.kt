package com.dibe.eduhive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dibe.eduhive.manager.GlobalTaskManager
import com.dibe.eduhive.nav.EduHiveNavigation
import com.dibe.eduhive.nav.Screen
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val activeTasks by globalTaskManager.activeTasks.collectAsState(initial = emptyList())

                    // Hide the overlay when the user is on the AddMaterial screen —
                    // that screen has its own full progress UI.
                    val currentRoute by navController.currentBackStackEntryAsState()
                    val isOnAddMaterial = currentRoute?.destination?.route
                        ?.startsWith("add_material/") == true

                    Box(modifier = Modifier.fillMaxSize()) {
                        EduHiveNavigation(navController = navController)

                        if (!isOnAddMaterial) {
                            GlobalTaskOverlay(
                                activeTasks = activeTasks,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}