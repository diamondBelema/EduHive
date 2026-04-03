package com.dibe.eduhive.presentation.hiveList.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.presentation.hiveList.viewmodel.HiveListEvent
import com.dibe.eduhive.presentation.hiveList.viewmodel.HiveListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiveListScreen(
    viewModel: HiveListViewModel = hiltViewModel(),
    onHiveSelected: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }

    val isFabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    LaunchedEffect(state.selectedHiveId) {
        state.selectedHiveId?.let { hiveId ->
            onHiveSelected(hiveId)
            viewModel.onEvent(HiveListEvent.ClearSelectedHive)
        }
    }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            runCatching { searchFocusRequester.requestFocus() }.onFailure { /* Focus unavailable; no-op */ }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.isSearchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "title_search"
                    ) { isSearching ->
                        if (isSearching) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onEvent(HiveListEvent.UpdateSearch(it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                                placeholder = { Text("Search hives…") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.onEvent(HiveListEvent.UpdateSearch("")) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("EduHive", fontWeight = FontWeight.Black)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = state.isSearchActive,
                        transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                        label = "action_search"
                    ) { isSearching ->
                        if (isSearching) {
                            IconButton(onClick = { viewModel.onEvent(HiveListEvent.ClearSearch) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close search")
                            }
                        } else {
                            Row {
                                IconButton(onClick = { viewModel.onEvent(HiveListEvent.ToggleSearch) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search hives")
                                }
                                IconButton(onClick = { viewModel.onEvent(HiveListEvent.ShowArchiveSheet) }) {
                                    Icon(Icons.Rounded.Inventory2, contentDescription = "View archive")
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !state.isSearchActive,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.onEvent(HiveListEvent.ShowCreateDialog) },
                    expanded = isFabExpanded,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Create Hive") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val displayHives = state.filteredHives
            when {
                state.isLoading && state.hives.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }

                state.hives.isEmpty() -> {
                    EmptyHivesState(
                        onCreateClick = { viewModel.onEvent(HiveListEvent.ShowCreateDialog) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                displayHives.isEmpty() && state.searchQuery.isNotBlank() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No hives match \"${state.searchQuery}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Try a different name or description.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
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
                    }
                }
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                state.error?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.onEvent(HiveListEvent.ClearError) }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }

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
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HiveCardExpressive(
    hive: Hive,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hive.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    hive.description?.let { desc ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp).size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Box {
                        IconButton(onClick = { showContextMenu = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    showContextMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                                onClick = {
                                    showContextMenu = false
                                    onArchive()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Active ${formatLastAccessed(hive.lastAccessedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    "Archived Hives",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                archivedHives.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No archived hives",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Archived hives will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    archivedHives.forEach { hive ->
                        ArchivedHiveItem(
                            hive = hive,
                            onUnarchive = { onUnarchive(hive.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedHiveItem(
    hive: Hive,
    onUnarchive: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.School,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hive.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                hive.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            FilledTonalButton(
                onClick = onUnarchive,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Rounded.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Restore", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun EmptyHivesState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Your Hive is empty",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Create a study hive to start turning your materials into intelligence.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onCreateClick,
            modifier = Modifier.height(56.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Initialize New Hive", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHiveBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
        ) {
            Text(
                "New Knowledge Hive",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Hive Name") },
                placeholder = { Text("e.g. Physics Core Concepts") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Goal or Description") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, description.ifBlank { null })
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Establish Hive", style = MaterialTheme.typography.titleMedium)
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
    val sheetState = rememberModalBottomSheetState()
    var name by remember(hive.id) { mutableStateOf(hive.name) }
    var description by remember(hive.id) { mutableStateOf(hive.description ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
        ) {
            Text(
                "Edit Hive",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Hive Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Goal or Description") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, description.ifBlank { null })
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Save Changes", style = MaterialTheme.typography.titleMedium)
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
        icon = {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete \"${hive.name}\"?") },
        text = {
            Text(
                "This will permanently delete the hive and ALL its concepts, flashcards, and quizzes. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
