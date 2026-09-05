package com.example.assistive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.net.URLEncoder

class BrowserManager(
    private val service: FloatingBallService,
    private val menuView: View
) {
    companion object {
        private const val DEFAULT_HOME_URL = "https://www.google.com"
    }

    data class WindowDimensions(val widthDp: Int, val heightDp: Int)

    // Layout Containers
    private lateinit var layoutBrowserContainer: FrameLayout
    private lateinit var layoutBrowserHeader: RelativeLayout
    private lateinit var layoutBrowserOmnibox: LinearLayout
    private lateinit var layoutWebviewContainer: FrameLayout
    private lateinit var layoutBrowserBottomToolbar: LinearLayout
    private lateinit var layoutError: View

    // Header elements
    private lateinit var btnBackToMenu: ImageButton
    private lateinit var txtTitle: TextView
    private lateinit var btnHideBars: ImageButton
    private lateinit var btnSizeToggle: ImageButton
    private lateinit var btnClose: ImageButton

    // Floating Unhide button
    private lateinit var btnUnhide: ImageButton

    // Omnibox / Search elements
    private lateinit var edtUrl: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnGo: TextView
    private lateinit var progressBar: ProgressBar

    // WebView & Video Custom View
    private lateinit var webView: WebView
    private lateinit var txtErrorMsg: TextView
    private lateinit var btnRetry: TextView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Bottom Navigation toolbar
    private lateinit var btnWebBack: ImageButton
    private lateinit var btnWebForward: ImageButton
    private lateinit var btnWebRefresh: ImageButton
    private lateinit var btnWebHome: ImageButton
    private lateinit var btnWebOpenExternal: ImageButton

    // State
    private var isExpanded = false
    private var areBarsHidden = false
    private var isLoading = false
    private var lastFailedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        layoutBrowserContainer = menuView.findViewById(R.id.layout_browser_container)
        layoutBrowserHeader = layoutBrowserContainer.findViewById(R.id.layout_browser_header)
        layoutBrowserOmnibox = layoutBrowserContainer.findViewById(R.id.layout_browser_omnibox)
        layoutWebviewContainer = layoutBrowserContainer.findViewById(R.id.layout_webview_container)
        layoutBrowserBottomToolbar = layoutBrowserContainer.findViewById(R.id.layout_browser_bottom_toolbar)

        // Header
        btnBackToMenu = layoutBrowserContainer.findViewById(R.id.btn_browser_back_to_menu)
        txtTitle = layoutBrowserContainer.findViewById(R.id.txt_browser_title)
        btnHideBars = layoutBrowserContainer.findViewById(R.id.btn_browser_hide_bars)
        btnSizeToggle = layoutBrowserContainer.findViewById(R.id.btn_browser_size_toggle)
        btnClose = layoutBrowserContainer.findViewById(R.id.btn_browser_close)

        // Floating Unhide Button
        btnUnhide = layoutBrowserContainer.findViewById(R.id.btn_browser_unhide)

        // Omnibox
        edtUrl = layoutBrowserContainer.findViewById(R.id.edt_browser_url)
        btnClearUrl = layoutBrowserContainer.findViewById(R.id.btn_browser_clear_url)
        btnGo = layoutBrowserContainer.findViewById(R.id.btn_browser_go)
        progressBar = layoutBrowserContainer.findViewById(R.id.progress_browser)

        // WebView & Error View
        webView = layoutBrowserContainer.findViewById(R.id.webview_browser)
        layoutError = layoutBrowserContainer.findViewById(R.id.layout_browser_error)
        txtErrorMsg = layoutBrowserContainer.findViewById(R.id.txt_browser_error_msg)
        btnRetry = layoutBrowserContainer.findViewById(R.id.btn_browser_retry)

        // Bottom toolbar
        btnWebBack = layoutBrowserContainer.findViewById(R.id.btn_web_back)
        btnWebForward = layoutBrowserContainer.findViewById(R.id.btn_web_forward)
        btnWebRefresh = layoutBrowserContainer.findViewById(R.id.btn_web_refresh)
        btnWebHome = layoutBrowserContainer.findViewById(R.id.btn_web_home)
        btnWebOpenExternal = layoutBrowserContainer.findViewById(R.id.btn_web_open_external)

        setupWebView()
        setupListeners()
        setupDragToMove()
        applyAdaptiveDimensions()
        updateNavigationButtonsState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Force hardware acceleration layer on webview
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.keepScreenOn = true

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false

            // Crucial for YouTube / Shorts video continuous playback without requiring user gesture
            mediaPlaybackRequiresUserGesture = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // Remove '; wv' and 'Version/4.0' from User Agent so YouTube treats this as standard Chrome mobile
            val defaultUa = userAgentString
            userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
        }

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                // External protocol intents (tel:, mailto:, market:, intent:, etc.)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 10
                btnWebRefresh.setImageResource(R.drawable.ic_close)
                layoutError.visibility = View.GONE

                if (!edtUrl.hasFocus() && !url.isNullOrEmpty()) {
                    edtUrl.setText(url)
                }
                updateNavigationButtonsState()
                injectMediaKeepAliveScript()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                progressBar.visibility = View.GONE
                btnWebRefresh.setImageResource(R.drawable.ic_refresh)

                val pageTitle = view?.title
                if (!pageTitle.isNullOrBlank()) {
                    txtTitle.text = pageTitle
                } else {
                    txtTitle.text = "Web Browser"
                }

                if (!edtUrl.hasFocus() && !url.isNullOrEmpty()) {
                    edtUrl.setText(url)
                }
                updateNavigationButtonsState()
                injectMediaKeepAliveScript()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    lastFailedUrl = request.url?.toString()
                    layoutError.visibility = View.VISIBLE
                    txtErrorMsg.text = error?.description?.toString() ?: "Connection error"
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    lastFailedUrl = failingUrl
                    layoutError.visibility = View.VISIBLE
                    txtErrorMsg.text = description ?: "Connection error"
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
                if (newProgress >= 40) {
                    injectMediaKeepAliveScript()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    txtTitle.text = title
                }
            }

            // Fullscreen video support for YouTube
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                layoutWebviewContainer.addView(
                    customView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                customView?.let {
                    layoutWebviewContainer.removeView(it)
                    customView = null
                }
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Injects JavaScript into the web page to prevent YouTube / YouTube Shorts from pausing
     * automatically during window blur, layout resize, or background checks.
     */
    private fun injectMediaKeepAliveScript() {
        val js = """
            (function() {
                // 1. Override Document prototype properties
                try {
                    Object.defineProperty(Document.prototype, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(Document.prototype, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Object.defineProperty(Document.prototype, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Document.prototype.hasFocus = function() { return true; };
                } catch(e) {}

                // 2. Block blur and visibilitychange listeners from executing YouTube's auto-pause
                var suppressEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur'];
                suppressEvents.forEach(function(evt) {
                    window.addEventListener(evt, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                    document.addEventListener(evt, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                });

                // 3. Track user deliberate interactions on the webpage
                if (!window.__userTapTracked) {
                    window.__userTapTracked = true;
                    window.__lastUserTap = 0;
                    var recordUserTap = function() {
                        window.__lastUserTap = Date.now();
                    };
                    window.addEventListener('pointerdown', recordUserTap, true);
                    window.addEventListener('touchstart', recordUserTap, true);
                    window.addEventListener('click', recordUserTap, true);

                    // 4. Intercept automatic pause calls
                    document.addEventListener('pause', function(e) {
                        var video = e.target;
                        if (video && video.tagName === 'VIDEO') {
                            var elapsed = Date.now() - (window.__lastUserTap || 0);
                            // If pause occurred without a direct user tap within 600ms (e.g. resize, blur, toolbar toggle), auto-resume it!
                            if (elapsed > 600 && !video.ended) {
                                setTimeout(function() {
                                    if (video.paused && !video.ended && (Date.now() - (window.__lastUserTap || 0) > 600)) {
                                        video.play().catch(function(){});
                                    }
                                }, 60);
                            }
                        }
                    }, true);
                }

                // 5. Keep inline playback on videos
                function patchVideos() {
                    var vids = document.getElementsByTagName('video');
                    for (var i = 0; i < vids.length; i++) {
                        vids[i].setAttribute('playsinline', '');
                        vids[i].setAttribute('webkit-playsinline', '');
                    }
                }
                patchVideos();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun setupListeners() {
        // Back to main floating menu
        btnBackToMenu.setOnClickListener {
            closeBrowserPanel()
        }

        // Close entire floating overlay
        btnClose.setOnClickListener {
            hideKeyboard()
            service.setMenuFocusable(false)
            service.closeMenu()
        }

        // Toggle size between compact and expanded
        btnSizeToggle.setOnClickListener {
            toggleWindowSize()
        }

        // Hide toolbars (Immersive Theater Mode)
        btnHideBars.setOnClickListener {
            hideToolbars()
        }

        // Floating button to unhide toolbars
        btnUnhide.setOnClickListener {
            showToolbars()
        }

        // Omnibox text changes
        edtUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearUrl.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Omnibox keyboard Action Go
        edtUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                executeSearch()
                true
            } else {
                false
            }
        }

        edtUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                service.setMenuFocusable(true)
                edtUrl.selectAll()
            }
        }

        // Clear omnibox text
        btnClearUrl.setOnClickListener {
            edtUrl.setText("")
            edtUrl.requestFocus()
            showKeyboard(edtUrl)
        }

        // Go button
        btnGo.setOnClickListener {
            executeSearch()
        }

        // Retry on error
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            if (!lastFailedUrl.isNullOrEmpty()) {
                webView.loadUrl(lastFailedUrl!!)
            } else {
                webView.reload()
            }
        }

        // Navigation toolbar actions
        btnWebBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                Toast.makeText(service, "No earlier page", Toast.LENGTH_SHORT).show()
            }
        }

        btnWebForward.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        btnWebRefresh.setOnClickListener {
            if (isLoading) {
                webView.stopLoading()
            } else {
                webView.reload()
            }
        }

        btnWebHome.setOnClickListener {
            loadSearchOrUrl(DEFAULT_HOME_URL)
        }

        btnWebOpenExternal.setOnClickListener {
            val currentUrl = webView.url ?: DEFAULT_HOME_URL
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
                service.closeMenu()
            } catch (e: Exception) {
                Toast.makeText(service, "Cannot open external browser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideToolbars() {
        areBarsHidden = true
        hideKeyboard()
        layoutBrowserHeader.visibility = View.GONE
        layoutBrowserOmnibox.visibility = View.GONE
        layoutBrowserBottomToolbar.visibility = View.GONE
        btnUnhide.visibility = View.VISIBLE

        // Immediately ensure videos continue playing across the layout change
        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun showToolbars() {
        areBarsHidden = false
        layoutBrowserHeader.visibility = View.VISIBLE
        layoutBrowserOmnibox.visibility = View.VISIBLE
        layoutBrowserBottomToolbar.visibility = View.VISIBLE
        btnUnhide.visibility = View.GONE

        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun resumeAllVideos() {
        val js = """
            (function() {
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                    if (vids[i].paused && !vids[i].ended) {
                        vids[i].play().catch(function(){});
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragToMove() {
        var startMenuX = 0
        var startMenuY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        txtTitle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startMenuX = service.menuParams.x
                    startMenuY = service.menuParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchStartX).toInt()
                    val deltaY = (event.rawY - touchStartY).toInt()
                    service.menuParams.x = startMenuX + deltaX
                    service.menuParams.y = startMenuY + deltaY
                    clampWindowToScreen()
                    true
                }
                else -> false
            }
        }
    }

    private fun executeSearch() {
        val query = edtUrl.text.toString().trim()
        hideKeyboard()
        edtUrl.clearFocus()
        loadSearchOrUrl(query)
    }

    fun loadSearchOrUrl(input: String) {
        val query = input.trim()
        val targetUrl = when {
            query.isEmpty() -> DEFAULT_HOME_URL
            query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) -> query
            !query.contains(" ") && (query.contains(".") || query.startsWith("localhost", ignoreCase = true)) -> "https://$query"
            else -> {
                val encoded = URLEncoder.encode(query, "UTF-8")
                "https://www.google.com/search?q=$encoded"
            }
        }

        layoutError.visibility = View.GONE
        edtUrl.setText(targetUrl)
        webView.loadUrl(targetUrl)
    }

    fun openBrowser() {
        service.setMenuFocusable(true)
        applyAdaptiveDimensions()
        repositionBrowserWindow()

        // Resume webview if paused
        webView.onResume()
        webView.resumeTimers()

        if (webView.url.isNullOrEmpty()) {
            loadSearchOrUrl(DEFAULT_HOME_URL)
        }
        updateNavigationButtonsState()
    }

    fun onConfigurationChanged() {
        applyAdaptiveDimensions()
        repositionBrowserWindow()
        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun closeBrowserPanel() {
        hideKeyboard()
        service.setMenuFocusable(false)
        showToolbars()

        layoutBrowserContainer.visibility = View.GONE
        val menuButtons = menuView.findViewById<View>(R.id.layout_menu_buttons)
        menuButtons.visibility = View.VISIBLE

        // Reset menu position for standard 200x234dp buttons grid so it doesn't overflow
        val density = service.resources.displayMetrics.density
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val menuWidthPx = (224 * density).toInt()
        val menuHeightPx = (258 * density).toInt()
        val pad = (8 * density).toInt()

        service.menuParams.x = service.menuParams.x.coerceIn(pad, (screenWidth - menuWidthPx - pad).coerceAtLeast(pad))
        service.menuParams.y = service.menuParams.y.coerceIn(pad, (screenHeight - menuHeightPx - pad).coerceAtLeast(pad))
        try {
            service.windowManager.updateViewLayout(service.menuView, service.menuParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleWindowSize() {
        isExpanded = !isExpanded
        applyAdaptiveDimensions()
        repositionBrowserWindow()
        btnSizeToggle.setImageResource(if (isExpanded) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun getAdaptiveDimensions(): WindowDimensions {
        val displayMetrics = service.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidthDp = (displayMetrics.widthPixels / density).toInt()
        val screenHeightDp = (displayMetrics.heightPixels / density).toInt()

        val isLandscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                || displayMetrics.widthPixels > displayMetrics.heightPixels

        return if (isLandscape) {
            // Landscape: Reduced width to avoid being "kinda long", optimal 16:9 ratio
            val maxAllowedH = (screenHeightDp - 48).coerceAtLeast(200)
            if (isExpanded) {
                // Expanded landscape: ~400-440dp width
                val targetW = (screenWidthDp * 0.50f).toInt().coerceIn(360, 440)
                val targetH = (screenHeightDp - 32).coerceIn(240, (screenHeightDp - 24).coerceAtLeast(220))
                WindowDimensions(targetW, targetH)
            } else {
                // Compact landscape: ~320-350dp width (leaves ample screen space behind)
                val targetW = (screenWidthDp * 0.38f).toInt().coerceIn(300, 350)
                val targetH = (screenHeightDp * 0.72f).toInt().coerceIn(210, maxAllowedH)
                WindowDimensions(targetW, targetH)
            }
        } else {
            // Portrait: Screen width is narrower, but height is tall
            if (isExpanded) {
                val targetW = (screenWidthDp - 16).coerceIn(310, 370)
                val targetH = (screenHeightDp - 80).coerceIn(460, 620)
                WindowDimensions(targetW, targetH)
            } else {
                val targetW = (screenWidthDp - 36).coerceIn(270, 310)
                val targetH = (screenHeightDp * 0.50f).toInt().coerceIn(330, 420)
                WindowDimensions(targetW, targetH)
            }
        }
    }

    private fun applyAdaptiveDimensions() {
        val dims = getAdaptiveDimensions()
        val density = service.resources.displayMetrics.density

        val params = layoutBrowserContainer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.width = (dims.widthDp * density).toInt()
        params.height = (dims.heightDp * density).toInt()
        layoutBrowserContainer.layoutParams = params
    }

    private fun repositionBrowserWindow() {
        val displayMetrics = service.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val edgePadPx = (8 * density).toInt()

        val dims = getAdaptiveDimensions()
        // Account for 12dp padding on all sides in floating_menu_layout.xml (24dp total)
        val totalWidthPx = ((dims.widthDp + 24) * density).toInt()
        val totalHeightPx = ((dims.heightDp + 24) * density).toInt()

        val isLandscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                || displayMetrics.widthPixels > displayMetrics.heightPixels

        var targetX = service.menuParams.x
        var targetY = service.menuParams.y

        if (isLandscape) {
            // In landscape, center vertically so nothing is ever clipped by bottom or top
            val verticalSlack = (screenHeight - totalHeightPx).coerceAtLeast(0)
            targetY = (verticalSlack / 2).coerceIn(
                edgePadPx,
                (screenHeight - totalHeightPx - edgePadPx).coerceAtLeast(edgePadPx)
            )

            // Keep X cleanly inside screen bounds
            val maxX = (screenWidth - totalWidthPx - edgePadPx).coerceAtLeast(edgePadPx)
            targetX = targetX.coerceIn(edgePadPx, maxX)
        } else {
            // In portrait, keep safely within screen bounds
            val maxX = (screenWidth - totalWidthPx - edgePadPx).coerceAtLeast(edgePadPx)
            val maxY = (screenHeight - totalHeightPx - edgePadPx).coerceAtLeast(edgePadPx)
            targetX = targetX.coerceIn(edgePadPx, maxX)
            targetY = targetY.coerceIn(edgePadPx, maxY)
        }

        service.menuParams.x = targetX
        service.menuParams.y = targetY

        if (service.isMenuVisible) {
            try {
                service.windowManager.updateViewLayout(service.menuView, service.menuParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun clampWindowToScreen() {
        val displayMetrics = service.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val padPx = (8 * density).toInt()

        val dims = getAdaptiveDimensions()
        val totalWidthPx = ((dims.widthDp + 24) * density).toInt()
        val totalHeightPx = ((dims.heightDp + 24) * density).toInt()

        val maxX = (screenWidth - totalWidthPx - padPx).coerceAtLeast(padPx)
        val maxY = (screenHeight - totalHeightPx - padPx).coerceAtLeast(padPx)

        service.menuParams.x = service.menuParams.x.coerceIn(padPx, maxX)
        service.menuParams.y = service.menuParams.y.coerceIn(padPx, maxY)

        if (service.isMenuVisible) {
            try {
                service.windowManager.updateViewLayout(service.menuView, service.menuParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateNavigationButtonsState() {
        val canGoBack = webView.canGoBack()
        val canGoForward = webView.canGoForward()

        btnWebBack.alpha = if (canGoBack) 1.0f else 0.4f
        btnWebForward.alpha = if (canGoForward) 1.0f else 0.4f
    }

    private fun hideKeyboard() {
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(menuView.windowToken, 0)
    }

    private fun showKeyboard(view: View) {
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun onDestroy() {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
