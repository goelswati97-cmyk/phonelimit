package app.screenlimit

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usedText: TextView
    private lateinit var leftText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var limitText: TextView
    private lateinit var adminRow: Row
    private lateinit var notifRow: Row
    private lateinit var batteryRow: Row

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        UsageService.start(this)
    }

    override fun onResume() {
        super.onResume()
        UsageService.start(this)
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    // ---- screen -----------------------------------------------------------

    private fun buildUi(): View {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(label("Screen Limit", 26f, bold = true))
        root.addView(label("Locks your phone once you've used it for your daily limit. Resets at midnight.", 14f, muted = true).also {
            it.setPadding(0, dp(4), 0, dp(24))
        })

        usedText = label("", 34f, bold = true)
        root.addView(usedText)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            }
        }
        root.addView(progress)
        leftText = label("", 15f, muted = true)
        root.addView(leftText)

        // daily limit control
        root.addView(sectionTitle("Daily limit"))
        val limitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        limitRow.addView(Button(this).apply { text = "− 15 min"; setOnClickListener { changeLimit(-15) } })
        limitText = label("", 20f, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        limitRow.addView(limitText)
        limitRow.addView(Button(this).apply { text = "+ 15 min"; setOnClickListener { changeLimit(15) } })
        root.addView(limitRow)

        // setup checklist
        root.addView(sectionTitle("Setup"))
        adminRow = row(root, "Permission to lock the phone", "Required") { requestAdmin() }
        notifRow = row(root, "Warnings before locking", "Recommended") { requestNotifications() }
        batteryRow = row(root, "Keep running in the background", "Recommended") { requestBatteryExemption() }

        root.addView(label(
            "Emergency calls still work from the lock screen, and the phone won't lock in the middle of a call.",
            13f, muted = true
        ).also { it.setPadding(0, dp(24), 0, 0) })

        return ScrollView(this).apply { addView(root) }
    }

    private fun refresh() {
        val used = Store.usedMs(this)
        val limit = Store.limitMs(this)
        usedText.text = "${Store.format(used)} used today"
        progress.max = (limit / 1000L).toInt()
        progress.progress = (used / 1000L).toInt().coerceAtMost(progress.max)
        leftText.text = if (used >= limit) "Limit reached — the phone stays locked until midnight."
                        else "${Store.format(limit - used)} left"
        limitText.text = Store.format(limit)

        adminRow.set(isAdminActive())
        notifRow.set(Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        batteryRow.set(getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
    }

    private fun changeLimit(deltaMin: Int) {
        val newMin = (Store.limitMinutes(this) + deltaMin).coerceIn(Store.MIN_LIMIT_MIN, Store.MAX_LIMIT_MIN)
        if (newMin * 60_000L <= Store.usedMs(this)) {
            Toast.makeText(this, "That's less than you've already used today.", Toast.LENGTH_SHORT).show()
            return
        }
        Store.setLimitMinutes(this, newMin)
        refresh()
    }

    // ---- permissions ------------------------------------------------------

    private fun isAdminActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(LockAdminReceiver.component(this))

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, LockAdminReceiver.component(this@MainActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Screen Limit only uses this to lock the screen when your daily time is used up."
            )
        }
        startActivity(intent)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---- small view helpers -----------------------------------------------

    private inner class Row(val status: TextView, val button: Button, val needed: String) {
        fun set(done: Boolean) {
            status.text = if (done) "✓ On" else needed
            status.setTextColor(if (done) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
            button.visibility = if (done) View.GONE else View.VISIBLE
        }
    }

    private fun row(parent: LinearLayout, title: String, needed: String, onClick: () -> Unit): Row {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(label(title, 16f))
        val status = label("", 13f)
        texts.addView(status)
        line.addView(texts)
        val button = Button(this).apply { text = "Turn on"; setOnClickListener { onClick() } }
        line.addView(button)
        parent.addView(line)
        return Row(status, button, needed)
    }

    private fun sectionTitle(s: String): TextView = label(s, 13f, bold = true, muted = true).also {
        it.isAllCaps = true
        it.setPadding(0, dp(28), 0, dp(8))
    }

    private fun label(s: String, size: Float, bold: Boolean = false, muted: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (muted) alpha = 0.7f
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
