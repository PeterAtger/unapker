package com.atger.unapker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val result = InstallResult(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
            confirmIntent = confirmIntentOf(intent),
        )

        val listener = InstallJobs.listeners[sessionId]
        if (listener != null) {
            listener(result)
            return
        }

        when (result.status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                result.confirmIntent?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> finishInBackground(context.applicationContext, sessionId)
            else -> InstallJobs.jobs.remove(sessionId)?.clear()
        }
    }

    private fun finishInBackground(context: Context, sessionId: Int) {
        val job = InstallJobs.jobs.remove(sessionId)
        if (job == null || job.bundle.obbs.isEmpty()) {
            job?.clear()
            Toast.makeText(context, R.string.installed, Toast.LENGTH_SHORT).show()
            return
        }

        val pending = goAsync()
        Thread {
            val outcome = runCatching { ObbCopier.copy(context, job.zip, job.bundle.obbs) }.getOrNull()
            job.clear()
            val message = when (outcome) {
                is ObbCopier.Outcome.InDownloads -> context.getString(R.string.installed_move_obb, outcome.folder)
                else -> context.getString(R.string.installed)
            }
            Handler(context.mainLooper).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            pending.finish()
        }.start()
    }

    private fun confirmIntentOf(intent: Intent): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        }
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }
}

class InstallResult(
    val status: Int,
    val message: String?,
    val packageName: String?,
    val confirmIntent: Intent?,
)
