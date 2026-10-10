package app.screenlimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts tracking again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> UsageService.start(context)
        }
    }
}
