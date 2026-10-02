package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.remote.CheckpointRow

@Composable
fun CheckpointEditor(checkpoint:CheckpointRow,onDismiss:()->Unit,onSave:(CheckpointRow,(String?)->Unit)->Unit) {
    var name by rememberSaveable(checkpoint.id){mutableStateOf(checkpoint.name)}
    var radius by rememberSaveable(checkpoint.id){mutableStateOf(checkpoint.radiusM.toInt().toString())}
    var instructions by rememberSaveable(checkpoint.id){mutableStateOf(checkpoint.instructions)}
    var photo by rememberSaveable(checkpoint.id){mutableStateOf(checkpoint.requiresPhoto)}
    var temperature by rememberSaveable(checkpoint.id){mutableStateOf(checkpoint.requiresTemperature)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(checkpoint.name.isBlank()) "New checkpoint" else "Edit checkpoint")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        OutlinedTextField(name,{name=it},label={Text("Checkpoint name")},singleLine=true)
        OutlinedTextField(radius,{radius=it},label={Text("Arrival radius (m)")},singleLine=true)
        OutlinedTextField(instructions,{instructions=it},label={Text("Instructions")},minLines=2)
        Row{Checkbox(photo,{photo=it});Text("Require photo",Modifier.padding(top=12.dp))}
        Row{Checkbox(temperature,{temperature=it});Text("Require temperature",Modifier.padding(top=12.dp))}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy,onClick={
        val meters=radius.toDoubleOrNull()
        if(name.isBlank()||meters==null||meters !in 20.0..500.0) error="Enter a name and radius between 20 and 500 m"
        else {busy=true;onSave(checkpoint.copy(name=name.trim(),radiusM=meters,instructions=instructions.trim(),requiresPhoto=photo,requiresTemperature=temperature,requiresWeight=false)){busy=false;error=it;if(it==null)onDismiss()}}
    }){Text(if(busy) "Saving…" else "Save")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}
