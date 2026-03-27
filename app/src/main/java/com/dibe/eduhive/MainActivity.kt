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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.dibe.eduhive.nav.EduHiveNavigation
import com.dibe.eduhive.ui.theme.EduHiveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                    EduHiveNavigation(navController = navController)
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
