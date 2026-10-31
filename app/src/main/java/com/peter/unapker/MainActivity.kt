package com.peter.unapker

import android.Manifest
import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipException
import java.util.zip.ZipFile

class MainActivity : Activity() {

    private class TileSpec(val label: String, val size: Long, val color: Int, val firstPart: Int)

    private lateinit var screens: List<View>
    private lateinit var homeScreen: View
    private lateinit var workScreen: View
    private lateinit var finishScreen: View
    private lateinit var doneScreen: View
    private lateinit var failScreen: View

    private lateinit var workIcon: FrameLayout
    private lateinit var workName: TextView
    private lateinit var workVersion: TextView
    private lateinit var workStage: TextView
    private lateinit var workCount: TextView
    private lateinit var workPercent: TextView
    private lateinit var workBar: BarView
    private lateinit var workStack: LinearLayout
    private lateinit var workSkipped: TextView
    private lateinit var workAction: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val loops = mutableListOf<Animator>()

    private var job: InstallJob? = null
    private var sessionId = NO_SESSION
    private var waitingForInstallPermission = false
    private var waitingForConfirm = false
    private var preview: AppPreview? = null
    private var tilePlan = emptyList<TileSpec>()
    private var tilesShown = 0
    private var lastProgressPost = 0L

    private val partColors by lazy {
        listOf(R.color.sun, R.color.coral, R.color.mint, R.color.lilac).map(::getColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).background = MatDrawable(this)

        homeScreen = findViewById(R.id.screen_home)
        workScreen = findViewById(R.id.screen_installing)
        finishScreen = findViewById(R.id.screen_finishing)
        doneScreen = findViewById(R.id.screen_installed)
        failScreen = findViewById(R.id.screen_failed)
        screens = listOf(homeScreen, workScreen, finishScreen, doneScreen, failScreen)

        workIcon = findViewById(R.id.work_icon)
        workName = findViewById(R.id.work_name)
        workVersion = findViewById(R.id.work_version)
        workStage = findViewById(R.id.work_stage)
        workCount = findViewById(R.id.work_count)
        workPercent = findViewById(R.id.work_percent)
        workBar = findViewById(R.id.work_bar)
        workStack = findViewById(R.id.work_stack)
        workSkipped = findViewById(R.id.work_skipped)
        workAction = findViewById(R.id.work_action)

        findViewById<Button>(R.id.choose).setOnClickListener { pickFile() }
        workAction.setOnClickListener { openInstallSettings() }
        findViewById<Button>(R.id.done_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.fail_close).setOnClickListener { finish() }
        fitHero()

        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission && packageManager.canRequestPackageInstalls()) {
            waitingForInstallPermission = false
            startInstall()
        }
        if (waitingForConfirm) {
            waitingForConfirm = false
            showFinishing()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoops()
        InstallJobs.listeners.remove(sessionId)
        worker.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == PICK_FILE && resultCode == RESULT_OK && uri != null) {
            open(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION) {
            copyObbs()
        }
    }

    private fun handle(intent: Intent) {
        val uri = intent.data ?: streamOf(intent)
        if (uri == null) {
            showHome()
            return
        }
        open(uri)
    }

    private fun streamOf(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        }
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun pickFile() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(picker, PICK_FILE)
    }

    private fun open(uri: Uri) {
        cleanUp()
        preview = null
        val fileName = displayNameOf(uri)
        val stamp = System.nanoTime()
        val source = File(cacheDir, "source-$stamp.apk")
        val previewApk = File(cacheDir, "preview-$stamp.apk")
        showOpening(fileName)
        worker.execute {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    source.outputStream().use { input.copyTo(it, 1 shl 16) }
                } ?: throw Failure(R.string.not_package_title, R.string.not_package_body)

                val zip = openAsZip(source, fileName)
                val bundle = SplitBundle.read(zip, source, fileName.substringBeforeLast('.'))
                if (bundle.parts.isEmpty()) {
                    zip.close()
                    throw Failure(R.string.not_package_title, R.string.not_package_body)
                }
                val loaded = AppPreview.load(this, zip, bundle, source, previewApk)
                val opened = InstallJob(zip, bundle, source, previewApk)
                runOnUiThread {
                    job = opened
                    preview = loaded
                    showWorking(bundle)
                    startInstall()
                }
            } catch (e: Exception) {
                source.delete()
                previewApk.delete()
                runOnUiThread { showFailure(e) }
            }
        }
    }

    private fun openAsZip(source: File, fileName: String): ZipFile {
        try {
            return ZipFile(source)
        } catch (e: ZipException) {
            if (fileName.endsWith(".apkm", ignoreCase = true)) {
                throw Failure(R.string.locked_title, R.string.locked_body, locked = true)
            }
            throw Failure(R.string.not_package_title, R.string.not_package_body)
        }
    }

    private fun startInstall() {
        val current = job ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            waitingForInstallPermission = true
            workStage.setText(R.string.waiting_for_you)
            workAction.visibility = View.VISIBLE
            openInstallSettings()
            return
        }

        workAction.visibility = View.GONE
        workStage.setText(R.string.preparing)
        worker.execute {
            try {
                Installer.install(this, current.source, current.zip, current.bundle, { id ->
                    sessionId = id
                    InstallJobs.jobs[id] = current
                    InstallJobs.listeners[id] = ::onInstallResult
                }, { index, written ->
                    onPartProgress(current.bundle, index, written)
                }) {
                    // Let the last tile land and settle on 100% before Android's dialog covers it.
                    Thread.sleep(COMMIT_PAUSE_MS)
                }
                runOnUiThread { workStage.setText(R.string.waiting_for_you) }
            } catch (e: Exception) {
                runOnUiThread { showFailure(e) }
            }
        }
    }

    private fun openInstallSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    private fun onPartProgress(bundle: SplitBundle, index: Int, written: Long) {
        val part = bundle.parts[index]
        val finished = written >= part.size
        val now = SystemClock.uptimeMillis()
        if (!finished && now - lastProgressPost < 40) {
            return
        }
        lastProgressPost = now
        val before = bundle.parts.take(index).sumOf { it.size.coerceAtLeast(0) }
        val total = bundle.totalSize.coerceAtLeast(1)
        val fraction = ((before + written).toFloat() / total).coerceIn(0f, 1f)
        runOnUiThread { renderProgress(index, fraction) }
    }

    private fun renderProgress(partIndex: Int, fraction: Float) {
        workPercent.text = getString(R.string.percent, (fraction * 100).toInt())
        workBar.progress = fraction
        while (tilesShown < tilePlan.size && tilePlan[tilesShown].firstPart <= partIndex) {
            dropTile(tilePlan[tilesShown], tilesShown)
            tilesShown++
        }
    }

    private fun onInstallResult(result: InstallResult) {
        when (result.status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> result.confirmIntent?.let {
                waitingForConfirm = true
                startActivity(it)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                if (job?.bundle?.obbs.isNullOrEmpty()) {
                    showInstalled(result.packageName, null)
                } else {
                    copyObbsWhenAllowed()
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> showFailure(Failure(R.string.cancelled_title, R.string.cancelled_body))
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> showFailure(Failure(R.string.failed_title, R.string.failed_incompatible))
            PackageInstaller.STATUS_FAILURE_STORAGE -> showFailure(Failure(R.string.failed_title, R.string.failed_storage))
            PackageInstaller.STATUS_FAILURE_CONFLICT -> showFailure(Failure(R.string.failed_title, R.string.failed_conflict))
            else -> showFailure(Failure(R.string.failed_title, R.string.failed_generic))
        }
    }

    private fun copyObbsWhenAllowed() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION)
            return
        }
        copyObbs()
    }

    private fun copyObbs() {
        val current = job ?: return
        worker.execute {
            val outcome = runCatching { ObbCopier.copy(this, current.zip, current.bundle.obbs) }.getOrNull()
            runOnUiThread { showInstalled(null, outcome) }
        }
    }

    private fun showScreen(screen: View) {
        stopLoops()
        screens.forEach { it.visibility = if (it == screen) View.VISIBLE else View.GONE }
        Motion.enter(screen)
    }

    private fun stopLoops() {
        loops.forEach { it.cancel() }
        loops.clear()
    }

    private fun fitHero() {
        val wrap = findViewById<FrameLayout>(R.id.hero_wrap)
        val hero = findViewById<View>(R.id.hero)
        wrap.post {
            val scale = minOf(1f, wrap.width.toFloat() / hero.width)
            hero.pivotX = 0f
            hero.pivotY = 0f
            hero.scaleX = scale
            hero.scaleY = scale
            hero.translationX = (wrap.width - hero.width * scale) / 2
            wrap.layoutParams = wrap.layoutParams.apply { height = (hero.height * scale).toInt() }
        }
    }

    private fun showHome() {
        showScreen(homeScreen)
        val tiles = listOf(
            Triple(R.id.tile_xapk, R.color.sun, -9f),
            Triple(R.id.tile_apks, R.color.coral, 8f),
            Triple(R.id.tile_apkm, R.color.mint, 5f),
            Triple(R.id.tile_apk, R.color.lilac, -10f),
        )
        val phases = listOf(0L, 1200L, 2100L, 600L)
        tiles.forEachIndexed { i, (id, color, rotation) ->
            val tile = findViewById<View>(id)
            tile.background = tileBackground(getColor(color), 22f, 12)
            loops += Motion.bob(tile, rotation, phases[i] % 1900)
        }
    }

    private fun showOpening(fileName: String) {
        showScreen(workScreen)
        bindIcon(workIcon, null, fileName, 16f)
        workName.text = fileName
        workVersion.visibility = View.GONE
        workStage.setText(R.string.opening)
        workCount.text = ""
        workPercent.text = getString(R.string.percent, 0)
        workBar.progress = 0f
        workStack.removeAllViews()
        workSkipped.visibility = View.GONE
        workAction.visibility = View.GONE
    }

    private fun showWorking(bundle: SplitBundle) {
        val current = preview
        val label = current?.label ?: bundle.label
        bindIcon(workIcon, current?.icon, label, 16f)
        workName.text = label
        workVersion.visibility = if (current?.version == null) View.GONE else View.VISIBLE
        workVersion.text = current?.version?.let { getString(R.string.version, it) }
        workCount.text = resources.getQuantityString(R.plurals.parts, bundle.parts.size, bundle.parts.size)
        workSkipped.visibility = if (bundle.skipped.isEmpty()) View.GONE else View.VISIBLE
        workSkipped.text = resources.getQuantityString(R.plurals.skipped, bundle.skipped.size, bundle.skipped.size)

        tilePlan = planTiles(bundle.parts)
        tilesShown = 0
        workStack.removeAllViews()
    }

    private fun planTiles(parts: List<Part>): List<TileSpec> {
        val singles = if (parts.size > MAX_TILES) parts.take(MAX_TILES - 1) else parts
        val specs = singles.mapIndexed { i, part ->
            TileSpec(friendlyName(part.name), part.size, partColors[i % partColors.size], i)
        }
        if (parts.size <= MAX_TILES) {
            return specs
        }
        val rest = parts.drop(MAX_TILES - 1)
        val more = TileSpec(
            resources.getQuantityString(R.plurals.more_parts, rest.size, rest.size),
            rest.sumOf { it.size.coerceAtLeast(0) },
            partColors[(MAX_TILES - 1) % partColors.size],
            MAX_TILES - 1,
        )
        return specs + more
    }

    private fun friendlyName(name: String): String {
        if (name == "base") return getString(R.string.part_main)
        if (name in ABIS) return getString(R.string.part_processor)
        if (name.matches(DENSITY)) return getString(R.string.part_screen)
        val locale = Locale.forLanguageTag(name.replace("-r", "-"))
        val language = locale.getDisplayName(Locale.getDefault())
        if (name.length <= 6 && language.isNotBlank() && !language.equals(name, ignoreCase = true)) {
            return language.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun dropTile(spec: TileSpec, index: Int) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = tileBackground(spec.color, 20f, 8)
            setPadding(dp(22), 0, dp(22), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)).apply {
                topMargin = dp(10)
            }
        }
        tile.addView(TextView(this).apply {
            text = spec.label
            setTextAppearance(R.style.Text)
            setTextColor(getColor(R.color.ink))
            fontVariationSettings = "'wght' 700"
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tile.addView(TextView(this).apply {
            text = Formatter.formatShortFileSize(this@MainActivity, spec.size)
            setTextAppearance(R.style.Text)
            setTextColor(getColor(R.color.ink))
            textSize = 15f
            fontVariationSettings = "'wght' 600"
            alpha = 0.75f
        })
        workStack.addView(tile, 0)
        tile.alpha = 0f
        tile.post { Motion.drop(tile, TILE_TILTS[index % TILE_TILTS.size]) }
    }

    private fun showFinishing() {
        showScreen(finishScreen)
        val current = preview
        val label = current?.label ?: job?.bundle?.label.orEmpty()
        val icon = findViewById<FrameLayout>(R.id.finish_icon)
        bindIcon(icon, current?.icon, label, 36f)
        findViewById<TextView>(R.id.finish_body).text = getString(R.string.putting_together, label)

        loops += Motion.halo(findViewById(R.id.finish_halo))
        loops += Motion.spin(findViewById(R.id.finish_ring))
        val gathers = listOf(
            floatArrayOf(-150f, -190f, -20f),
            floatArrayOf(150f, -130f, 18f),
            floatArrayOf(-140f, 190f, 12f),
            floatArrayOf(140f, 210f, -16f),
        )
        listOf(R.id.gather_1, R.id.gather_2, R.id.gather_3, R.id.gather_4).forEachIndexed { i, id ->
            val piece = findViewById<View>(id)
            piece.background = GradientDrawable().apply {
                setColor(partColors[i])
                cornerRadius = dp(16f)
            }
            val (x, y, r) = gathers[i]
            Motion.gather(piece, dp(x), dp(y), r, i * 80L)
        }
        Motion.pop(icon, 900)
        listOf(R.id.dot_1, R.id.dot_2, R.id.dot_3).forEachIndexed { i, id ->
            loops += Motion.hop(findViewById(id), i * 150L)
        }
    }

    private fun showInstalled(installedPackage: String?, obb: ObbCopier.Outcome?) {
        waitingForConfirm = false
        val current = preview
        val label = current?.label ?: job?.bundle?.label.orEmpty()
        val packageToLaunch = installedPackage ?: current?.packageName ?: job?.bundle?.packageName
        cleanUp()
        showScreen(doneScreen)

        val icon = findViewById<FrameLayout>(R.id.done_icon)
        bindIcon(icon, current?.icon, label, 40f)
        findViewById<TextView>(R.id.done_body).text = when (obb) {
            is ObbCopier.Outcome.InDownloads -> getString(R.string.installed_move_obb, obb.folder)
            else -> getString(R.string.installed_ready, label)
        }

        val stage = findViewById<FrameLayout>(R.id.done_stage)
        (0 until stage.childCount).map(stage::getChildAt).filter { it.tag == CONFETTI }.forEach(stage::removeView)
        CONFETTI_PIECES.forEachIndexed { i, piece ->
            val view = View(this).apply {
                tag = CONFETTI
                background = GradientDrawable().apply {
                    setColor(partColors[i % partColors.size])
                    cornerRadius = dp(7f)
                }
                layoutParams = FrameLayout.LayoutParams(dp(piece[0].toInt()), dp(piece[1].toInt()), Gravity.CENTER)
            }
            stage.addView(view, 0)
            loops += Motion.burst(view, dp(piece[2]), dp(piece[3]), piece[4])
        }
        Motion.pop(findViewById(R.id.done_pop), 0)
        Motion.pop(findViewById(R.id.done_badge), 350)
        loops += Motion.float(icon, 8f, 2000, 650)
        Motion.rise(findViewById(R.id.done_title), 300)
        Motion.rise(findViewById(R.id.done_body), 400)
        Motion.rise(findViewById(R.id.done_buttons), 500)

        val openButton = findViewById<Button>(R.id.done_open)
        val launch = packageToLaunch?.let(packageManager::getLaunchIntentForPackage)
        openButton.visibility = if (launch == null) View.GONE else View.VISIBLE
        openButton.text = getString(R.string.open_app, label)
        openButton.setOnClickListener {
            launch?.let(::startActivity)
            finish()
        }
    }

    private fun showFailure(error: Exception) {
        waitingForConfirm = false
        cleanUp()
        val failure = error as? Failure ?: Failure(R.string.failed_title, R.string.failed_generic)
        showScreen(failScreen)
        val tile = findViewById<View>(R.id.fail_tile)
        tile.background = tileBackground(getColor(R.color.coral), 40f, 12)
        findViewById<ImageView>(R.id.fail_glyph).setImageResource(if (failure.locked) R.drawable.ic_lock else R.drawable.ic_alert)
        findViewById<TextView>(R.id.fail_title).setText(failure.title)
        findViewById<TextView>(R.id.fail_body).setText(failure.body)
        loops += Motion.shake(tile)
    }

    private fun bindIcon(frame: FrameLayout, icon: Drawable?, name: String, radiusDp: Float) {
        frame.removeAllViews()
        if (icon != null) {
            frame.background = null
            frame.addView(ImageView(this).apply {
                setImageDrawable(icon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            return
        }
        frame.background = GradientDrawable().apply {
            setColor(getColor(android.R.color.white))
            cornerRadius = dp(radiusDp)
        }
        frame.addView(TextView(this).apply {
            setTextAppearance(R.style.Text_Display)
            setTextColor(getColor(R.color.ink))
            gravity = Gravity.CENTER
            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
            setAutoSizeTextTypeUniformWithConfiguration(12, 72, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun cleanUp() {
        job?.clear()
        job = null
        InstallJobs.jobs.remove(sessionId)
        InstallJobs.listeners.remove(sessionId)
        sessionId = NO_SESSION
    }

    private fun displayNameOf(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment ?: "Package"
    }

    private class Failure(val title: Int, val body: Int, val locked: Boolean = false) : Exception()

    companion object {
        private const val PICK_FILE = 1
        private const val STORAGE_PERMISSION = 2
        private const val NO_SESSION = -1
        private const val MAX_TILES = 4
        private const val COMMIT_PAUSE_MS = 1200L
        private const val CONFETTI = "confetti"
        private val TILE_TILTS = floatArrayOf(-1.5f, 1.5f, -1f, 2f)
        private val ABIS = setOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86", "x86-64", "x86_64")
        private val DENSITY = Regex("(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi)")

        // width dp, height dp, x dp, y dp, rotation
        private val CONFETTI_PIECES = listOf(
            floatArrayOf(44f, 20f, -140f, -130f, -30f),
            floatArrayOf(36f, 18f, 130f, -150f, 40f),
            floatArrayOf(40f, 18f, 160f, 10f, -60f),
            floatArrayOf(34f, 18f, -165f, 30f, 70f),
            floatArrayOf(30f, 16f, -95f, 150f, -20f),
            floatArrayOf(42f, 18f, 105f, 140f, 25f),
            floatArrayOf(30f, 16f, 10f, -178f, 90f),
            floatArrayOf(28f, 14f, -40f, 182f, -80f),
        )
    }
}
