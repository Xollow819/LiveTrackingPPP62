package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.UserRole

@Composable
fun WelcomeScreen(onRole: (UserRole) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(32.dp)), color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)))) {
                Column(Modifier.align(Alignment.BottomStart).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f)) {
                        Icon(Icons.Default.Route, "", Modifier.padding(13.dp).size(34.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text("PPPVenza", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Field practicals, in view.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Live routes, clear checkpoints, and reliable records for every fish transport run.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .84f))
                }
                Icon(Icons.Default.Explore, null, Modifier.align(Alignment.TopEnd).padding(25.dp).size(94.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .15f))
            }
        }
        Text("Choose your role", style = MaterialTheme.typography.titleLarge)
        RoleCard("Student", "Join a field session and record each stop.", Icons.Default.PersonPinCircle) { onRole(UserRole.STUDENT) }
        RoleCard("Lecturer", "Plan the route and monitor the group live.", Icons.Default.Dashboard) { onRole(UserRole.LECTURER) }
        Text("OpenStreetMap  ·  Offline-ready", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoleCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(11.dp).size(27.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
