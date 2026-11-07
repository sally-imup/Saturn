package com.sally.saturn.core

import java.io.File

object JvmLauncher {
    init {
        System.loadLibrary("saturn_jre")
    }

    /**
     * Native: fork, lalu child membuka libjvm + libjli dan memanggil JLI_Launch.
     * Stdout/stderr child ditulis ke outFile. Return exit code child.
     */
    external fun runJvm(
        jliPath: String,
        jvmPath: String,
        ldLibraryPath: String,
        args: Array<String>,
        outFile: String
    ): Int

    /** Tes JVM: jalankan `java -version`. Semua langkahnya masuk ke AppLog. */
    fun runVersion(home: File, workDir: File): String {
        AppLog.i("Cari libjli.so di ${home.absolutePath}")
        val jli = home.walkTopDown().maxDepth(5).firstOrNull { it.name == "libjli.so" }
        if (jli == null) {
            AppLog.i("libjli.so tidak ditemukan")
            error("libjli.so tidak ditemukan di ${home.absolutePath}")
        }
        val archDir = jli.parentFile.parentFile
        val jvmDir = File(archDir, "server")
        val jvm = File(jvmDir, "libjvm.so")
        AppLog.i("libjli : ${jli.absolutePath}")
        AppLog.i("libjvm : ${jvm.absolutePath} (ada=${jvm.isFile})")
        AppLog.i("isi ${archDir.name}/: ${archDir.list()?.sorted()?.joinToString() ?: "-"}")
        if (!jvm.isFile) error("libjvm.so tidak ada di ${jvmDir.absolutePath}")

        val ld = listOf(
            jli.parentFile.absolutePath,
            jvmDir.absolutePath,
            archDir.absolutePath,
            "/system/lib64"
        ).joinToString(":")
        AppLog.i("LD_LIBRARY_PATH: $ld")
        val javaBin = File(home, "bin/java")
        AppLog.i("bin/java: ada=${javaBin.exists()} executable=${javaBin.canExecute()}")

        val out = File(workDir, "jvm_out.txt")
        out.delete()
        AppLog.i("Menjalankan: java -version")
        val rc = runJvm(jli.absolutePath, jvm.absolutePath, ld, arrayOf("-version"), out.absolutePath)
        AppLog.i("exit code: $rc")

        val text = if (out.exists()) out.readText() else "(tidak ada output)"
        AppLog.i("--- output JVM ---")
        text.lines().filter { it.isNotBlank() }.forEach { AppLog.i(it) }
        AppLog.i("--- selesai ---")
        return text
    }
}
