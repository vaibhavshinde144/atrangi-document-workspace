package com.atrangi.documentworkspace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * One-tap workspace updater.
 *
 * Atrangi's feature/UI layer is hosted on GitHub Pages, so normal releases can be
 * applied without reinstalling the APK. Tapping Update restarts MainActivity with
 * a cache-busted URL. Local WebView storage remains intact.
 */
class UpdateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = intent.getStringExtra(UpdateManager.EXTRA_WEB_VERSION)
            ?: BuildConfig.VERSION_NAME

        val restart = Intent(this, MainActivity::class.java).apply {
            putExtra(UpdateManager.EXTRA_APPLY_WEB_UPDATE, true)
            putExtra(UpdateManager.EXTRA_WEB_VERSION, version)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(restart)
        finish()
    }
}
