package com.atrangi.documentworkspace

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import kotlin.math.abs
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
    private lateinit var zoomLabel: TextView
    private lateinit var nextSearchAction: TextView
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var cachedPdf: File? = null
    private var pageAdapter: PdfPageAdapter? = null
    private var displayName: String = "Document.pdf"
    private var zoomLevel = 1f
    private var searchQuery = ""
    private var searchResultPages = emptyList<Int>()
    private var currentSearchResult = 0
    private var handoffInProgress = false

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

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp, 7.dp, 10.dp, 7.dp)
        }
        actionRow.addView(actionChip("Search", "Search text in this PDF") { promptSearch() })
        nextSearchAction = actionChip("Next", "Go to the next search result") { nextSearchResult() }.apply {
            visibility = View.GONE
        }
        actionRow.addView(nextSearchAction)
        actionRow.addView(actionChip("Edit", "Edit or annotate in Atrangi") { openInWorkspace("edit") })
        actionRow.addView(actionChip("Add password", "Protect this PDF with a password") { openInWorkspace("addPassword") })
        actionRow.addView(actionChip("Remove password", "Remove this PDF password") { openInWorkspace("removePassword") })
        actionRow.addView(actionChip("Sign", "Add a signature to this PDF") { openInWorkspace("signPdf") })
        contentView.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.WHITE)
            addView(actionRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 58.dp))

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
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoom(zoomLevel * detector.scaleFactor)
                return true
            }
        })
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                scaleDetector.onTouchEvent(event)
                return scaleDetector.isInProgress
            }

            override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
                scaleDetector.onTouchEvent(event)
            }
        })
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
        footer.addView(footerControl("−", "Zoom out") { setZoom(zoomLevel - .25f) })
        zoomLabel = TextView(this).apply {
            text = "100%"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 58, 69))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        footer.addView(zoomLabel, LinearLayout.LayoutParams(50.dp, 42.dp))
        footer.addView(footerControl("+", "Zoom in") { setZoom(zoomLevel + .25f) })
        footer.addView(footerControl("Fit", "Fit pages to screen") { setZoom(1f) }.apply { textSize = 11f })
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
                    pageAdapter = PdfPageAdapter(localRenderer).also { recyclerView.adapter = it }
                    pageIndicator.text = getString(R.string.pdf_viewer_page_of, 1, localRenderer.pageCount)
                }
            } catch (error: Exception) {
                runCatching { localDescriptor?.close() }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showError(error.message ?: "This PDF could not be opened.")
                    }
                }
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

    private fun promptSearch() {
        if (cachedPdf == null) {
            Toast.makeText(this, "Please wait until the PDF opens.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            AlertDialog.Builder(this)
                .setTitle("Search this PDF")
                .setMessage("On this Android version, text search opens in the Atrangi workspace and remains on your device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> openInWorkspace("search") }
                .show()
            return
        }
        val input = EditText(this).apply {
            hint = "Text to find"
            setSingleLine(true)
            setText(searchQuery)
            selectAll()
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        val holder = FrameLayout(this).apply {
            setPadding(20.dp, 4.dp, 20.dp, 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Search this PDF")
            .setView(holder)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ -> searchPdf(input.text.toString()) }
            .show()
    }

    @Suppress("NewApi")
    private fun searchPdf(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter text to search for.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            openInWorkspace("search")
            return
        }
        searchQuery = query
        nextSearchAction.visibility = View.GONE
        subtitleView.text = getString(R.string.pdf_viewer_searching, query)
        renderExecutor.execute {
            val localRenderer = renderer ?: return@execute
            val pages = mutableListOf<Int>()
            val highlights = mutableMapOf<Int, List<RectF>>()
            try {
                for (pageIndex in 0 until localRenderer.pageCount) {
                    localRenderer.openPage(pageIndex).use { page ->
                        val matches = page.searchText(query)
                        if (matches.isNotEmpty()) {
                            repeat(matches.size) { pages.add(pageIndex) }
                            highlights[pageIndex] = matches.flatMap { match ->
                                match.bounds.map(::RectF)
                            }
                        }
                    }
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    searchResultPages = pages
                    currentSearchResult = 0
                    pageAdapter?.setSearchHighlights(highlights)
                    if (pages.isEmpty()) {
                        subtitleView.text = getString(R.string.pdf_viewer_no_search_results, query)
                        Toast.makeText(this, "No embedded text found. Try OCR or Edit in the workspace.", Toast.LENGTH_LONG).show()
                    } else {
                        nextSearchAction.visibility = View.VISIBLE
                        showCurrentSearchResult()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        subtitleView.setText(R.string.pdf_viewer_search_unavailable)
                        Toast.makeText(this, "Search failed: ${error.message ?: "Unable to read PDF text"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun nextSearchResult() {
        if (searchResultPages.isEmpty()) return
        currentSearchResult = (currentSearchResult + 1) % searchResultPages.size
        showCurrentSearchResult()
    }

    private fun showCurrentSearchResult() {
        val page = searchResultPages.getOrNull(currentSearchResult) ?: return
        nextSearchAction.text = "Next ${currentSearchResult + 1}/${searchResultPages.size}"
        subtitleView.text = getString(
            R.string.pdf_viewer_search_position,
            searchQuery,
            currentSearchResult + 1,
            searchResultPages.size,
            page + 1
        )
        recyclerView.smoothScrollToPosition(page)
    }

    private fun setZoom(requested: Float) {
        val next = requested.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (abs(next - zoomLevel) < .01f) return
        val manager = recyclerView.layoutManager as? LinearLayoutManager
        val visiblePage = manager?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
        val offset = manager?.findViewByPosition(visiblePage)?.top ?: 0
        zoomLevel = next
        zoomLabel.text = "${(zoomLevel * 100).roundToInt()}%"
        pageAdapter?.setZoom(zoomLevel)
        recyclerView.post { manager?.scrollToPositionWithOffset(visiblePage, offset) }
    }

    private fun openInWorkspace(action: String) {
        val file = cachedPdf
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Please wait until the PDF opens.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val workspaceIntent = Intent(this, MainActivity::class.java).apply {
                this.action = Intent.ACTION_VIEW
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri(displayName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_IN_WORKSPACE, true)
                putExtra(EXTRA_WORKSPACE_ACTION, action)
                putExtra(EXTRA_WORKSPACE_SOURCE_PATH, file.canonicalPath)
            }
            handoffInProgress = true
            startActivity(workspaceIntent)
            finish()
        } catch (error: Exception) {
            handoffInProgress = false
            Toast.makeText(this, "Unable to open workspace: ${error.message}", Toast.LENGTH_LONG).show()
        }
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

    private fun actionChip(label: String, description: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(10, 92, 117))
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(14.dp, 0, 14.dp, 0)
        background = roundedBackground(Color.rgb(244, 250, 252), 12.dp, Color.rgb(193, 216, 224))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42.dp).apply { marginEnd = 8.dp }
    }

    private fun footerControl(label: String, description: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(10, 92, 117))
        textSize = 19f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        background = roundedBackground(Color.rgb(244, 250, 252), 10.dp, Color.rgb(205, 222, 228))
        layoutParams = LinearLayout.LayoutParams(44.dp, 42.dp).apply { marginStart = 5.dp }
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
        private val cache = object : LruCache<String, Bitmap>(
            ((Runtime.getRuntime().maxMemory() / 1024L) / 10L).coerceAtMost(48L * 1024L).toInt()
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
        private var adapterZoom = 1f
        @Volatile private var searchHighlights: Map<Int, List<RectF>> = emptyMap()
        private var searchVersion = 0

        fun setZoom(value: Float) {
            adapterZoom = value
            notifyDataSetChanged()
        }

        fun setSearchHighlights(value: Map<Int, List<RectF>>) {
            searchHighlights = value.mapValues { (_, bounds) -> bounds.map(::RectF) }
            searchVersion++
            cache.evictAll()
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = pdf.pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val scroller = HorizontalScrollView(parent.context).apply {
                isHorizontalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
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
            scroller.addView(card, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            scroller.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp
            }
            return PageHolder(scroller, scroller, card, label, image, progress)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.boundPage = position
            val matchCount = searchHighlights[position]?.size ?: 0
            holder.label.text = if (matchCount > 0) {
                getString(R.string.pdf_viewer_page_matches, position + 1, pdf.pageCount, matchCount)
            } else {
                getString(R.string.pdf_viewer_page_of, position + 1, pdf.pageCount)
            }
            holder.image.contentDescription = getString(R.string.pdf_viewer_page_description, position + 1)
            val viewportWidth = (recyclerView.width - 24.dp).coerceAtLeast(320.dp)
            val cardWidth = (viewportWidth * adapterZoom).roundToInt().coerceAtLeast(240.dp)
            holder.scroller.isFillViewport = adapterZoom <= 1f
            holder.card.layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
            val cacheKey = "$position:${(adapterZoom * 100).roundToInt()}:$searchVersion"
            holder.boundRenderKey = cacheKey
            val cached = cache.get(cacheKey)
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
                    val targetWidth = (cardWidth - 16.dp).coerceIn(480, 2000)
                    val targetHeight = (targetWidth.toDouble() * openedPage.height / openedPage.width)
                        .roundToInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    openedPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val bounds = searchHighlights[position].orEmpty()
                    if (bounds.isNotEmpty()) {
                        val canvas = Canvas(bitmap)
                        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(92, 255, 205, 40) }
                        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(223, 152, 0)
                            style = Paint.Style.STROKE
                            strokeWidth = 2.dp.toFloat()
                        }
                        val scaleX = targetWidth.toFloat() / openedPage.width
                        val scaleY = targetHeight.toFloat() / openedPage.height
                        bounds.forEach { source ->
                            val target = RectF(source.left * scaleX, source.top * scaleY, source.right * scaleX, source.bottom * scaleY)
                            canvas.drawRoundRect(target, 3.dp.toFloat(), 3.dp.toFloat(), fill)
                            canvas.drawRoundRect(target, 3.dp.toFloat(), 3.dp.toFloat(), stroke)
                        }
                    }
                    cache.put(cacheKey, bitmap)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && holder.boundPage == position && holder.boundRenderKey == cacheKey) {
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
            holder.boundRenderKey = ""
            holder.image.setImageDrawable(null)
            super.onViewRecycled(holder)
        }

        inner class PageHolder(
            item: View,
            val scroller: HorizontalScrollView,
            val card: LinearLayout,
            val label: TextView,
            val image: ImageView,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(item) {
            var boundPage: Int = RecyclerView.NO_POSITION
            var boundRenderKey: String = ""
        }
    }

    override fun onDestroy() {
        if (!renderExecutor.isShutdown) {
            renderExecutor.execute {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
                if (!handoffInProgress) runCatching { cachedPdf?.delete() }
            }
            renderExecutor.shutdown()
        }
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_URI = "atrangi.pdf.URI"
        private const val EXTRA_NAME = "atrangi.pdf.NAME"
        private const val EXTRA_OPEN_IN_WORKSPACE = "atrangi.pdf.OPEN_IN_WORKSPACE"
        private const val EXTRA_WORKSPACE_ACTION = "atrangi.pdf.WORKSPACE_ACTION"
        private const val EXTRA_WORKSPACE_SOURCE_PATH = "atrangi.pdf.WORKSPACE_SOURCE_PATH"
        private const val MIN_ZOOM = .75f
        private const val MAX_ZOOM = 3f
        private val PRIMARY_DARK = Color.rgb(7, 55, 72)
        private val PAGE_BACKGROUND = Color.rgb(235, 241, 243)

        fun createIntent(context: Context, uri: Uri, displayName: String): Intent =
            Intent(context, PdfViewerActivity::class.java).apply {
                data = uri
                type = "application/pdf"
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_NAME, displayName)
            }
    }
}
