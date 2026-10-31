package com.peter.unapker

import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class Part(val name: String, val size: Long, val entry: ZipEntry?)

class SplitBundle(
    val label: String,
    val packageName: String?,
    val parts: List<Part>,
    val skipped: List<String>,
    val obbs: List<ZipEntry>,
    val isPlainApk: Boolean,
) {
    val base: Part? get() = parts.firstOrNull()
    val totalSize: Long get() = parts.sumOf { it.size.coerceAtLeast(0) }

    companion object {
        private const val OBB_PREFIX = "Android/obb/"

        private val ABI_SEGMENTS = mapOf(
            "arm64_v8a" to "arm64-v8a",
            "armeabi_v7a" to "armeabi-v7a",
            "armeabi" to "armeabi",
            "x86_64" to "x86_64",
            "x86" to "x86",
        )

        private val META_FILES = listOf("manifest.json", "info.json", "meta.sai_v2.json", "meta.sai_v1.json")
        private val PACKAGE_KEYS = listOf("package_name", "pname", "package")
        private val LABEL_KEYS = listOf("name", "app_name", "label")
        private val SPLIT_PREFIXES = listOf("split_config.", "config.", "split_")

        fun read(zip: ZipFile, source: File, fallbackLabel: String): SplitBundle {
            if (zip.getEntry("AndroidManifest.xml") != null) {
                val whole = Part("base", source.length(), null)
                return SplitBundle(fallbackLabel, null, listOf(whole), emptyList(), emptyList(), isPlainApk = true)
            }

            val files = zip.entries().toList().filter { !it.isDirectory }
            val obbs = files.filter { it.name.startsWith(OBB_PREFIX, ignoreCase = true) }
            val apks = files.filter { it.name.endsWith(".apk", ignoreCase = true) && it !in obbs }
            val meta = readMeta(zip)

            val packageName = meta?.firstString(PACKAGE_KEYS)
                ?: obbs.firstOrNull()?.name?.removePrefix(OBB_PREFIX)?.substringBefore('/')
            val label = meta?.firstString(LABEL_KEYS) ?: fallbackLabel

            val kept = pickForThisDevice(apks)
            val skipped = (apks - kept.toSet()).map { displayName(it) }
            val base = findBase(kept)
            val ordered = listOfNotNull(base) + (kept - setOfNotNull(base))
            val parts = ordered.map { entry ->
                val name = if (entry == base) "base" else displayName(entry)
                Part(name, entry.size, entry)
            }

            return SplitBundle(label, packageName, parts, skipped, obbs, isPlainApk = false)
        }

        private fun readMeta(zip: ZipFile): JSONObject? {
            val entry = META_FILES.firstNotNullOfOrNull { zip.getEntry(it) } ?: return null
            return runCatching {
                JSONObject(zip.getInputStream(entry).bufferedReader().use { it.readText() })
            }.getOrNull()
        }

        private fun JSONObject.firstString(keys: List<String>): String? =
            keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }

        private fun fileName(entry: ZipEntry) = entry.name.substringAfterLast('/')

        private fun isSplit(entry: ZipEntry) = SPLIT_PREFIXES.any { fileName(entry).startsWith(it) }

        private fun findBase(apks: List<ZipEntry>): ZipEntry? =
            apks.firstOrNull { fileName(it) == "base.apk" }
                ?: apks.firstOrNull { !isSplit(it) }
                ?: apks.maxByOrNull { it.size }

        private fun displayName(entry: ZipEntry): String {
            val bare = fileName(entry).removeSuffix(".apk")
            val prefix = SPLIT_PREFIXES.firstOrNull { bare.startsWith(it) }.orEmpty()
            return bare.removePrefix(prefix).replace('_', '-')
        }

        private fun abiOf(entry: ZipEntry): String? {
            val segments = fileName(entry).split('.')
            return ABI_SEGMENTS[segments.getOrNull(segments.size - 2)]
        }

        // Installing native-lib splits for several ABIs at once can fail, so keep only the best match.
        private fun pickForThisDevice(apks: List<ZipEntry>): List<ZipEntry> {
            val presentAbis = apks.mapNotNull(::abiOf).toSet()
            val bestAbi = Build.SUPPORTED_ABIS.firstOrNull { it in presentAbis } ?: return apks
            return apks.filter { abiOf(it).let { abi -> abi == null || abi == bestAbi } }
        }
    }
}
