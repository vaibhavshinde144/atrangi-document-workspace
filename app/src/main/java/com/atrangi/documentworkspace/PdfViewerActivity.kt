package com.atrangi.documentworkspace

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Fully local PDF viewer for documents opened from Android's Open with / Share flows.
 * Android PdfRenderer is used directly, so the first page never depends on a network,
 * a WebView blob URL, an advertisement SDK, or a third-party viewer application.
 */
class PdfViewerActivity : AppCompatActivity() {
    private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var rootView: FrameLayout
    private lateinit var contentView: LinearLayout
    private lateinit var statusBarScrim: View
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: LinearLayout
    private lateinit var pageIndicator: TextView
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var cachedPdf: File? = null
    private var displayName: String = "Document.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = PRIMARY_DARK
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        buildInterface()
        applySystemBarInsets()
        openPdf()
    }

    private fun buildInterface() {
        rootView = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, 6.dp, 10.dp, 6.dp)
            setBackgroundColor(PRIMARY_DARK)
        }
        toolbar.addView(
            actionText("‹", "Close PDF") { finish() },
            LinearLayout.LayoutParams(46.dp, 52.dp)
        )

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp, 0, 8.dp, 0)
        }
        titleView = TextView(this).apply {
            text = displayName
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        subtitleView = TextView(this).apply {
            setText(R.string.pdf_viewer_opening_securely)
            setTextColor(Color.rgb(190, 224, 235))
            textSize = 10f
            maxLines = 1
        }
        titles.addView(titleView)
        titles.addView(subtitleView)
        toolbar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        toolbar.addView(
            actionText("Share", "Share PDF") { sharePdf() }.apply {
                textSize = 12f
                background = roundedBackground(Color.rgb(19, 91, 112), 12.dp, Color.rgb(84, 151, 171))
                setPadding(14.dp, 0, 14.dp, 0)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42.dp)
        )
        contentView.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64.dp))

        val viewerHost = FrameLayout(this).apply { setBackgroundColor(PAGE_BACKGROUND) }
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PdfViewerActivity)
            setPadding(12.dp, 12.dp, 12.dp, 18.dp)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updatePageIndicator()
                }
            })
        }
        viewerHost.addView(
            recyclerView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp, 24.dp, 24.dp, 24.dp)
            addView(ProgressBar(this@PdfViewerActivity).apply { isIndeterminate = true })
            addView(TextView(this@PdfViewerActivity).apply {
                setText(R.string.pdf_viewer_opening_offline)
                setTextColor(Color.rgb(40, 72, 83))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 14.dp, 0, 0)
            })
        }
        viewerHost.addView(
            loadingView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        contentView.addView(viewerHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 0, 16.dp, 0)
            setBackgroundColor(Color.WHITE)
            elevation = 8.dp.toFloat()
        }
        pageIndicator = TextView(this).apply {
            setText(R.string.pdf_viewer_preparing_pages)
            setTextColor(Color.rgb(25, 58, 69))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        footer.addView(pageIndicator, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(TextView(this).apply {
            setText(R.string.pdf_viewer_offline_fit)
            setTextColor(Color.rgb(20, 120, 95))
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(10.dp, 7.dp, 10.dp, 7.dp)
            background = roundedBackground(Color.rgb(231, 248, 241), 999.dp, Color.rgb(190, 227, 212))
        })
        contentView.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 54.dp))

        rootView.addView(
            contentView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        statusBarScrim = View(this).apply { setBackgroundColor(PRIMARY_DARK) }
        rootView.addView(
            statusBarScrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        )
        setContentView(rootView)
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val contentParams = contentView.layoutParams as FrameLayout.LayoutParams
            contentParams.leftMargin = maxOf(statusInsets.left, navigationInsets.left)
            contentParams.topMargin = statusInsets.top
            contentParams.rightMargin = maxOf(statusInsets.right, navigationInsets.right)
            contentParams.bottomMargin = navigationInsets.bottom
            contentView.layoutParams = contentParams

            val scrimParams = statusBarScrim.layoutParams as FrameLayout.LayoutParams
            scrimParams.height = statusInsets.top
            statusBarScrim.layoutParams = scrimParams
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun openPdf() {
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            showError("The PDF address is missing.")
            return
        }
        displayName = intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: "Document.pdf"
        titleView.text = displayName
        val startedAt = SystemClock.elapsedRealtime()

        renderExecutor.execute {
            var localDescriptor: ParcelFileDescriptor? = null
            try {
                val viewerDirectory = File(cacheDir, "native-pdf-viewer").apply { mkdirs() }
                val localFile = File(viewerDirectory, "${UUID.randomUUID()}.pdf")
                contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                } ?: throw IllegalStateException("The selected PDF cannot be read.")
                if (localFile.length() == 0L) throw IllegalStateException("The selected PDF is empty.")

                localDescriptor = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val localRenderer = PdfRenderer(localDescriptor)
                if (localRenderer.pageCount < 1) throw IllegalStateException("The PDF contains no viewable pages.")
                descriptor = localDescriptor
                renderer = localRenderer
                cachedPdf = localFile
                localDescriptor = null

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    subtitleView.text = resources.getQuantityString(
                        R.plurals.pdf_viewer_ready,
                        localRenderer.pageCount,
                        localRenderer.pageCount,
                        elapsed
                    )
                    loadingView.visibility = View.GONE
                    recyclerView.adapter = PdfPageAdapter(localRenderer)
                    pageIndicator.text = getString(R.string.pdf_viewer_page_of, 1, localRenderer.pageCount)
                }
            } catch (error: Exception) {
                runCatching { localDescriptor?.close() }
                runOnUiThread { showError(error.message ?: "This PDF could not be opened.") }
            }
        }
    }

    private fun showError(message: String) {
        loadingView.removeAllViews()
        loadingView.addView(TextView(this).apply {
            text = getString(R.string.pdf_viewer_unable_to_open, message)
            setTextColor(Color.rgb(118, 42, 48))
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        loadingView.addView(actionText("Close", "Close PDF viewer") { finish() }.apply {
            textSize = 13f
            background = roundedBackground(Color.rgb(12, 139, 178), 12.dp)
            setPadding(20.dp, 0, 20.dp, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 46.dp).apply { topMargin = 18.dp })
        loadingView.visibility = View.VISIBLE
        subtitleView.setText(R.string.pdf_viewer_read_failure)
        pageIndicator.setText(R.string.pdf_viewer_unavailable)
    }

    private fun sharePdf() {
        val file = cachedPdf
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Please wait until the PDF opens.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            clipData = ClipData.newRawUri(displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Share $displayName"))
    }

    private fun updatePageIndicator() {
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        val count = renderer?.pageCount ?: return
        if (first >= 0) pageIndicator.text = getString(R.string.pdf_viewer_page_of, first + 1, count)
    }

    private fun actionText(label: String, description: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun roundedBackground(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(1.dp, stroke)
        }

    private inner class PdfPageAdapter(private val pdf: PdfRenderer) :
        RecyclerView.Adapter<PdfPageAdapter.PageHolder>() {
        private val cache = object : LruCache<Int, Bitmap>(
            ((Runtime.getRuntime().maxMemory() / 1024L) / 10L).coerceAtMost(48L * 1024L).toInt()
        ) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }

        override fun getItemCount(): Int = pdf.pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                background = roundedBackground(Color.WHITE, 15.dp, Color.rgb(215, 226, 230))
                elevation = 3.dp.toFloat()
            }
            val label = TextView(parent.context).apply {
                setTextColor(Color.rgb(66, 91, 100))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(4.dp, 2.dp, 4.dp, 8.dp)
            }
            val stage = FrameLayout(parent.context).apply { setBackgroundColor(Color.WHITE) }
            val image = ImageView(parent.context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
                minimumHeight = 360.dp
            }
            val progress = ProgressBar(parent.context).apply { isIndeterminate = true }
            stage.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            stage.addView(progress, FrameLayout.LayoutParams(36.dp, 36.dp, Gravity.CENTER))
            card.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp
            }
            return PageHolder(card, label, image, progress)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.boundPage = position
            holder.label.text = getString(R.string.pdf_viewer_page_of, position + 1, pdf.pageCount)
            holder.image.contentDescription = getString(R.string.pdf_viewer_page_description, position + 1)
            val cached = cache.get(position)
            if (cached != null) {
                holder.image.setImageBitmap(cached)
                holder.progress.visibility = View.GONE
                return
            }
            holder.image.setImageDrawable(null)
            holder.progress.visibility = View.VISIBLE
            if (renderExecutor.isShutdown) return
            renderExecutor.execute {
                var page: PdfRenderer.Page? = null
                try {
                    val openedPage = pdf.openPage(position)
                    page = openedPage
                    val availableWidth = (recyclerView.width - 40.dp).coerceAtLeast(480)
                    val targetWidth = availableWidth.coerceAtMost(1800)
                    val targetHeight = (targetWidth.toDouble() * openedPage.height / openedPage.width)
                        .roundToInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    openedPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(position, bitmap)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && holder.boundPage == position) {
                            holder.image.setImageBitmap(bitmap)
                            holder.progress.visibility = View.GONE
                        }
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (holder.boundPage == position) {
                            holder.progress.visibility = View.GONE
                            holder.label.text = getString(R.string.pdf_viewer_page_render_failure, position + 1)
                        }
                    }
                } finally {
                    runCatching { page?.close() }
                }
            }
        }

        override fun onViewRecycled(holder: PageHolder) {
            holder.boundPage = RecyclerView.NO_POSITION
            holder.image.setImageDrawable(null)
            super.onViewRecycled(holder)
        }

        inner class PageHolder(
            item: View,
            val label: TextView,
            val image: ImageView,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(item) {
            var boundPage: Int = RecyclerView.NO_POSITION
        }
    }

    override fun onDestroy() {
        if (!renderExecutor.isShutdown) {
            renderExecutor.execute {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
                runCatching { cachedPdf?.delete() }
            }
            renderExecutor.shutdown()
        }
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_URI = "atrangi.pdf.URI"
        private const val EXTRA_NAME = "atrangi.pdf.NAME"
        private val PRIMARY_DARK = Color.rgb(7, 55, 72)
        private val PAGE_BACKGROUND = Color.rgb(235, 241, 243)

        fun createIntent(context: Context, uri: Uri, displayName: String): Intent =
            Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_NAME, displayName)
            }
    }
}
