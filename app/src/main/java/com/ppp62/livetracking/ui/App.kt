package com.ppp62.livetracking.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.ppp62.livetracking.data.UserRole
import com.ppp62.livetracking.ui.screens.*

@Composable
fun PPP62App(vm: AppViewModel = viewModel(), bvm: BackendViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        NavHost(navController = nav, startDestination = "welcome", modifier = Modifier.padding(padding)) {
            composable("welcome") { WelcomeScreen(onRole = { role -> nav.navigate(if (role == UserRole.STUDENT) "student" else "lecturer") }) }
            composable("student") { StudentScreen(vm, bvm, onBack = { nav.popBackStack() }, onCheckIn = { nav.navigate("checkin/$it") }) }
            composable("checkin/{checkpointId}") { entry -> CheckInScreen(vm, entry.arguments?.getString("checkpointId").orEmpty()) { nav.popBackStack() } }
            composable("lecturer") { LecturerScreen(vm, bvm, onBack = { nav.popBackStack() }, onSubmissions = { nav.navigate("submissions") }) }
            composable("submissions") { SubmissionsScreen(vm, bvm) { nav.popBackStack() } }
        }
    }
}
