package com.sally.saturn.core

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Download runtime JRE 8 (arm64) dari release AngelAuraMC/angelauramc-openjdk-build,
 * lalu unpack ke filesDir/runtime/jre8. Dilakukan sekali, lalu ada marker.
 */
object JreInstaller {
    private const val MARKER = ".installed"
    private const val JRE_URL =
        "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre8/jre8-android-arm64.tar.xz"

    fun ensureInstalled(context: Context, runtimeRoot: File, log: (String) -> Unit): File {
        val home = File(runtimeRoot, "jre8")
        val marker = File(home, MARKER)
        if (marker.exists()) {
            fixExecutables(home)
            return home
        }

        val archive = File(context.cacheDir, "jre8-android-arm64.tar.xz")
        log("Download JRE 8...")
        Net.downloadTo(JRE_URL, archive)

        log("Unpack JRE 8...")
        home.deleteRecursively()
        home.mkdirs()
        archive.inputStream().use { extract(it, home) }

        marker.writeText("ok")
        archive.delete()
        log("JRE siap")
        fixExecutables(home)
        return home
    }

    /** Semua file di bin/ harus bisa dieksekusi (termasuk yang sudah ter-extract sebelumnya). */
    fun fixExecutables(home: File) {
        File(home, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
    }

    private fun extract(input: InputStream, dest: File) {
        TarArchiveInputStream(XZCompressorInputStream(input)).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                when {
                    entry.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        if (out.exists() || isLink(out)) out.delete()
                        // Os.symlink(target, linkPath)
                        Os.symlink(entry.linkName, out.absolutePath)
                    }
                    entry.isDirectory -> out.mkdirs()
                    else -> {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { tar.copyTo(it, 8192) }
                        if ((entry.mode and 0b001001001) != 0 || entry.name.contains("bin/")) {
                            out.setExecutable(true, false)
                        }
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }

    private fun isLink(f: File): Boolean = try {
        java.nio.file.Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }
}
