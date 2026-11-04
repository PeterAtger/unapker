package com.atger.unapker

import android.content.Context
import android.graphics.drawable.Drawable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class AppPreview(val label: String, val version: String?, val icon: Drawable?, val packageName: String?) {

    companion object {
        private val ABIS = setOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86", "x86-64")

        fun load(context: Context, zip: ZipFile, bundle: SplitBundle, source: File, scratchDir: File): AppPreview {
            val fallback = AppPreview(bundle.label, null, null, bundle.packageName)
            val base = bundle.base ?: return fallback
            scratchDir.mkdirs()
            val apk = base.entry?.let { extract(zip, it, File(scratchDir, "base.apk")) } ?: source

            // Many split apps keep their icon bitmaps in the density split, so resources must see the splits too.
            val resourceSplits = bundle.parts.drop(1)
                .filter { it.entry != null && it.name !in ABIS }
                .mapIndexed { i, part -> extract(zip, part.entry!!, File(scratchDir, "split$i.apk")).path }

            val pm = context.packageManager
            val info = runCatching { pm.getPackageArchiveInfo(apk.path, 0) }.getOrNull() ?: return fallback
            val app = info.applicationInfo ?: return fallback
            app.sourceDir = apk.path
            app.publicSourceDir = apk.path
            if (resourceSplits.isNotEmpty()) {
                app.splitSourceDirs = resourceSplits.toTypedArray()
                app.splitPublicSourceDirs = resourceSplits.toTypedArray()
            }

            // An app whose name lives in a split APK resolves to its package name here, so prefer the bundle's.
            val label = pm.getApplicationLabel(app).toString()
                .takeIf { it.isNotBlank() && it != info.packageName }
                ?: bundle.label
            val icon = if (app.icon != 0) runCatching { app.loadIcon(pm) }.getOrNull() else null

            return AppPreview(label, info.versionName, icon, info.packageName)
        }

        private fun extract(zip: ZipFile, entry: ZipEntry, target: File): File {
            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            return target
        }
    }
}
