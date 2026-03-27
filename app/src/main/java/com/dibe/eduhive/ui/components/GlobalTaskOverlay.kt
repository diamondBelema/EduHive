package com.dibe.eduhive.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dibe.eduhive.manager.TaskProgress

@Composable
fun GlobalTaskOverlay(
    activeTasks: List<TaskProgress>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeTasks.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 32.dp) // Lifted up to avoid blocking system bars/FABs
    ) {
        val currentTask = activeTasks.firstOrNull() ?: return@AnimatedVisibility
        
        // Compact "Pill" design instead of a full Card
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                // Circular indicator is less intrusive than a linear one
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    if (currentTask.isIndeterminate) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { currentTask.progress },
                            strokeWidth = 3.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = currentTask.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = currentTask.status,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                if (activeTasks.size > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    VerticalDivider(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+${activeTasks.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
