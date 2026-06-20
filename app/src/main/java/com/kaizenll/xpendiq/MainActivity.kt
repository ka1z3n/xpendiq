package com.kaizenll.xpendiq

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import com.kaizenll.xpendiq.ui.onboarding.OnboardingActivity
import com.kaizenll.xpendiq.util.AppLock
import com.kaizenll.xpendiq.util.Preferences
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val lockAuthenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    // Auto-show the prompt once per lock. After a cancel we keep it true so onResume doesn't loop;
    // a real background (onStop without an auth in progress) re-arms it.
    private var autoPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Preferences.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // While the lock is enabled, keep the screen out of screenshots and the recents preview so
        // the cover can't be bypassed by peeking at the thumbnail.
        if (Preferences.isAppLockEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        findViewById<MaterialButton>(R.id.lock_unlock_btn).setOnClickListener { promptUnlock() }

        val navHostView = findViewById<View>(R.id.nav_host)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Status-bar inset → top of the nav host. Gesture/3-button-nav inset → BottomNavigationView's
        // own bottom padding, so its background extends to the screen edge instead of leaving a
        // grey strip below.
        ViewCompat.setOnApplyWindowInsetsListener(navHostView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        // setupWithNavController keeps the selected tab highlight in sync with the destination.
        bottomNav.setupWithNavController(navController)

        // …but override tab taps to always land on the tab's root. The default behaviour saves and
        // restores each tab's back stack, which would re-open a half-finished "Add transaction"
        // form when you leave and return to the Transactions tab. We don't want leaf screens like
        // the editor preserved across tab switches.
        bottomNav.setOnItemSelectedListener { item ->
            val options = navOptions {
                launchSingleTop = true
                restoreState = false
                popUpTo(navController.graph.startDestinationId) {
                    saveState = false
                    inclusive = false
                }
            }
            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        // Cover immediately on first creation so content never flashes before the prompt.
        if (shouldLock()) findViewById<View>(R.id.lock_overlay).visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (shouldLock()) {
            findViewById<View>(R.id.lock_overlay).visibility = View.VISIBLE
            if (!AppLock.authInProgress && !autoPrompted) {
                autoPrompted = true
                promptUnlock()
            }
        } else {
            findViewById<View>(R.id.lock_overlay).visibility = View.GONE
            autoPrompted = false
        }
    }

    override fun onStop() {
        super.onStop()
        // A genuine background (not the auth system activity) re-arms the auto-prompt.
        if (!AppLock.authInProgress) autoPrompted = false
    }

    /** Locked when the feature is on, the session isn't unlocked yet, and onboarding is done. */
    private fun shouldLock(): Boolean =
        Preferences.isAppLockEnabled(this) && !AppLock.isUnlocked

    private fun promptUnlock() {
        // No usable credential (lock removed after enabling) — fail open rather than trap the user.
        val canAuth = BiometricManager.from(this).canAuthenticate(lockAuthenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            unlock()
            return
        }

        AppLock.authInProgress = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.authInProgress = false
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    AppLock.authInProgress = false
                    // Stay covered; the overlay's Unlock button lets the user retry.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(lockAuthenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun unlock() {
        AppLock.isUnlocked = true
        findViewById<View>(R.id.lock_overlay).visibility = View.GONE
    }
}
