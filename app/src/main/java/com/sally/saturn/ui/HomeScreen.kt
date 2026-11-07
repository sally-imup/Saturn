package com.sally.saturn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sally.saturn.R
import com.sally.saturn.core.GameInstaller
import com.sally.saturn.core.GameLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private const val VERSION_ID = "1.16.5"

@Composable
fun HomeScreen(root: File) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Siap") }
    var busy by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("Player") }
    var installed by remember { mutableStateOf<JSONObject?>(null) }

    val installer = remember {
        GameInstaller(root) { msg ->
            scope.launch(Dispatchers.Main) { status = msg }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_saturn),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Text("Saturn", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Minecraft $VERSION_ID",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        installed = installer.install(VERSION_ID)
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        ) {
            Text("Install $VERSION_ID")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !busy && installed != null,
            onClick = {
                installed?.let { json ->
                    GameLauncher(installer.gameDir).launch(VERSION_ID, json, username) {
                        status = it
                    }
                }
            }
        ) {
            Text("Play")
        }

        Spacer(Modifier.height(24.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
