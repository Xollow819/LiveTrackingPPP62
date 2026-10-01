package com.ppp62.livetracking.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.ppp62.livetracking.data.UserRole
import com.ppp62.livetracking.ui.screens.*

@Composable
fun PPP62App(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { _ ->
        NavHost(navController = nav, startDestination = "welcome") {
            composable("welcome") { WelcomeScreen { role -> nav.navigate(if (role == UserRole.STUDENT) "student" else "lecturer") } }
            composable("student") { StudentScreen(vm, onBack = { nav.popBackStack() }, onCheckIn = { nav.navigate("checkin/$it") }) }
            composable("checkin/{checkpointId}") { entry -> CheckInScreen(vm, entry.arguments?.getString("checkpointId").orEmpty()) { nav.popBackStack() } }
            composable("lecturer") { LecturerScreen(vm, onBack = { nav.popBackStack() }, onEditor = { nav.navigate("editor") }, onSubmissions = { nav.navigate("submissions") }) }
            composable("editor") { CheckpointEditorScreen(vm) { nav.popBackStack() } }
            composable("submissions") { SubmissionsScreen(vm) { nav.popBackStack() } }
        }
    }
}
