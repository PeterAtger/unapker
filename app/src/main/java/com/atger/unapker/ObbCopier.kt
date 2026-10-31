package com.atger.unapker

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ObbCopier {

    sealed class Outcome {
        object InPlace : Outcome()
        class InDownloads(val folder: String) : Outcome()
    }

    fun copy(context: Context, zip: ZipFile, obbs: List<ZipEntry>): Outcome {
        val placed = runCatching { obbs.forEach { copyInPlace(zip, it) } }
        if (placed.isSuccess) {
            return Outcome.InPlace
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw placed.exceptionOrNull()!!
        }

        obbs.forEach { copyToDownloads(context, zip, it) }
        val packageFolder = obbs.first().name.substringBeforeLast('/').substringAfterLast('/')
        return Outcome.InDownloads("${Environment.DIRECTORY_DOWNLOADS}/Unapker/$packageFolder")
    }

    @Suppress("DEPRECATION")
    private fun copyInPlace(zip: ZipFile, entry: ZipEntry) {
        val target = File(Environment.getExternalStorageDirectory(), entry.name)
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { input.copyTo(it, 1 shl 16) }
        }
    }

    private fun copyToDownloads(context: Context, zip: ZipFile, entry: ZipEntry) {
        val packageFolder = entry.name.substringBeforeLast('/').substringAfterLast('/')
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, entry.name.substringAfterLast('/'))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Unapker/$packageFolder")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create ${entry.name} in Downloads")
        zip.getInputStream(entry).use { input ->
            resolver.openOutputStream(uri)!!.use { input.copyTo(it, 1 shl 16) }
        }
    }
}
