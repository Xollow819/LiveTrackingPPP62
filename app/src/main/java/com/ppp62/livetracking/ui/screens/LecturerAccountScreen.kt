package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerAccountScreen(bvm:BackendViewModel,onBack:()->Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar={TopAppBar(title={Text("Lecturer workspace")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}})}) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(48.dp))
            Text(if(register) "Your workspace.\nYour group." else "Welcome back.",style=MaterialTheme.typography.headlineLarge)
            Text("Sign in to create routes, monitor students and review private evidence.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            } }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Button(onClick={busy=true; bvm.authenticate(email,password,register){busy=false;error=it}},enabled=!busy&&email.isNotBlank()&&password.length>=8,modifier=Modifier.fillMaxWidth().height(56.dp)) {
                if(busy) CircularProgressIndicator(Modifier.size(22.dp)) else Text(if(register) "Create account" else "Sign in")
            }
            TextButton(onClick={register=!register;error=null}) { Text(if(register) "Already have an account? Sign in" else "Create a lecturer account") }
            if(!register) TextButton(onClick={bvm.resetPassword(email){error=it}},enabled=email.isNotBlank()) { Text("Forgot password?") }
        }
    }
}
