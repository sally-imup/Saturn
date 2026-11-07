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

    /** Tes JVM: jalankan `java -version` dan kembalikan outputnya. */
    fun runVersion(home: File, workDir: File): String {
        val jli = home.walkTopDown().maxDepth(5).firstOrNull { it.name == "libjli.so" }
            ?: error("libjli.so tidak ditemukan di ${home.absolutePath}")
        val archDir = jli.parentFile.parentFile
        val jvmDir = File(archDir, "server")
        val jvm = File(jvmDir, "libjvm.so")
        if (!jvm.isFile) error("libjvm.so tidak ada di ${jvmDir.absolutePath}")

        val ld = listOf(
            jli.parentFile.absolutePath,
            jvmDir.absolutePath,
            archDir.absolutePath,
            "/system/lib64"
        ).joinToString(":")

        val out = File(workDir, "jvm_out.txt")
        val rc = runJvm(jli.absolutePath, jvm.absolutePath, ld, arrayOf("-version"), out.absolutePath)
        val text = if (out.exists()) out.readText() else "(tidak ada output)"
        return "exit code: $rc\n$text"
    }
}
