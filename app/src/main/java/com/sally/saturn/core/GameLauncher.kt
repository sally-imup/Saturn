package com.sally.saturn.core

import org.json.JSONObject
import java.io.File

class GameLauncher(private val gameDir: File) {

    fun classpath(versionId: String, json: JSONObject): String {
        val parts = mutableListOf<String>()
        val libs = json.getJSONArray("libraries")
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            if (!libraryAllowed(lib)) continue
            val a = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            parts += File(gameDir, "libraries/" + a.getString("path")).absolutePath
        }
        parts += File(gameDir, "versions/$versionId/$versionId.jar").absolutePath
        return parts.joinToString(":")
    }

    /**
     * Susun argumen game. Eksekusi JVM belum tersambung.
     * TODO: load JRE dari Amethyst via JNI (libjli/libjvm), set java.library.path
     * ke folder natives, lalu sambungkan renderer GL (bridge seperti Pojav/Amethyst).
     */
    fun launch(
        versionId: String,
        json: JSONObject,
        username: String,
        log: (String) -> Unit
    ) {
        val cp = classpath(versionId, json)
        val mainClass = json.getString("mainClass")
        val assetIndex = json.getJSONObject("assetIndex").getString("id")

        val args = listOf(
            "-cp", cp, mainClass,
            "--username", username,
            "--version", versionId,
            "--gameDir", gameDir.absolutePath,
            "--assetsDir", File(gameDir, "assets").absolutePath,
            "--assetIndex", assetIndex,
            "--accessToken", "0",
            "--userType", "legacy"
        )
        log("Main class: $mainClass (${args.size} argumen siap). Runtime belum dihubungkan.")
    }
}
