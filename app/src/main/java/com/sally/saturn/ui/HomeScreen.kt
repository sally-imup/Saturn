package com.sally.saturn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sally.saturn.R
import com.sally.saturn.core.AppLog
import com.sally.saturn.core.GameInstaller
import com.sally.saturn.core.GameLauncher
import com.sally.saturn.core.JreInstaller
import com.sally.saturn.core.JvmLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val VERSION_ID = "1.16.5"

@Composable
fun HomeScreen(root: File) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    remember { AppLog.init(root); Unit }
    val logText by AppLog.text.collectAsState()
    val logScroll = rememberScrollState()

    var status by remember { mutableStateOf("Siap") }
    var busy by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("Player") }
    var installed by remember { mutableStateOf<JSONObject?>(null) }

    // Semua pesan: masuk log layar + status singkat
    val logger: (String) -> Unit = { msg ->
        AppLog.i(msg)
        scope.launch(Dispatchers.Main) { status = msg }
    }

    val installer = remember { GameInstaller(root, logger) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_saturn),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Text("Saturn", style = MaterialTheme.typography.headlineMedium)
        Text("Minecraft $VERSION_ID", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        installed = withContext(Dispatchers.IO) { installer.install(VERSION_ID) }
                    } catch (e: Throwable) {
                        AppLog.i("ERROR install: ${e.javaClass.simpleName}: ${e.message}")
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
                    GameLauncher(installer.gameDir).launch(VERSION_ID, json, username, logger)
                }
            }
        ) {
            Text("Play")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val home = withContext(Dispatchers.IO) {
                            JreInstaller.ensureInstalled(context, root, logger)
                        }
                        status = "Menjalankan JVM..."
                        withContext(Dispatchers.IO) { JvmLauncher.runVersion(home, root) }
                        status = "Tes JVM selesai"
                    } catch (e: Throwable) {
                        AppLog.i("ERROR JVM: ${e.javaClass.simpleName}: ${e.message}")
                        status = "Error: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        ) {
            Text("Test JVM (-version)")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = { AppLog.clear() }) {
            Text("Hapus log")
        }

        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(12.dp))
        Text("Log", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFF0E0E14))
                .padding(8.dp)
                .verticalScroll(logScroll)
        ) {
            SelectionContainer {
                Text(
                    text = logText.ifEmpty { "(kosong)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        // Auto scroll ke baris terakhir
        androidx.compose.runtime.LaunchedEffect(logText) {
            logScroll.scrollTo(logScroll.maxValue)
        }
    }
}
