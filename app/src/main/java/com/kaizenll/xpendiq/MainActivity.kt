package com.kaizenll.xpendiq

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.kaizenll.xpendiq.ui.onboarding.OnboardingActivity
import com.kaizenll.xpendiq.util.Preferences
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Preferences.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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
        bottomNav.setupWithNavController(navHost.navController)
    }
}
