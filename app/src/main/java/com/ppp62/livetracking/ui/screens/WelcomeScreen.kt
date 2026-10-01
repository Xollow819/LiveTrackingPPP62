package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.UserRole

@Composable
fun WelcomeScreen(onRole: (UserRole) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Route, null, Modifier.padding(18.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
        Text("Live Tracking PPP62", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Field supervision for fish transportation and distribution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        RoleCard("Student", "Join a practical session, share the team vehicle location, and submit checkpoint evidence.", Icons.Default.PersonPinCircle) { onRole(UserRole.STUDENT) }
        Spacer(Modifier.height(12.dp))
        RoleCard("Lecturer", "Monitor teams, review exceptions, manage checkpoints, and export reports.", Icons.Default.Dashboard) { onRole(UserRole.LECTURER) }
        Spacer(Modifier.height(24.dp))
        Text("OpenStreetMap • Local-first • No paid API key", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun RoleCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null) } }
}
