package com.dibe.eduhive.presentation.settings.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.presentation.settings.viewmodel.ModelSettingsInfo
import com.dibe.eduhive.presentation.settings.viewmodel.SettingsEvent
import com.dibe.eduhive.presentation.settings.viewmodel.SettingsMessage
import com.dibe.eduhive.presentation.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        when (message) {
            is SettingsMessage.Success -> snackbarHostState.showSnackbar(message.text)
            is SettingsMessage.Error -> snackbarHostState.showSnackbar(message.text)
        }
        viewModel.onEvent(SettingsEvent.DismissMessage)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSectionHeader(title = "AI Intelligence", icon = Icons.Rounded.Memory)

            if (state.isBusy && state.downloadStatus != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            state.downloadStatus ?: "Working...",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (state.downloadProgress > 0f) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            
            // Model Selection
            state.availableModels.forEach { model ->
                ModelSelectionCard(
                    model = model,
                    isActive = model.id == state.activeModelId,
                    isBusy = state.isBusy && state.busyModelId == model.id,
                    onSelect = { viewModel.onEvent(SettingsEvent.SelectModel(model.id)) },
                    enabled = !state.isBusy
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Network & Sync", icon = Icons.Rounded.NetworkCheck)
            
            SettingsToggleRow(
                title = "Use Mobile Data",
                subtitle = "Allow model downloads over cellular networks",
                icon = Icons.Rounded.DataUsage,
                checked = state.useMobileData,
                enabled = !state.isBusy,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.ToggleMobileData(it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(title = "Advanced", icon = Icons.Rounded.SettingsSuggest)

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Developer Tools",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Clear AI Cache & Models")
                    }

                    OutlinedButton(
                        onClick = { viewModel.onEvent(SettingsEvent.ResetSetupOnNextLaunch) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Show Setup Wizard On Next Launch")
                    }
                }
            }
        }

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text("Clear AI Cache?") },
                text = {
                    Text("This removes downloaded model files and unloads the active model. You can download again from this screen.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCacheDialog = false
                            viewModel.onEvent(SettingsEvent.ClearCache)
                        }
                    ) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ModelSelectionCard(
    model: ModelSettingsInfo,
    isActive: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp) ) {
                    if (model.isRecommended) {
                        AssistChip(onClick = {}, enabled = false, label = { Text("Recommended") })
                    }
                    Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(model.description, style = MaterialTheme.typography.bodyMedium)
                Text(model.sizeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                isBusy -> {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                isActive -> {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                }
                !model.isDownloaded -> {
                Icon(Icons.Rounded.Download, contentDescription = "Download required", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}
