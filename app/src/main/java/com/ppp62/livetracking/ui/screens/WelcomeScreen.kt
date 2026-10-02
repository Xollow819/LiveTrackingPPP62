package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.UserRole
import com.ppp62.livetracking.ui.components.*

@Composable
fun WelcomeScreen(onRole: (UserRole) -> Unit, onPreferences: () -> Unit = {}) {
    val blue=MaterialTheme.colorScheme.primary
    val ink=MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassSource(LocalGlassState.current)) {
            val route=Path().apply {
                moveTo(size.width*.85f,0f)
                cubicTo(size.width*.1f,size.height*.2f,size.width*1.4f,size.height*.25f,size.width*.48f,size.height*.48f)
                cubicTo(-size.width*.15f,size.height*.67f,size.width*.9f,size.height*.75f,size.width*.15f,size.height)
            }
            drawPath(route,blue.copy(alpha=.08f),style=Stroke(100.dp.toPx()))
            drawPath(route,blue.copy(alpha=.2f),style=Stroke(1.dp.toPx()))
            drawCircle(blue.copy(alpha=.12f),60.dp.toPx(),Offset(size.width*.72f,size.height*.22f))
            drawCircle(blue,7.dp.toPx(),Offset(size.width*.72f,size.height*.22f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Image(painterResource(com.ppp62.livetracking.R.drawable.logo_ppp),"PPPVenza map pin logo",modifier=Modifier.size(42.dp)); Spacer(Modifier.width(10.dp))
                Text("PPPVenza",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                IconButton(onClick=onPreferences){Icon(Icons.Default.Tune,"Preferences")}
            }
            Spacer(Modifier.height(92.dp))
            Text("Every stop.\nIn view.",style=MaterialTheme.typography.displayLarge,color=ink)
            Text("Your field practical, connected. Follow the route, capture the conditions, keep the record.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            GlassCard(Modifier.fillMaxWidth(),onClick={onRole(UserRole.STUDENT)}) {
                Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonPinCircle,null,tint=blue,modifier=Modifier.size(32.dp)); Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text("Join as a student",style=MaterialTheme.typography.titleLarge); Text("Open your route with a session code",style=MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Default.ArrowForward,null)
                }
            }
            TextButton(onClick={onRole(UserRole.LECTURER)},modifier=Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.School,null); Spacer(Modifier.width(10.dp)); Text("Lecturer workspace")
            }
            Spacer(Modifier.height(24.dp))
            Text("Live location · Private evidence · Reliable records",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
