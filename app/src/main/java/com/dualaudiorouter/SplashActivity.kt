package com.example.dualaudiorouter

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashDelay = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide action bar for fullscreen effect
        supportActionBar?.hide()

        // Set version info
        setVersionInfo()

        // Start main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startMainActivity()
        }, splashDelay)
    }

    /**
     * Set version information from app manifest
     */
    private fun setVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val tvVersion = findViewById<TextView>(R.id.tvVersion)
            tvVersion.text = "Version $versionName (Build $versionCode)"

        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback if package info can't be retrieved
            val tvVersion = findViewById<TextView>(R.id.tvVersion)
            tvVersion.text = "Version 1.0"
        }
    }

    /**
     * Start the main activity and finish splash
     */
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()

        // Optional: Add transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * Handle back button - prevent going back from splash
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - prevent going back from splash screen
    }
}
