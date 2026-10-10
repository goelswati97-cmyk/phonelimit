package app.screenlimit

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device admin that only has permission to lock the screen. */
class LockAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        UsageService.start(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off means Screen Limit can no longer lock your phone when your daily time is up."

    companion object {
        fun component(context: Context) = ComponentName(context, LockAdminReceiver::class.java)
    }
}
