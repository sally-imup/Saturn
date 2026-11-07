package com.sally.saturn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log internal Saturn yang bisa dilihat langsung di layar (tanpa adb).
 * Disimpan juga ke filesDir/saturn_log.txt supaya tetap ada setelah app ditutup.
 */
object AppLog {
    private const val MAX_CHARS = 60_000
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private var file: File? = null

    fun init(dir: File) {
        val f = File(dir, "saturn_log.txt")
        file = f
        _text.value = if (f.exists()) f.readText().takeLast(MAX_CHARS) else ""
    }

    fun i(msg: String) {
        val line = "[${timeFmt.format(Date())}] $msg\n"
        _text.update { current -> (current + line).takeLast(MAX_CHARS) }
        file?.let { runCatching { it.appendText(line) } }
    }

    fun clear() {
        _text.value = ""
        file?.let { runCatching { it.writeText("") } }
    }
}
