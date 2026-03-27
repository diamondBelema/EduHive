package com.dibe.eduhive.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dibe.eduhive.manager.TaskProgress
import com.dibe.eduhive.manager.TaskType

@Composable
fun GlobalTaskOverlay(
    activeTasks: List<TaskProgress>,
    onTaskClick: (TaskProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeTasks.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 48.dp) // Adjusted height
    ) {
        val currentTask = activeTasks.firstOrNull() ?: return@AnimatedVisibility
        
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable { onTaskClick(currentTask) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateContentSize()
            ) {
                // Task-specific expressive icon
                val icon = when(currentTask.type) {
                    TaskType.MATERIAL -> Icons.Rounded.Description
                    TaskType.FLASHCARD -> Icons.Rounded.Style
                    TaskType.QUIZ -> Icons.Rounded.Quiz
                    else -> Icons.Rounded.AutoAwesome
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    CircularProgressIndicator(
                        progress = { if (currentTask.isIndeterminate) 0.6f else currentTask.progress },
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = currentTask.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = currentTask.status,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                if (activeTasks.size > 1) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "+${activeTasks.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
