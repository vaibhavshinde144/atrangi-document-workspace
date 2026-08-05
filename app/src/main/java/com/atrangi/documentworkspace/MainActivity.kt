package com.atrangi.documentworkspace

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
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
import android.provider.OpenableColumns
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var rootView: FrameLayout
    private lateinit var appContentView: FrameLayout
    private lateinit var statusBarScrim: View
    private lateinit var navigationBarScrim: View
    private lateinit var splashView: View
    private var contentIsVisible = false
    private var webContentReady = false
    private var externalOpenInProgress = false
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingCameraIntent: Intent? = null
    private var pendingCameraUri: Uri? = null
    private var pendingAppliedWebVersion: String? = null
    @Volatile private var pendingExternalDocument: ExternalDocument? = null
    private val nativeExports = ConcurrentHashMap<String, NativeExport>()
    private val exportPickerQueue = ArrayDeque<NativeExport>()
    private var pendingExportDestination: NativeExport? = null

    private data class ExternalDocument(
        val id: String,
        val cacheFile: File,
        val displayName: String,
        val mimeType: String,
        val workspaceAction: String? = null
    )

    private data class NativeExport(
        val id: String,
        val cacheFile: File,
        val displayName: String,
        val mimeType: String,
        val expectedSize: Long,
        var bytesWritten: Long = 0L
    )

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

    private val exportDestinationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val export = pendingExportDestination
        if (export == null) {
            launchNextExportDestination()
            return@registerForActivityResult
        }
        val destination = result.data?.data
        if (result.resultCode != RESULT_OK || destination == null) {
            export.cacheFile.delete()
            pendingExportDestination = null
            Toast.makeText(this, "Export cancelled", Toast.LENGTH_SHORT).show()
            launchNextExportDestination()
            return@registerForActivityResult
        }
        Thread {
            val resultMessage = try {
                contentResolver.openOutputStream(destination, "w")?.use { output ->
                    export.cacheFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("The selected destination is not writable.")
                "Saved ${export.displayName}"
            } catch (error: Exception) {
                "Unable to save ${export.displayName}: ${error.message ?: "Unknown error"}"
            } finally {
                export.cacheFile.delete()
            }
            runOnUiThread {
                pendingExportDestination = null
                Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show()
                launchNextExportDestination()
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (openExternalPdfIfNeeded(intent)) {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(7, 55, 72)
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        rootView = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        appContentView = FrameLayout(this)
        webView = WebView(this).apply {
            alpha = 0f
            visibility = View.INVISIBLE
            setBackgroundColor(Color.rgb(247, 249, 250))
        }
        splashView = createLaunchView()
        appContentView.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        appContentView.addView(
            splashView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        rootView.addView(
            appContentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        statusBarScrim = View(this).apply { setBackgroundColor(Color.rgb(7, 55, 72)) }
        rootView.addView(
            statusBarScrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        )
        navigationBarScrim = View(this).apply { setBackgroundColor(Color.WHITE) }
        rootView.addView(
            navigationBarScrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM)
        )
        setContentView(rootView)
        applySystemBarInsets()
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

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            webContentReady = true
        }
        applyLaunchIntent(intent)
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
            useWideViewPort = true
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
        }

        webView.addJavascriptInterface(AtrangiNativeBridge(), "AtrangiNative")
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

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
                val uri = Uri.parse(url)
                if (uri.scheme !in setOf("http", "https")) {
                    Toast.makeText(
                        this,
                        "This local export could not be prepared. Please retry the export.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@DownloadListener
                }
                val request = DownloadManager.Request(uri)
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

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // The web shell owns safe-area spacing via env(safe-area-inset-*). Keep the
            // WebView edge-to-edge so system insets are applied exactly once.
            val contentParams = appContentView.layoutParams as FrameLayout.LayoutParams
            contentParams.leftMargin = 0
            contentParams.topMargin = 0
            contentParams.rightMargin = 0
            contentParams.bottomMargin = 0
            appContentView.layoutParams = contentParams

            val scrimParams = statusBarScrim.layoutParams as FrameLayout.LayoutParams
            scrimParams.height = statusInsets.top
            statusBarScrim.layoutParams = scrimParams

            val navigationScrimParams = navigationBarScrim.layoutParams as FrameLayout.LayoutParams
            navigationScrimParams.height = navigationInsets.bottom
            navigationBarScrim.layoutParams = navigationScrimParams
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
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
        fun beginFileExport(fileName: String, mimeType: String, expectedSize: Long): String {
            if (expectedSize < 0L || expectedSize > MAX_NATIVE_EXPORT_BYTES) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "This export is too large. Maximum size is ${MAX_NATIVE_EXPORT_BYTES / 1_000_000} MB.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return ""
            }
            return try {
                val safeName = fileName
                    .replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
                    .trim()
                    .take(160)
                    .ifBlank { "Atrangi-Export" }
                val safeMime = mimeType
                    .replace(Regex("[^A-Za-z0-9.+/-]"), "")
                    .take(100)
                    .ifBlank { "application/octet-stream" }
                val id = UUID.randomUUID().toString()
                val exportDir = File(cacheDir, "native-exports").apply { mkdirs() }
                val cacheFile = File.createTempFile("atrangi-export-", ".part", exportDir)
                nativeExports[id] = NativeExport(id, cacheFile, safeName, safeMime, expectedSize)
                id
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to start export: ${error.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                ""
            }
        }

        @JavascriptInterface
        fun appendFileExportChunk(exportId: String, base64Chunk: String): Boolean {
            val export = nativeExports[exportId] ?: return false
            if (base64Chunk.length > MAX_NATIVE_EXPORT_CHUNK_BASE64_CHARS) return false
            return try {
                val bytes = Base64.decode(base64Chunk, Base64.NO_WRAP)
                synchronized(export) {
                    if (export.bytesWritten + bytes.size > MAX_NATIVE_EXPORT_BYTES) return false
                    FileOutputStream(export.cacheFile, true).use { output -> output.write(bytes) }
                    export.bytesWritten += bytes.size
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun finishFileExport(exportId: String): Boolean {
            val export = nativeExports.remove(exportId) ?: return false
            synchronized(export) {
                if (export.bytesWritten != export.expectedSize || export.cacheFile.length() != export.expectedSize) {
                    export.cacheFile.delete()
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Export was incomplete. Please retry.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return false
                }
            }
            saveNativeExport(export)
            return true
        }

        @JavascriptInterface
        fun cancelFileExport(exportId: String) {
            nativeExports.remove(exportId)?.cacheFile?.delete()
        }

        @JavascriptInterface
        fun contentReady() {
            runOnUiThread {
                webContentReady = true
                revealContent()
                tryOpenPendingExternalDocument()
            }
        }

        @JavascriptInterface
        fun externalDocumentInfo(): String {
            val document = pendingExternalDocument ?: return ""
            return JSONObject()
                .put("id", document.id)
                .put("name", document.displayName)
                .put("mime", document.mimeType)
                .put("size", document.cacheFile.length())
                .put("action", document.workspaceAction ?: "")
                .toString()
        }

        @JavascriptInterface
        fun readExternalDocumentChunk(documentId: String, offset: Int, requestedLength: Int): String {
            val document = pendingExternalDocument ?: return ""
            if (document.id != documentId || offset < 0 || requestedLength <= 0) return ""
            val remaining = document.cacheFile.length() - offset.toLong()
            if (remaining <= 0L) return ""
            val length = minOf(requestedLength, EXTERNAL_CHUNK_BYTES, remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            return try {
                RandomAccessFile(document.cacheFile, "r").use { file ->
                    file.seek(offset.toLong())
                    val buffer = ByteArray(length)
                    val read = file.read(buffer)
                    if (read <= 0) "" else Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                }
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun markExternalDocumentConsumed(documentId: String) {
            runOnUiThread {
                val document = pendingExternalDocument
                if (document?.id == documentId) {
                    pendingExternalDocument = null
                    externalOpenInProgress = false
                    document.cacheFile.delete()
                }
            }
        }

        @JavascriptInterface
        fun externalDocumentFailed(documentId: String, message: String) {
            runOnUiThread {
                if (pendingExternalDocument?.id == documentId) externalOpenInProgress = false
                Toast.makeText(
                    this@MainActivity,
                    message.ifBlank { "Unable to open this document in Atrangi." },
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        @JavascriptInterface
        fun installUrl(): String = INSTALL_URL
    }

    private fun saveNativeExport(export: NativeExport) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            enqueueExportDestination(export)
            return
        }
        Thread {
            var destination: Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, export.displayName)
                    put(MediaStore.Downloads.MIME_TYPE, export.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Atrangi")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Android did not provide a Downloads destination.")
                contentResolver.openOutputStream(destination, "w")?.use { output ->
                    export.cacheFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Downloads is not writable.")
                contentResolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null
                )
                export.cacheFile.delete()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Saved ${export.displayName} to Downloads/Atrangi",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) {
                destination?.let { runCatching { contentResolver.delete(it, null, null) } }
                enqueueExportDestination(export)
            }
        }.start()
    }

    private fun enqueueExportDestination(export: NativeExport) {
        runOnUiThread {
            exportPickerQueue.addLast(export)
            launchNextExportDestination()
        }
    }

    private fun launchNextExportDestination() {
        if (pendingExportDestination != null || exportPickerQueue.isEmpty()) return
        val export = exportPickerQueue.removeFirst()
        pendingExportDestination = export
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = export.mimeType
            putExtra(Intent.EXTRA_TITLE, export.displayName)
        }
        try {
            exportDestinationPicker.launch(intent)
        } catch (error: Exception) {
            pendingExportDestination = null
            export.cacheFile.delete()
            Toast.makeText(
                this,
                "Unable to choose an export destination: ${error.message ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            launchNextExportDestination()
        }
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
                if (splashView.parent === appContentView) appContentView.removeView(splashView)
            }.start()
        }
    }

    private fun openExternalPdfIfNeeded(source: Intent?): Boolean {
        if (source?.getBooleanExtra(EXTRA_OPEN_IN_WORKSPACE, false) == true) return false
        val uri = extractExternalDocumentUri(source) ?: return false
        val mimeType = source?.type?.takeIf { it.isNotBlank() }
            ?: runCatching { contentResolver.getType(uri) }.getOrNull()
            ?: "application/octet-stream"
        val displayName = resolveExternalDisplayName(uri, mimeType)
        if (!isPdfDocument(uri, mimeType, displayName)) return false

        val viewerIntent = PdfViewerActivity.createIntent(this, uri, displayName).apply {
            clipData = ClipData.newRawUri(displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(viewerIntent)
        return true
    }

    private fun isPdfDocument(uri: Uri, mimeType: String, displayName: String): Boolean {
        if (mimeType.equals("application/pdf", ignoreCase = true)) return true
        if (displayName.endsWith(".pdf", ignoreCase = true)) return true
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val signature = ByteArray(5)
                input.read(signature) == signature.size && signature.contentEquals("%PDF-".toByteArray())
            } == true
        }.getOrDefault(false)
    }

    private fun applyLaunchIntent(source: Intent?) {
        val shouldApply = source?.getBooleanExtra(UpdateManager.EXTRA_APPLY_WEB_UPDATE, false) == true
        val version = source?.getStringExtra(UpdateManager.EXTRA_WEB_VERSION)
            ?: UpdateManager.appliedWebVersion(this)
        if (shouldApply) {
            pendingAppliedWebVersion = version
            webView.clearCache(true)
        }

        val hasExternalDocument = extractExternalDocumentUri(source) != null
        if (hasExternalDocument) prepareExternalDocument(source)

        val shouldLoadWorkspace = shouldApply || webView.url.isNullOrBlank()
        if (shouldLoadWorkspace) {
            webContentReady = false
            val cacheBust = if (shouldApply) "&wv=${Uri.encode(version)}&update=${System.currentTimeMillis()}" else ""
            webView.loadUrl(APP_URL + cacheBust)
        } else if (hasExternalDocument) {
            tryOpenPendingExternalDocument()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractExternalDocumentUri(source: Intent?): Uri? {
        if (source == null) return null
        return when (source.action) {
            Intent.ACTION_VIEW -> source.data
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    source.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream ?: source.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }
    }

    private fun prepareExternalDocument(source: Intent?) {
        val uri = extractExternalDocumentUri(source) ?: return
        val workspaceAction = source?.getStringExtra(EXTRA_WORKSPACE_ACTION)
            ?.takeIf { it in WORKSPACE_ACTIONS }
        val workspaceSourcePath = source?.getStringExtra(EXTRA_WORKSPACE_SOURCE_PATH)
        externalOpenInProgress = false
        Thread {
            var cacheFileForCleanup: File? = null
            try {
                val mimeType = source?.type?.takeIf { it.isNotBlank() }
                    ?: contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
                    ?: "application/octet-stream"
                val displayName = resolveExternalDisplayName(uri, mimeType)
                val externalDir = File(cacheDir, "external-open").apply { mkdirs() }
                val tempFile = File.createTempFile("atrangi-open-", cacheSuffix(displayName), externalDir)
                cacheFileForCleanup = tempFile
                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("The selected document is not readable.")
                input.use { sourceStream ->
                    tempFile.outputStream().use { target -> sourceStream.copyTo(target) }
                }
                deleteWorkspaceHandoffSource(workspaceSourcePath)
                val document = ExternalDocument(
                    id = UUID.randomUUID().toString(),
                    cacheFile = tempFile,
                    displayName = displayName,
                    mimeType = mimeType,
                    workspaceAction = workspaceAction
                )
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        document.cacheFile.delete()
                        return@runOnUiThread
                    }
                    pendingExternalDocument?.cacheFile?.delete()
                    pendingExternalDocument = document
                    tryOpenPendingExternalDocument()
                }
            } catch (error: Exception) {
                cacheFileForCleanup?.delete()
                deleteWorkspaceHandoffSource(workspaceSourcePath)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Unable to read document: ${error.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun deleteWorkspaceHandoffSource(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val source = File(path).canonicalFile
            val viewerDirectory = File(cacheDir, "native-pdf-viewer").canonicalFile
            if (source.parentFile == viewerDirectory && source.extension.equals("pdf", ignoreCase = true)) {
                source.delete()
            }
        }
    }

    private fun resolveExternalDisplayName(uri: Uri, mimeType: String): String {
        var name: String? = null
        if (uri.scheme.equals("content", ignoreCase = true)) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = cursor.getString(index)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')
        if (name.isNullOrBlank()) name = if (mimeType.equals("application/pdf", ignoreCase = true)) "Document.pdf" else "Document"
        return name!!
            .replace(Regex("[\\/\r\n]"), "_")
            .trim()
            .take(180)
            .ifBlank { "Document" }
    }

    private fun cacheSuffix(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .take(10)
        return if (extension.isBlank()) ".bin" else ".$extension"
    }

    private fun tryOpenPendingExternalDocument() {
        if (!webContentReady || externalOpenInProgress || pendingExternalDocument == null || !::webView.isInitialized) return
        externalOpenInProgress = true
        webView.evaluateJavascript(EXTERNAL_DOCUMENT_OPEN_SCRIPT, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (openExternalPdfIfNeeded(intent)) return
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
        pendingExternalDocument?.cacheFile?.delete()
        pendingExternalDocument = null
        nativeExports.values.forEach { it.cacheFile.delete() }
        nativeExports.clear()
        pendingExportDestination?.cacheFile?.delete()
        pendingExportDestination = null
        exportPickerQueue.forEach { it.cacheFile.delete() }
        exportPickerQueue.clear()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AtrangiNative")
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val APP_URL = "https://vaibhavshinde144.github.io/atrangi-document-workspace/?app=7.2.1"
        private const val APP_HOST = "vaibhavshinde144.github.io"
        private const val INSTALL_URL = "https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk"
        private const val CONTENT_READY_FALLBACK_MS = 1600L
        private const val MAX_SHARE_BASE64_CHARS = 32_000_000
        private const val MAX_NATIVE_EXPORT_BYTES = 250_000_000L
        private const val MAX_NATIVE_EXPORT_CHUNK_BASE64_CHARS = 400_000
        private const val EXTERNAL_CHUNK_BYTES = 256 * 1024
        private const val EXTRA_OPEN_IN_WORKSPACE = "atrangi.pdf.OPEN_IN_WORKSPACE"
        private const val EXTRA_WORKSPACE_ACTION = "atrangi.pdf.WORKSPACE_ACTION"
        private const val EXTRA_WORKSPACE_SOURCE_PATH = "atrangi.pdf.WORKSPACE_SOURCE_PATH"
        private val WORKSPACE_ACTIONS = setOf("search", "edit", "addPassword", "removePassword", "signPdf")
        private val EXTERNAL_DOCUMENT_OPEN_SCRIPT = """
            (function waitForAtrangiExternalOpen(attempt){
              const nativeBridge=window.AtrangiNative;
              const workspace=window.AtrangiWorkspaceV7;
              const core=window.AtrangiWorkspaceCore;
              if(!nativeBridge||!workspace||!core){
                if(attempt<100){setTimeout(()=>waitForAtrangiExternalOpen(attempt+1),100);return;}
                if(nativeBridge)nativeBridge.externalDocumentFailed('', 'Atrangi document viewer did not become ready.');
                return;
              }
              (async()=>{
                let info=null;
                try{
                  const raw=nativeBridge.externalDocumentInfo();
                  if(!raw)throw new Error('External document is no longer available.');
                  info=JSON.parse(raw);
                  const size=Number(info.size||0);
                  const parts=[];
                  let offset=0;
                  const chunkSize=256*1024;
                  while(offset<size){
                    const requested=Math.min(chunkSize,size-offset);
                    const encoded=nativeBridge.readExternalDocumentChunk(info.id,offset,requested);
                    if(!encoded)throw new Error('Unable to read the external document.');
                    const binary=atob(encoded);
                    const bytes=new Uint8Array(binary.length);
                    for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
                    parts.push(bytes);
                    offset+=bytes.length;
                  }
                  const file=new File(parts,info.name||'Document',{type:info.mime||'application/octet-stream',lastModified:Date.now()});
                  const detected=await core.detectWithSignature(file);
                  const now=Date.now();
                  const asset={
                    id:'external:'+info.id,
                    name:file.name,
                    mime:file.type||info.mime||'application/octet-stream',
                    size:file.size,
                    family:detected.id,
                    createdAt:now,
                    modifiedAt:now,
                    versionNo:1,
                    starred:false,
                    tags:[],
                    blob:file,
                    previewStatus:'ready'
                  };
                  asset.capability=core.capability(asset);
                  if(window.AtrangiScannerApp?.switchTab)window.AtrangiScannerApp.switchTab('files');
                  if(info.action&&typeof workspace.openExternalAction==='function'){
                    await workspace.openExternalAction(info.action,file,asset);
                  }else{
                    await workspace.openAsset(asset);
                  }
                  nativeBridge.markExternalDocumentConsumed(info.id);
                }catch(error){
                  nativeBridge.externalDocumentFailed(info?.id||'',error?.message||String(error||'Unable to open document.'));
                }
              })();
            })(0);
        """.trimIndent()
    }
}
