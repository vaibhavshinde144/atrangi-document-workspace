package com.atrangi.documentworkspace

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Applies web-only updates immediately, or opens the stable APK download URL when
 * a native Android update is required (for example, launcher icon/native bridge changes).
 */
class UpdateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = intent.getStringExtra(UpdateManager.EXTRA_WEB_VERSION)
            ?: BuildConfig.VERSION_NAME
        val nativeUpdate = intent.getBooleanExtra(UpdateManager.EXTRA_NATIVE_UPDATE, false)
        val apkUrl = intent.getStringExtra(UpdateManager.EXTRA_APK_URL)
            ?.takeIf { it.startsWith("https://") }
            ?: UpdateManager.DEFAULT_APK_URL

        if (nativeUpdate) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
            finish()
            return
        }

        val restart = Intent(this, MainActivity::class.java).apply {
            putExtra(UpdateManager.EXTRA_APPLY_WEB_UPDATE, true)
            putExtra(UpdateManager.EXTRA_WEB_VERSION, version)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(restart)
        finish()
    }
}
