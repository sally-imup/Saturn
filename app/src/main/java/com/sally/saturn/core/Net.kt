package com.sally.saturn.core

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Net {
    private fun open(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${c.responseCode}: $url")
        }
        return c.inputStream
    }

    fun getText(url: String): String = open(url).use { it.bufferedReader().readText() }

    /** Skip kalau file sudah ada, supaya install ulang tidak download lagi. */
    fun downloadTo(url: String, dest: File) {
        if (dest.isFile && dest.length() > 0) return
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        open(url).use { input ->
            tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }
}
