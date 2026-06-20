package com.kaizenll.xpendiq.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/**
 * Tracks whether the app is unlocked for the current foreground session. The gate itself lives in
 * MainActivity; this remembers the unlocked state and, on returning from the background, relocks
 * only if the app was away longer than [GRACE_MS] — so quick app-switches don't re-prompt.
 *
 * [authInProgress] guards the one case that would otherwise loop: the device-credential prompt is a
 * separate system activity, so showing it backgrounds our own activities. We must not treat that as
 * "the user left the app".
 */
object AppLock {

    // Brief background trips (copying an OTP, checking a notification) shouldn't force re-auth.
    private const val GRACE_MS = 30_000L

    @Volatile
    var isUnlocked = false

    @Volatile
    var authInProgress = false

    // elapsedRealtime when the app last went to the background; 0L while foreground.
    @Volatile
    private var backgroundedAt = 0L

    fun lock() {
        isUnlocked = false
    }

    /**
     * Call when MainActivity resumes. Relocks only if the app sat in the background past the grace
     * window; a short trip away leaves it unlocked.
     */
    fun onForeground() {
        val bg = backgroundedAt
        backgroundedAt = 0L
        if (bg != 0L && SystemClock.elapsedRealtime() - bg > GRACE_MS) {
            isUnlocked = false
        }
    }

    /** Counts started activities to detect a true background transition (last activity stopped). */
    fun registerBackgroundReset(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                // Start the grace timer instead of locking outright; onForeground decides later.
                if (started <= 0 && !authInProgress) backgroundedAt = SystemClock.elapsedRealtime()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
