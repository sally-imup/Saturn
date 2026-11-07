package com.sally.saturn.core

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object Net {
    private fun open(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${c.responseCode}: $url")
        }
        return c.inputStream
    }

    fun getText(url: String): String = open(url).use { it.bufferedReader().readText() }

    private fun exists(f: File) = f.isFile && f.length() > 0

    /**
     * Skip kalau file sudah ada. Tiap download pakai nama temp unik supaya
     * dua coroutine yang nulis file yang sama tidak saling tabrakan.
     */
    fun downloadTo(url: String, dest: File) {
        if (exists(dest)) return
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + "." + UUID.randomUUID() + ".part")
        try {
            open(url).use { input ->
                tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
            if (!tmp.renameTo(dest)) {
                // Kalau download lain sudah selesai duluan, tidak perlu apa-apa
                if (exists(dest)) return
                tmp.copyTo(dest, overwrite = true)
            }
        } finally {
            tmp.delete()
        }
    }
}
