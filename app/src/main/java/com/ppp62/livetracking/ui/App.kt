package com.ppp62.livetracking.ui

import com.ppp62.livetracking.util.UserFacingErrors

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import com.ppp62.livetracking.ui.components.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.ppp62.livetracking.data.UserRole
import com.ppp62.livetracking.ui.screens.*

@Composable
fun PPP62App(vm: AppViewModel = viewModel(), bvm: BackendViewModel = viewModel()) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.ppp62.livetracking.PPP62Application
    val recovery by app.backend.recoveryRequested.collectAsState()
    val scope=rememberCoroutineScope()
    var recoveryPassword by remember {mutableStateOf("")}
    var recoveryError by remember {mutableStateOf<String?>(null)}
    var savingPassword by remember {mutableStateOf(false)}
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    val glassState = remember { HazeState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val appearance = remember { context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE) }
    var reduced by remember { mutableStateOf(appearance.getBoolean("opaque",false)) }
    DisposableEffect(appearance) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> reduced=appearance.getBoolean("opaque",false) }
        appearance.registerOnSharedPreferenceChangeListener(listener)
        onDispose {appearance.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    if(recovery) AlertDialog(onDismissRequest={},title={Text("Choose a new password")},text={
        androidx.compose.foundation.layout.Column {
            OutlinedTextField(recoveryPassword,{recoveryPassword=it},label={Text("New password")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true)
            recoveryError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!savingPassword&&recoveryPassword.length>=8,onClick={
        savingPassword=true
        scope.launch {try {app.backend.changePassword(recoveryPassword);recoveryPassword="";bvm.refreshAuthentication()}catch(e:Exception){recoveryError=UserFacingErrors.message(e, "Unable to change your password. Please try again.")}finally{savingPassword=false}}
    }){Text(if(savingPassword) "Saving…" else "Save password")}})
    CompositionLocalProvider(LocalGlassState provides glassState, LocalReduceTransparency provides reduced) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        NavHost(navController = nav, startDestination = "welcome", modifier = Modifier.fillMaxSize().padding(padding)) {
            composable("welcome") { WelcomeScreen(onRole = { role -> scope.launch {
                val nextRole=if(role==UserRole.STUDENT) "student" else "lecturer"
                if(bvm.onlineSession.value!=null && bvm.myRole.value!=nextRole){bvm.leaveSession().join();vm.resetSession()}
                nav.navigate(nextRole)
            } }, onPreferences = {nav.navigate("preferences")}) }
            composable("student") { StudentScreen(vm, bvm, onBack = { nav.popBackStack() }, onCheckIn = { nav.navigate("checkin/$it") }) }
            composable("checkin/{checkpointId}") { entry -> CheckInScreen(vm, entry.arguments?.getString("checkpointId").orEmpty()) { nav.popBackStack() } }
            composable("lecturer") { LecturerScreen(vm, bvm, onBack = { nav.popBackStack() }, onSubmissions = { nav.navigate("submissions") }) }
            composable("preferences") { PreferencesScreen(bvm) {nav.popBackStack()} }
            composable("submissions") { SubmissionsScreen(vm, bvm) { nav.popBackStack() } }
        }
    }
    }
}
