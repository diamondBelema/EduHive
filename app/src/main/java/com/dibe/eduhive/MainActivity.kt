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
import com.dibe.eduhive.manager.GlobalTaskManager
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val activeTasks by globalTaskManager.activeTasks.collectAsState(initial = emptyList())
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main App Navigation
                        EduHiveNavigation()
                        
                        // Global Overlay for background tasks
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
