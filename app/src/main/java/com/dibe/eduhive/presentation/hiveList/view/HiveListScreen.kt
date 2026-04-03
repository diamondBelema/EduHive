package com.dibe.eduhive.presentation.hiveList.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.presentation.hiveList.viewmodel.HiveListEvent
import com.dibe.eduhive.presentation.hiveList.viewmodel.HiveListViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiveListScreen(
    viewModel: HiveListViewModel = hiltViewModel(),
    onHiveSelected: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val isFabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    LaunchedEffect(state.selectedHiveId) {
        state.selectedHiveId?.let { hiveId ->
            onHiveSelected(hiveId)
            viewModel.onEvent(HiveListEvent.ClearSelectedHive)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header with expressive branding
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Edu Hive",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.displaySmall,
                                letterSpacing = (-2).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Your Intelligence Hub",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.onEvent(HiveListEvent.ShowArchiveSheet) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(Icons.Rounded.Inventory2, contentDescription = "Archive")
                        }
                    }
                    
                    // Expressive Persistent Search Widget
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        "Search your hives...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.onEvent(HiveListEvent.UpdateSearch(it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true
                                )
                            }
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onEvent(HiveListEvent.UpdateSearch("")) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hive List with Bouncy Entry
            val displayHives = state.filteredHives
            when {
                state.isLoading && state.hives.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }

                state.hives.isEmpty() -> {
                    EmptyHivesState(
                        onCreateClick = { viewModel.onEvent(HiveListEvent.ShowCreateDialog) }
                    )
                }

                displayHives.isEmpty() && state.searchQuery.isNotBlank() -> {
                    NoSearchResults(state.searchQuery)
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayHives, key = { it.id }) { hive ->
                            HiveCardExpressive(
                                hive = hive,
                                onClick = { viewModel.onEvent(HiveListEvent.SelectHive(hive.id)) },
                                onEdit = { viewModel.onEvent(HiveListEvent.ShowEditDialog(hive)) },
                                onArchive = { viewModel.onEvent(HiveListEvent.ArchiveHive(hive.id)) },
                                onDelete = { viewModel.onEvent(HiveListEvent.ShowDeleteConfirm(hive)) }
                            )
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }

        // Expressive Floating Action Button
        ExtendedFloatingActionButton(
            onClick = { viewModel.onEvent(HiveListEvent.ShowCreateDialog) },
            expanded = isFabExpanded,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(28.dp)) },
            text = { Text("New Hive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(24.dp), // Expressive Pill
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
        )
    }

    // Sheets & Dialogs (unchanged logic, just ensuring consistency)
    if (state.showCreateDialog) {
        CreateHiveBottomSheet(
            onDismiss = { viewModel.onEvent(HiveListEvent.HideCreateDialog) },
            onCreate = { name, description ->
                viewModel.onEvent(HiveListEvent.CreateHive(name, description))
            }
        )
    }

    state.hiveToEdit?.let { hive ->
        EditHiveBottomSheet(
            hive = hive,
            onDismiss = { viewModel.onEvent(HiveListEvent.HideEditDialog) },
            onSave = { name, description ->
                viewModel.onEvent(HiveListEvent.EditHive(hive.id, name, description))
            }
        )
    }

    state.hiveToDelete?.let { hive ->
        DeleteHiveDialog(
            hive = hive,
            onDismiss = { viewModel.onEvent(HiveListEvent.HideDeleteConfirm) },
            onConfirm = { viewModel.onEvent(HiveListEvent.DeleteHive(hive.id)) }
        )
    }

    if (state.showArchiveSheet) {
        ArchivedHivesSheet(
            archivedHives = state.archivedHives,
            isLoading = state.isLoadingArchived,
            onDismiss = { viewModel.onEvent(HiveListEvent.HideArchiveSheet) },
            onUnarchive = { hiveId -> viewModel.onEvent(HiveListEvent.UnarchiveHive(hiveId)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiveCardExpressive(
    hive: Hive,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AutoAwesome, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(20.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        hive.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Outlined.AccessTime, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Updated ${formatLastAccessed(hive.lastAccessedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.offset(x = 8.dp, y = (-8).dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Hive") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { onEdit(); showContextMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive Hive") },
                            leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                            onClick = { onArchive(); showContextMenu = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showContextMenu = false }
                        )
                    }
                }
            }

            if (!hive.description.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    hive.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NoSearchResults(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "No Matches Found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "We couldn't find any hive matching \"$query\".",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun EmptyHivesState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.size(160.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AutoAwesome, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        Text(
            "Begin Your Journey",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Create your first Knowledge Hive to start processing your materials into structured intelligence.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 26.sp
        )
        
        Spacer(Modifier.height(56.dp))
        
        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("Initialize Hive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedHivesSheet(
    archivedHives: List<Hive>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onUnarchive: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                "Archived Hives",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (archivedHives.isEmpty()) {
                Text(
                    "No archived hives found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(archivedHives) { hive ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(hive.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    Text(
                                        "Archived ${formatLastAccessed(hive.lastAccessedAt)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Button(
                                    onClick = { onUnarchive(hive.id) },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHiveBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 64.dp)
        ) {
            Text(
                "Initialize New Hive",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Define the scope for your new intelligence core.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Hive Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Focus or Description") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                minLines = 3
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { onCreate(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Establish Hive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHiveBottomSheet(
    hive: Hive,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf(hive.name) }
    var description by remember { mutableStateOf(hive.description ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 64.dp)
        ) {
            Text(
                "Refine Hive",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Hive Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                minLines = 3
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { onSave(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Update Intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun DeleteHiveDialog(
    hive: Hive,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decommission Hive?", fontWeight = FontWeight.Black) },
        text = { Text("This will permanently remove \"${hive.name}\" and all generated knowledge. This action is irreversible.") },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = CircleShape
            ) {
                Text("Decommission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

fun formatLastAccessed(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}
