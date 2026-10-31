package com.atger.unapker

import android.content.Context
import android.graphics.drawable.Drawable
import java.io.File
import java.util.zip.ZipFile

class AppPreview(val label: String, val version: String?, val icon: Drawable?, val packageName: String?) {

    companion object {
        fun load(context: Context, zip: ZipFile, bundle: SplitBundle, source: File, scratch: File): AppPreview {
            val fallback = AppPreview(bundle.label, null, null, bundle.packageName)
            val base = bundle.base ?: return fallback
            val apk = base.entry?.let { entry ->
                zip.getInputStream(entry).use { input -> scratch.outputStream().use { input.copyTo(it) } }
                scratch
            } ?: source

            val pm = context.packageManager
            val info = runCatching { pm.getPackageArchiveInfo(apk.path, 0) }.getOrNull() ?: return fallback
            val app = info.applicationInfo ?: return fallback
            app.sourceDir = apk.path
            app.publicSourceDir = apk.path

            // An app whose name lives in a split APK resolves to its package name here, so prefer the bundle's.
            val label = pm.getApplicationLabel(app).toString()
                .takeIf { it.isNotBlank() && it != info.packageName }
                ?: bundle.label
            val icon = if (app.icon != 0) runCatching { app.loadIcon(pm) }.getOrNull() else null

            return AppPreview(label, info.versionName, icon, info.packageName)
        }
    }
}
