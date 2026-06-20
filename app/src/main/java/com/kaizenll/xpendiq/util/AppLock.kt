package com.kaizenll.xpendiq.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks whether the app is unlocked for the current foreground session. The gate itself lives in
 * MainActivity; this just remembers the unlocked state and clears it whenever the app is sent to
 * the background, so returning to the app re-prompts.
 *
 * [authInProgress] guards the one case that would otherwise loop: the device-credential prompt is a
 * separate system activity, so showing it backgrounds our own activities. We must not treat that as
 * "the user left the app" and re-lock.
 */
object AppLock {

    @Volatile
    var isUnlocked = false

    @Volatile
    var authInProgress = false

    fun lock() {
        isUnlocked = false
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
                if (started <= 0 && !authInProgress) lock()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
