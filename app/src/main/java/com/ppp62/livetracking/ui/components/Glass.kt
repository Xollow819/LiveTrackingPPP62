@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
package com.ppp62.livetracking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.glass.*

val LocalGlassState = staticCompositionLocalOf { HazeState() }
val LocalReduceTransparency = staticCompositionLocalOf { false }
fun Modifier.glassSource(state: HazeState) = hazeSource(state)

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp),
              colors: CardColors? = null, onClick: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    val state=LocalGlassState.current
    val reduced=LocalReduceTransparency.current
    val surface=colors?.containerColor ?: MaterialTheme.colorScheme.surface
    val style=remember(surface,shape) { GlassStyle.regular.then {
        backgroundColor(surface);tint(surface.copy(alpha=.12f));shape(shape)
        specularIntensity(.28f);ambientResponse(.10f)
    } }
    val glass=if(reduced) Modifier.background(surface,shape)
        else Modifier.hazeGlass(HazeInput.Sources(state,retention=HazeSourceRetention.ClearWhenUnavailable),style)
    val click=if(onClick==null) Modifier else Modifier.clickable(onClick=onClick)
    Box(modifier.clip(shape).then(glass)
        .border(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.42f),shape).then(click)) {
        CompositionLocalProvider(LocalContentColor provides (colors?.contentColor ?: MaterialTheme.colorScheme.onSurface)) {Column(content=content)}
    }
}

@Composable
fun GlassNavigation(selected: Int, labels: List<String>, icons: List<androidx.compose.ui.graphics.vector.ImageVector>, onSelect: (Int)->Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),shape=RoundedCornerShape(32.dp)) {
        Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            labels.forEachIndexed { index,label ->
                FilledTonalButton(onClick={onSelect(index)},colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(selected==index) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) {
                    Icon(icons[index],null,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(label,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
