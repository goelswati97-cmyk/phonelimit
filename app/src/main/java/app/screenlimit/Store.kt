package app.screenlimit

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Keeps today's usage total and the daily limit.
 * Everything resets automatically when the date changes (at midnight).
 */
object Store {
    private const val PREFS = "screen_limit"
    private const val KEY_DAY = "day"
    private const val KEY_USED = "used_ms"
    private const val KEY_LIMIT = "limit_min"

    const val DEFAULT_LIMIT_MIN = 90      // 1.5 hours
    const val MIN_LIMIT_MIN = 15
    const val MAX_LIMIT_MIN = 12 * 60

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Clears yesterday's numbers the first time we touch the store on a new day. */
    private fun rollover(c: Context): SharedPreferences {
        val p = prefs(c)
        val today = LocalDate.now().toString()
        if (p.getString(KEY_DAY, null) != today) {
            val limit = p.getInt(KEY_LIMIT, DEFAULT_LIMIT_MIN)
            p.edit().clear()
                .putString(KEY_DAY, today)
                .putInt(KEY_LIMIT, limit)
                .apply()
        }
        return p
    }

    fun usedMs(c: Context): Long = rollover(c).getLong(KEY_USED, 0L)

    fun addUsed(c: Context, ms: Long): Long {
        val p = rollover(c)
        val total = p.getLong(KEY_USED, 0L) + ms
        p.edit().putLong(KEY_USED, total).apply()
        return total
    }

    fun limitMinutes(c: Context): Int = prefs(c).getInt(KEY_LIMIT, DEFAULT_LIMIT_MIN)

    fun limitMs(c: Context): Long = limitMinutes(c) * 60_000L

    fun setLimitMinutes(c: Context, minutes: Int) {
        prefs(c).edit()
            .putInt(KEY_LIMIT, minutes.coerceIn(MIN_LIMIT_MIN, MAX_LIMIT_MIN))
            .apply()
    }

    /** One-per-day flags, e.g. "already warned at 10 minutes left". */
    fun flag(c: Context, name: String): Boolean = rollover(c).getBoolean("flag_$name", false)

    fun setFlag(c: Context, name: String) {
        rollover(c).edit().putBoolean("flag_$name", true).apply()
    }

    fun format(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
