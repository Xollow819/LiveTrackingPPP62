package com.ppp62.livetracking.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.util.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@Composable
fun MapExploreControls(onTarget:(GeoPoint)->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    fun locate() { scope.launch { busy=true;error=null; try {
        val point=DeviceLocation.currentOrLastKnown(context)
        if(point!=null) onTarget(point) else error="No location fix. Enable device location and retry."
    } finally {busy=false} } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if(grants.values.any {it}) locate() else error="Location permission is required. Enable it in app settings."
    }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        MapControlButton(Icons.Default.Search,"Search places",{search=true})
        MapControlButton(Icons.Default.MyLocation,"Find my location",{if(DeviceLocation.hasPermission(context)) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},busy=busy)
    }
    if(search || error!=null) AlertDialog(onDismissRequest={search=false;error=null},title={Text(if(search) "Find a place" else "Location")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(search) {
                OutlinedTextField(query,{query=it},label={Text("Place name")},singleLine=true)
                results.forEach { result -> TextButton(onClick={onTarget(GeoPoint(result.lat,result.lon));search=false}) {Text(result.detail,maxLines=2)} }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick={if(search) scope.launch {
        busy=true;error=null;try { results=PlaceSearch.search(query);if(results.isEmpty()) error="No places found" }
        catch(e:Exception){error="Search unavailable. Please retry."} finally {busy=false}
    } else { context.startActivity(if(DeviceLocation.hasPermission(context)) android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS) else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")));error=null }}){Text(if(search) "Search" else if(DeviceLocation.hasPermission(context)) "Location settings" else "App settings")}},dismissButton={TextButton(onClick={search=false;error=null}){Text("Close")}})
}
