package com.peter.unapker

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class InstallJob(val zip: ZipFile, val bundle: SplitBundle, val source: File, private val preview: File) {
    fun clear() {
        runCatching { zip.close() }
        source.delete()
        preview.delete()
    }
}

// Keyed by install session, so two bundles opened back to back never share files or results,
// and OBB copying still works if the user leaves Unapker mid-install.
object InstallJobs {
    val jobs = ConcurrentHashMap<Int, InstallJob>()
    val listeners = ConcurrentHashMap<Int, (InstallResult) -> Unit>()
}
