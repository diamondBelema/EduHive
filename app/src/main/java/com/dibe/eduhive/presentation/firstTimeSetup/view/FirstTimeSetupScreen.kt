package com.dibe.eduhive.presentation.firstTimeSetup.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.presentation.firstTimeSetup.viewmodel.FirstTimeSetupEvent
import com.dibe.eduhive.presentation.firstTimeSetup.viewmodel.FirstTimeSetupViewModel
import com.dibe.eduhive.presentation.firstTimeSetup.viewmodel.SetupStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstTimeSetupScreen(
    viewModel: FirstTimeSetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                fadeIn(tween(600)) + slideInHorizontally { it / 2 } togetherWith
                fadeOut(tween(400)) + slideOutHorizontally { -it / 2 }
            },
            label = "setup_step_transition"
        ) { step ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (step) {
                    SetupStep.WELCOME -> WelcomeStep(onStart = { viewModel.onEvent(FirstTimeSetupEvent.StartSetup) })
                    SetupStep.MODEL_SELECTION -> ModelSelectionStep(
                        availableModels = state.availableModels,
                        selectedModelId = state.selectedModel?.id,
                        onSelect = { viewModel.onEvent(FirstTimeSetupEvent.SelectModel(it)) },
                        onConfirm = { state.selectedModel?.let { viewModel.onEvent(FirstTimeSetupEvent.DownloadModel(it.id)) } }
                    )
                    SetupStep.DOWNLOADING -> DownloadingStep(
                        progress = state.downloadProgress,
                        status = state.downloadStatus,
                        error = state.error,
                        onRetry = { viewModel.onEvent(FirstTimeSetupEvent.RetryDownload) }
                    )
                    SetupStep.COMPLETE -> FinalStep(onFinish = { viewModel.onEvent(FirstTimeSetupEvent.CompleteSetup) })
                }
            }
        }
    }
}

@Composable
fun WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Hive, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            "Build your Knowledge Hive",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "EduHive uses powerful on-device AI to turn your documents and notes into interactive study aids.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun ModelSelectionStep(
    availableModels: List<com.dibe.eduhive.data.source.ai.ModelInfo>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Choose your AI engine",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Select the model that best fits your device performance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        availableModels.forEach { model ->
            SetupModelCard(
                name = model.name,
                description = model.description,
                size = "${(model.sizeBytes / 1024 / 1024)} MB",
                isSelected = model.id == selectedModelId,
                isRecommended = model.recommended,
                onSelect = { onSelect(model.id) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onConfirm,
            enabled = selectedModelId != null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Initialize Engine", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SetupModelCard(
    name: String,
    description: String,
    size: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Column() {
                    if (isRecommended) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                            Text(
                                "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

                }
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            RadioButton(selected = isSelected, onClick = onSelect)
        }
    }
}

@Composable
fun DownloadingStep(
    progress: Float,
    status: String,
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(200.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(200.dp),
                strokeWidth = 16.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            if (error != null) "Initialization Failed" else "Configuring Engine",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            error ?: status,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRetry, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Retry Download")
            }
        }
    }
}

@Composable
fun FinalStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            "Hive logic initialized",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Your local AI engine is ready. Everything you process stays on your device.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Enter the Hive", style = MaterialTheme.typography.titleMedium)
        }
    }
}
