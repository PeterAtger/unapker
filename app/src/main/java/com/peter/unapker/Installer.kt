package com.peter.unapker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

object Installer {

    fun install(
        context: Context,
        source: File,
        zip: ZipFile,
        bundle: SplitBundle,
        onSessionCreated: (sessionId: Int) -> Unit,
        onProgress: (partIndex: Int, written: Long) -> Unit,
        beforeCommit: () -> Unit,
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        bundle.packageName?.let(params::setAppPackageName)
        params.setSize(bundle.totalSize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        onSessionCreated(sessionId)
        val session = packageInstaller.openSession(sessionId)
        try {
            bundle.parts.forEachIndexed { index, part ->
                val input = part.entry?.let(zip::getInputStream) ?: source.inputStream()
                write(session, "$index-${part.name}.apk", part.size, input) { onProgress(index, it) }
            }

            beforeCommit()
            val callback = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun write(
        session: PackageInstaller.Session,
        name: String,
        size: Long,
        input: InputStream,
        onWritten: (Long) -> Unit,
    ) {
        val buffer = ByteArray(1 shl 16)
        var written = 0L
        input.use { source ->
            session.openWrite(name, 0, size).use { out ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    onWritten(written)
                }
                session.fsync(out)
            }
        }
    }
}
