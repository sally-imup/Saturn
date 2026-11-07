package com.sally.saturn.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Cek rules di library Mojang. Android dianggap "linux" (sama seperti Pojav). */
internal fun libraryAllowed(lib: JSONObject): Boolean {
    val rules = lib.optJSONArray("rules") ?: return true
    var allowed = false
    for (i in 0 until rules.length()) {
        val r = rules.getJSONObject(i)
        if (r.has("features")) continue
        val os = r.optJSONObject("os")?.optString("name")
        if (os == null || os == "linux") {
            allowed = r.getString("action") == "allow"
        }
    }
    return allowed
}

class GameInstaller(
    private val root: File,
    private val log: (String) -> Unit
) {
    private val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val assetBase = "https://resources.download.minecraft.net"

    val gameDir = File(root, ".minecraft")
    private val versionsDir = File(gameDir, "versions")
    private val librariesDir = File(gameDir, "libraries")
    private val assetsDir = File(gameDir, "assets")

    /** Return JSON versi (dipakai launcher). */
    suspend fun install(versionId: String): JSONObject = withContext(Dispatchers.IO) {
        log("Ambil daftar versi...")
        val manifest = JSONObject(Net.getText(manifestUrl))
        val versions = manifest.getJSONArray("versions")

        var metaUrl: String? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.getString("id") == versionId) {
                metaUrl = v.getString("url")
                break
            }
        }
        requireNotNull(metaUrl) { "Versi $versionId tidak ditemukan" }

        val vDir = File(versionsDir, versionId)
        vDir.mkdirs()
        val json = JSONObject(Net.getText(metaUrl))
        File(vDir, "$versionId.json").writeText(json.toString())

        log("Download client.jar...")
        val client = json.getJSONObject("downloads").getJSONObject("client")
        Net.downloadTo(client.getString("url"), File(vDir, "$versionId.jar"))

        val sem = Semaphore(8)

        log("Download libraries...")
        // Satu artifact bisa muncul berkali-kali di manifest (beda rules),
        // jadi dedupe by path supaya tidak ada dua download ke file yang sama.
        val libs = json.getJSONArray("libraries")
        val libTasks = LinkedHashMap<String, String>() // path -> url
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            if (!libraryAllowed(lib)) continue
            // TODO: natives (classifiers) belum diunduh
            val artifact = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            libTasks.putIfAbsent(artifact.getString("path"), artifact.getString("url"))
        }
        coroutineScope {
            libTasks.map { (path, url) ->
                async {
                    sem.withPermit {
                        Net.downloadTo(url, File(librariesDir, path))
                    }
                }
            }.awaitAll()
        }

        log("Download asset index...")
        val idx = json.getJSONObject("assetIndex")
        val indexFile = File(assetsDir, "indexes/${idx.getString("id")}.json")
        Net.downloadTo(idx.getString("url"), indexFile)
        val objects = JSONObject(indexFile.readText()).getJSONObject("objects")
        // Beberapa asset punya hash sama, dedupe supaya tidak tabrakan
        val hashes = objects.keys().asSequence()
            .map { objects.getJSONObject(it).getString("hash") }
            .distinct()
            .toList()

        log("Download ${hashes.size} assets...")
        coroutineScope {
            hashes.map { hash ->
                async {
                    sem.withPermit {
                        val prefix = hash.substring(0, 2)
                        Net.downloadTo(
                            "$assetBase/$prefix/$hash",
                            File(assetsDir, "objects/$prefix/$hash")
                        )
                    }
                }
            }.awaitAll()
        }

        log("Install $versionId selesai")
        json
    }
}
