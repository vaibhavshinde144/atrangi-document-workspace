package com.atrangi.documentworkspace

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var rootView: FrameLayout
    private lateinit var splashView: View
    private var contentIsVisible = false
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingCameraIntent: Intent? = null
    private var pendingCameraUri: Uri? = null
    private var pendingAppliedWebVersion: String? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        val selected = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        val uris = selected ?: if (result.resultCode == RESULT_OK) {
            pendingCameraUri?.let { arrayOf(it) }
        } else {
            null
        }
        callback.onReceiveValue(uris)
        fileChooserCallback = null
        pendingCameraIntent = null
        pendingCameraUri = null
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingPermissionRequest
        if (request != null) {
            if (granted) request.grant(request.resources) else request.deny()
            pendingPermissionRequest = null
            return@registerForActivityResult
        }
        val captureIntent = pendingCameraIntent
        pendingCameraIntent = null
        if (granted && captureIntent != null) {
            filePicker.launch(captureIntent)
        } else {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            pendingCameraUri = null
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView = FrameLayout(this)
        webView = WebView(this).apply {
            alpha = 0f
            visibility = View.INVISIBLE
            setBackgroundColor(Color.rgb(247, 249, 250))
        }
        splashView = createLaunchView()
        rootView.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        rootView.addView(
            splashView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(rootView)
        configureWebView()

        UpdateManager.createNotificationChannel(this)
        UpdateManager.schedule(this)
        UpdateManager.checkNow(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) {
            applyLaunchIntent(intent)
        } else {
            webView.restoreState(savedInstanceState)
        }
        webView.postDelayed({ revealContent() }, CONTENT_READY_FALLBACK_MS * 2)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
        }

        webView.addJavascriptInterface(AtrangiNativeBridge(), "AtrangiNative")
        webView.setInitialScale(100)
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.host == APP_HOST) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                pendingAppliedWebVersion?.let { version ->
                    UpdateManager.markWebVersionApplied(this@MainActivity, version)
                    pendingAppliedWebVersion = null
                }
                view.postDelayed({ revealContent() }, CONTENT_READY_FALLBACK_MS)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val acceptsImages = fileChooserParams?.acceptTypes
                    ?.filter { it.isNotBlank() }
                    ?.all { it.startsWith("image/") || it == "image/*" } != false

                if (fileChooserParams?.isCaptureEnabled == true && acceptsImages) {
                    val captureIntent = createCameraCaptureIntent()
                    if (captureIntent == null) {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = null
                        Toast.makeText(this@MainActivity, "No camera app is available.", Toast.LENGTH_LONG).show()
                        return true
                    }
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        filePicker.launch(captureIntent)
                    } else {
                        pendingCameraIntent = captureIntent
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                    return true
                }

                val pickerIntent = try {
                    fileChooserParams?.createIntent() ?: createDocumentPicker()
                } catch (_: Exception) {
                    createDocumentPicker()
                }
                filePicker.launch(pickerIntent)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (!needsCamera || ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                    )
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(this, "Unable to download: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun createDocumentPicker(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    private fun createCameraCaptureIntent(): Intent? {
        val captureFile = File.createTempFile("atrangi-passport-", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", captureFile)
        pendingCameraUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("Atrangi camera photo", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.takeIf { it.resolveActivity(packageManager) != null }
    }

    private inner class AtrangiNativeBridge {
        @JavascriptInterface
        fun shareApp() {
            runOnUiThread { shareInstallLink() }
        }

        @JavascriptInterface
        fun shareText(title: String, text: String, url: String) {
            val payload = listOf(text, url).filter { it.isNotBlank() }.joinToString("\n\n")
            runOnUiThread { shareTextToApps(title, payload) }
        }

        @JavascriptInterface
        fun copyInstallUrl() {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Atrangi install link", INSTALL_URL))
                Toast.makeText(this@MainActivity, "Install link copied", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun shareFile(fileName: String, mimeType: String, base64Payload: String) {
            if (base64Payload.length > MAX_SHARE_BASE64_CHARS) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "This file is too large to share directly.", Toast.LENGTH_LONG).show()
                }
                return
            }
            try {
                val encoded = base64Payload.substringAfter(',', base64Payload)
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Atrangi-File" }
                val shareDir = File(cacheDir, "shared").apply { mkdirs() }
                val output = File(shareDir, safeName)
                output.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    output
                )
                runOnUiThread {
                    shareFileToApps(uri, mimeType.ifBlank { "application/octet-stream" }, safeName)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Unable to prepare file: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun contentReady() {
            runOnUiThread { revealContent() }
        }

        @JavascriptInterface
        fun installUrl(): String = INSTALL_URL
    }

    private fun shareInstallLink() {
        val shareText = "Install Atrangi Document Workspace — scanner, OCR, PDF tools, passport/ID photo studio and secure document workspace.\n\n$INSTALL_URL"
        shareTextToApps("Atrangi Document Workspace", shareText)
    }

    private fun shareTextToApps(title: String, shareText: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share with an app"))
        } catch (error: Exception) {
            Toast.makeText(this, "Unable to open share options: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFileToApps(uri: Uri, mimeType: String, fileName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share $fileName"))
        } catch (error: Exception) {
            Toast.makeText(this, "Unable to share file: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createLaunchView(): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(247, 249, 250))
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.atrangi_riders_logo)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Atrangi Riders"
                },
                LinearLayout.LayoutParams((156 * density).toInt(), (156 * density).toInt())
            )
            addView(
                ProgressBar(this@MainActivity).apply { isIndeterminate = true },
                LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt()).apply {
                    topMargin = (24 * density).toInt()
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
        }
    }

    private fun revealContent() {
        if (contentIsVisible || !::webView.isInitialized) return
        contentIsVisible = true
        webView.visibility = View.VISIBLE
        webView.animate().alpha(1f).setDuration(160L).start()
        if (::splashView.isInitialized) {
            splashView.animate().alpha(0f).setDuration(120L).withEndAction {
                if (splashView.parent === rootView) rootView.removeView(splashView)
            }.start()
        }
    }

    private fun applyLaunchIntent(source: Intent?) {
        val shouldApply = source?.getBooleanExtra(UpdateManager.EXTRA_APPLY_WEB_UPDATE, false) == true
        val version = source?.getStringExtra(UpdateManager.EXTRA_WEB_VERSION)
            ?: UpdateManager.appliedWebVersion(this)
        if (shouldApply) {
            pendingAppliedWebVersion = version
            webView.clearCache(true)
        }
        val cacheBust = if (shouldApply) "&wv=${Uri.encode(version)}&update=${System.currentTimeMillis()}" else ""
        webView.loadUrl(APP_URL + cacheBust)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        pendingCameraIntent = null
        pendingCameraUri = null
        webView.removeJavascriptInterface("AtrangiNative")
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val APP_URL = "https://vaibhavshinde144.github.io/atrangi-document-workspace/?app=7.2.1"
        private const val APP_HOST = "vaibhavshinde144.github.io"
        private const val INSTALL_URL = "https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk"
        private const val CONTENT_READY_FALLBACK_MS = 1600L
        private const val MAX_SHARE_BASE64_CHARS = 32_000_000
    }
}
