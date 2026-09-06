package com.example.assistive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.pm.PackageManager
import android.webkit.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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

    // Tab Switcher Container & Views
    private lateinit var layoutTabSwitcher: LinearLayout
    private lateinit var txtTabSwitcherCount: TextView
    private lateinit var btnTabsCloseAll: TextView
    private lateinit var btnTabsAddNew: TextView
    private lateinit var recyclerTabs: RecyclerView
    private lateinit var layoutEmptyTabs: LinearLayout
    private lateinit var btnTabsEmptyNew: TextView
    private var tabsAdapter: TabsAdapter? = null

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

    // Error View & Video Custom View
    private lateinit var txtErrorMsg: TextView
    private lateinit var btnRetry: TextView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Bottom Navigation toolbar
    private lateinit var btnWebBack: ImageButton
    private lateinit var btnWebForward: ImageButton
    private lateinit var btnWebRefresh: ImageButton
    private lateinit var btnWebHome: ImageButton
    private lateinit var btnWebTabs: FrameLayout
    private lateinit var txtTabCountBadge: TextView

    // Tab State
    private val tabsList = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    val activeTab: BrowserTab?
        get() = tabsList.getOrNull(activeTabIndex)

    // View State
    private var isTabSwitcherOpen = false
    private var isExpanded = false
    private var areBarsHidden = false
    private var isLoading = false

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

        // Error View
        layoutError = layoutBrowserContainer.findViewById(R.id.layout_browser_error)
        txtErrorMsg = layoutBrowserContainer.findViewById(R.id.txt_browser_error_msg)
        btnRetry = layoutBrowserContainer.findViewById(R.id.btn_browser_retry)

        // Tab Switcher Views
        layoutTabSwitcher = layoutBrowserContainer.findViewById(R.id.layout_tab_switcher)
        txtTabSwitcherCount = layoutBrowserContainer.findViewById(R.id.txt_tab_switcher_count)
        btnTabsCloseAll = layoutBrowserContainer.findViewById(R.id.btn_tabs_close_all)
        btnTabsAddNew = layoutBrowserContainer.findViewById(R.id.btn_tabs_add_new)
        recyclerTabs = layoutBrowserContainer.findViewById(R.id.recycler_tabs)
        layoutEmptyTabs = layoutBrowserContainer.findViewById(R.id.layout_empty_tabs)
        btnTabsEmptyNew = layoutBrowserContainer.findViewById(R.id.btn_tabs_empty_new)

        // Bottom toolbar
        btnWebBack = layoutBrowserContainer.findViewById(R.id.btn_web_back)
        btnWebForward = layoutBrowserContainer.findViewById(R.id.btn_web_forward)
        btnWebRefresh = layoutBrowserContainer.findViewById(R.id.btn_web_refresh)
        btnWebHome = layoutBrowserContainer.findViewById(R.id.btn_web_home)
        btnWebTabs = layoutBrowserContainer.findViewById(R.id.btn_web_tabs)
        txtTabCountBadge = layoutBrowserContainer.findViewById(R.id.txt_tab_count_badge)

        setupTabRecyclerView()

        // Initialize Tab 1 using the initial WebView from layout
        val initialWv = layoutWebviewContainer.findViewById<WebView>(R.id.webview_browser)
        val firstTab = BrowserTab(
            title = "Google",
            url = DEFAULT_HOME_URL,
            webView = initialWv
        )
        setupWebView(initialWv, firstTab)
        tabsList.add(firstTab)
        activeTabIndex = 0
        updateTabCountBadge()

        setupListeners()
        setupDragToMove()
        applyAdaptiveDimensions()
        updateNavigationButtonsState()
    }

    private fun setupTabRecyclerView() {
        recyclerTabs.layoutManager = GridLayoutManager(service, 2)
        tabsAdapter = TabsAdapter(
            tabs = tabsList,
            activeTabIndex = activeTabIndex,
            onTabClick = { pos -> selectTab(pos) },
            onCloseClick = { pos -> closeTab(pos) }
        )
        recyclerTabs.adapter = tabsAdapter

        // Enable swipe to delete on tab cards!
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < tabsList.size) {
                    closeTab(position)
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerTabs)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(wv: WebView, tab: BrowserTab) {
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        wv.keepScreenOn = true

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = true
            allowContentAccess = true

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
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
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
                tab.url = url ?: ""
                if (tab == activeTab) {
                    isLoading = true
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 10
                    btnWebRefresh.setImageResource(R.drawable.ic_close)
                    layoutError.visibility = View.GONE

                    if (!edtUrl.hasFocus() && !url.isNullOrEmpty()) {
                        edtUrl.setText(url)
                    }
                    updateNavigationButtonsState()
                }
                injectMediaKeepAliveScript(wv)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tab.url = view?.url ?: url ?: ""
                val pageTitle = view?.title
                if (!pageTitle.isNullOrBlank()) {
                    tab.title = pageTitle
                }

                if (tab == activeTab) {
                    isLoading = false
                    progressBar.visibility = View.GONE
                    btnWebRefresh.setImageResource(R.drawable.ic_refresh)

                    if (!isTabSwitcherOpen) {
                        txtTitle.text = if (!pageTitle.isNullOrBlank()) pageTitle else "Web Browser"
                    }

                    if (!edtUrl.hasFocus() && !url.isNullOrEmpty()) {
                        edtUrl.setText(url)
                    }
                    updateNavigationButtonsState()
                }

                injectMediaKeepAliveScript(wv)
                captureTabThumbnail(tab)

                if (isTabSwitcherOpen) {
                    val idx = tabsList.indexOf(tab)
                    if (idx != -1) {
                        tabsAdapter?.notifyItemChanged(idx)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tab.lastFailedUrl = request.url?.toString()
                    if (tab == activeTab) {
                        layoutError.visibility = View.VISIBLE
                        txtErrorMsg.text = error?.description?.toString() ?: "Connection error"
                    }
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
                    tab.lastFailedUrl = failingUrl
                    if (tab == activeTab) {
                        layoutError.visibility = View.VISIBLE
                        txtErrorMsg.text = description ?: "Connection error"
                    }
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (tab == activeTab) {
                    progressBar.progress = newProgress
                    if (newProgress >= 100) {
                        progressBar.visibility = View.GONE
                    } else {
                        progressBar.visibility = View.VISIBLE
                    }
                }
                if (newProgress >= 40) {
                    injectMediaKeepAliveScript(wv)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    if (tab == activeTab && !isTabSwitcherOpen) {
                        txtTitle.text = title
                    }
                    if (isTabSwitcherOpen) {
                        val idx = tabsList.indexOf(tab)
                        if (idx != -1) {
                            tabsAdapter?.notifyItemChanged(idx)
                        }
                    }
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
                wv.visibility = View.GONE
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
                wv.visibility = View.VISIBLE
            }

            // File upload & Google Lens support
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false
                return openFileChooser(filePathCallback, fileChooserParams)
            }

            // HTML5 Camera / Microphone permissions (WebRTC / live camera)
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val resources = request.resources
                var canGrant = true
                if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    val hasCamera = ContextCompat.checkSelfPermission(
                        service,
                        android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasCamera) canGrant = false
                }
                if (canGrant) {
                    request.grant(resources)
                } else {
                    request.deny()
                }
            }
        }
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        WebViewFileChooserActivity.cancelPendingCallback()
        WebViewFileChooserActivity.currentCallback = callback
        WebViewFileChooserActivity.currentParams = params
        return try {
            val intent = Intent(service, WebViewFileChooserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            WebViewFileChooserActivity.cancelPendingCallback()
            false
        }
    }

    private fun createWebView(): WebView {
        val themedContext = ContextThemeWrapper(service, R.style.Theme_Assistive)
        val wv = WebView(themedContext)
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        wv.isVerticalScrollBarEnabled = true
        return wv
    }

    fun addNewTab(url: String = DEFAULT_HOME_URL) {
        // Save thumbnail and pause current tab
        activeTab?.let {
            captureTabThumbnail(it)
            it.webView.onPause()
            layoutWebviewContainer.removeView(it.webView)
        }

        val newWv = createWebView()
        val newTab = BrowserTab(
            title = "New Tab",
            url = url,
            webView = newWv
        )
        setupWebView(newWv, newTab)
        tabsList.add(newTab)
        activeTabIndex = tabsList.size - 1

        // Add WebView at index 0 so unhide button and error view stay on top
        layoutWebviewContainer.addView(newWv, 0)
        newWv.onResume()
        newWv.loadUrl(url)

        updateTabCountBadge()

        if (isTabSwitcherOpen) {
            closeTabSwitcher()
        } else {
            txtTitle.text = newTab.title
            edtUrl.setText(newTab.url)
            updateNavigationButtonsState()
        }
    }

    fun selectTab(index: Int) {
        if (index !in tabsList.indices) return

        if (index == activeTabIndex) {
            if (isTabSwitcherOpen) closeTabSwitcher()
            return
        }

        // Save thumbnail and detach current tab
        activeTab?.let {
            captureTabThumbnail(it)
            it.webView.onPause()
            layoutWebviewContainer.removeView(it.webView)
        }

        activeTabIndex = index
        val selectedTab = tabsList[index]
        if (selectedTab.webView.parent == null) {
            layoutWebviewContainer.addView(selectedTab.webView, 0)
        }
        selectedTab.webView.onResume()

        if (isTabSwitcherOpen) {
            closeTabSwitcher()
        } else {
            txtTitle.text = if (selectedTab.title.isNotBlank()) selectedTab.title else "Web Browser"
            edtUrl.setText(selectedTab.url)
            updateNavigationButtonsState()
            layoutError.visibility = if (selectedTab.lastFailedUrl != null) View.VISIBLE else View.GONE
        }
    }

    fun closeTab(index: Int) {
        if (index !in tabsList.indices) return

        val tabToClose = tabsList[index]
        val isClosingActive = (index == activeTabIndex)

        // Clean up WebView
        try {
            tabToClose.webView.stopLoading()
            tabToClose.webView.loadUrl("about:blank")
            tabToClose.webView.clearHistory()
            (tabToClose.webView.parent as? ViewGroup)?.removeView(tabToClose.webView)
            tabToClose.webView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tabToClose.thumbnail?.recycle()
        tabToClose.thumbnail = null

        tabsList.removeAt(index)

        if (tabsList.isEmpty()) {
            activeTabIndex = 0
            updateTabCountBadge()
            if (isTabSwitcherOpen) {
                txtTabSwitcherCount.text = "Tabs (0)"
                layoutEmptyTabs.visibility = View.VISIBLE
                recyclerTabs.visibility = View.GONE
                tabsAdapter?.notifyDataSetChanged()
            } else {
                addNewTab(DEFAULT_HOME_URL)
            }
            return
        }

        if (isClosingActive) {
            activeTabIndex = (index - 1).coerceAtLeast(0).coerceAtMost(tabsList.size - 1)
            val newActive = tabsList[activeTabIndex]
            if (!isTabSwitcherOpen) {
                if (newActive.webView.parent == null) {
                    layoutWebviewContainer.addView(newActive.webView, 0)
                }
                newActive.webView.onResume()
                txtTitle.text = if (newActive.title.isNotBlank()) newActive.title else "Web Browser"
                edtUrl.setText(newActive.url)
                updateNavigationButtonsState()
                layoutError.visibility = if (newActive.lastFailedUrl != null) View.VISIBLE else View.GONE
            }
        } else if (index < activeTabIndex) {
            activeTabIndex--
        }

        updateTabCountBadge()

        if (isTabSwitcherOpen) {
            txtTabSwitcherCount.text = "Tabs (${tabsList.size})"
            tabsAdapter?.updateActiveIndex(activeTabIndex)
            tabsAdapter?.notifyItemRemoved(index)
            tabsAdapter?.notifyItemRangeChanged(index, tabsList.size - index)
        }
    }

    fun closeAllTabs() {
        for (tab in tabsList) {
            try {
                tab.webView.stopLoading()
                tab.webView.loadUrl("about:blank")
                tab.webView.clearHistory()
                (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
                tab.webView.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tab.thumbnail?.recycle()
            tab.thumbnail = null
        }
        tabsList.clear()
        activeTabIndex = 0
        updateTabCountBadge()
        txtTabSwitcherCount.text = "Tabs (0)"
        layoutEmptyTabs.visibility = View.VISIBLE
        recyclerTabs.visibility = View.GONE
        tabsAdapter?.notifyDataSetChanged()
    }

    private fun openTabSwitcher() {
        if (isTabSwitcherOpen) return
        isTabSwitcherOpen = true
        hideKeyboard()

        // Capture snapshot of current tab
        activeTab?.let {
            captureTabThumbnail(it)
        }

        layoutBrowserOmnibox.visibility = View.GONE
        progressBar.visibility = View.GONE
        layoutWebviewContainer.visibility = View.GONE
        layoutTabSwitcher.visibility = View.VISIBLE

        txtTitle.text = "Tabs (${tabsList.size})"
        txtTabSwitcherCount.text = "Tabs (${tabsList.size})"

        layoutEmptyTabs.visibility = if (tabsList.isEmpty()) View.VISIBLE else View.GONE
        recyclerTabs.visibility = if (tabsList.isEmpty()) View.GONE else View.VISIBLE

        txtTabCountBadge.setBackgroundResource(R.drawable.bg_tab_badge_active)
        txtTabCountBadge.setTextColor(0xFF4FC3F7.toInt())

        tabsAdapter?.updateActiveIndex(activeTabIndex)
        tabsAdapter?.notifyDataSetChanged()
    }

    private fun closeTabSwitcher() {
        if (!isTabSwitcherOpen) return
        if (tabsList.isEmpty()) {
            addNewTab(DEFAULT_HOME_URL)
            return
        }

        isTabSwitcherOpen = false
        layoutTabSwitcher.visibility = View.GONE
        layoutBrowserOmnibox.visibility = View.VISIBLE
        layoutWebviewContainer.visibility = View.VISIBLE

        txtTabCountBadge.setBackgroundResource(R.drawable.bg_tab_badge)
        txtTabCountBadge.setTextColor(0xFFFFFFFF.toInt())

        val current = activeTab
        if (current != null) {
            if (current.webView.parent == null) {
                layoutWebviewContainer.addView(current.webView, 0)
            }
            current.webView.onResume()
            txtTitle.text = if (current.title.isNotBlank()) current.title else "Web Browser"
            edtUrl.setText(current.url)
            updateNavigationButtonsState()
            layoutError.visibility = if (current.lastFailedUrl != null) View.VISIBLE else View.GONE
        }
    }

    private fun updateTabCountBadge() {
        txtTabCountBadge.text = tabsList.size.toString()
    }

    private fun captureTabThumbnail(tab: BrowserTab) {
        try {
            val wv = tab.webView
            val w = wv.width
            val h = wv.height
            if (w > 0 && h > 0) {
                val scale = (240f / w).coerceAtMost(1f)
                val thumbW = (w * scale).toInt().coerceAtLeast(1)
                val thumbH = (h * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(scale, scale)
                wv.draw(canvas)
                tab.thumbnail = bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Injects JavaScript into the web page to prevent YouTube / YouTube Shorts from pausing
     * automatically during window blur, layout resize, or background checks.
     */
    private fun injectMediaKeepAliveScript(targetWebView: WebView? = activeTab?.webView) {
        val wv = targetWebView ?: return
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
        wv.evaluateJavascript(js, null)
    }

    private fun setupListeners() {
        // Back button in header: closes tab switcher if open, otherwise exits back to floating menu
        btnBackToMenu.setOnClickListener {
            if (isTabSwitcherOpen) {
                closeTabSwitcher()
            } else {
                closeBrowserPanel()
            }
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
            val current = activeTab ?: return@setOnClickListener
            if (!current.lastFailedUrl.isNullOrEmpty()) {
                current.webView.loadUrl(current.lastFailedUrl!!)
            } else {
                current.webView.reload()
            }
        }

        // Tab Switcher Header Actions
        btnTabsAddNew.setOnClickListener {
            addNewTab(DEFAULT_HOME_URL)
        }

        btnTabsCloseAll.setOnClickListener {
            closeAllTabs()
        }

        btnTabsEmptyNew.setOnClickListener {
            addNewTab(DEFAULT_HOME_URL)
        }

        // Navigation toolbar actions
        btnWebBack.setOnClickListener {
            if (isTabSwitcherOpen) {
                closeTabSwitcher()
                return@setOnClickListener
            }
            val wv = activeTab?.webView
            if (wv?.canGoBack() == true) {
                wv.goBack()
            } else {
                Toast.makeText(service, "No earlier page", Toast.LENGTH_SHORT).show()
            }
        }

        btnWebForward.setOnClickListener {
            if (!isTabSwitcherOpen && activeTab?.webView?.canGoForward() == true) {
                activeTab?.webView?.goForward()
            }
        }

        btnWebRefresh.setOnClickListener {
            if (isTabSwitcherOpen) return@setOnClickListener
            val wv = activeTab?.webView ?: return@setOnClickListener
            if (isLoading) {
                wv.stopLoading()
            } else {
                wv.reload()
            }
        }

        btnWebHome.setOnClickListener {
            if (isTabSwitcherOpen) {
                closeTabSwitcher()
            }
            loadSearchOrUrl(DEFAULT_HOME_URL)
        }

        // Tabs button: toggles between webview and tab grid view
        btnWebTabs.setOnClickListener {
            if (isTabSwitcherOpen) {
                closeTabSwitcher()
            } else {
                openTabSwitcher()
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

        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun showToolbars() {
        areBarsHidden = false
        layoutBrowserHeader.visibility = View.VISIBLE
        if (!isTabSwitcherOpen) {
            layoutBrowserOmnibox.visibility = View.VISIBLE
        }
        layoutBrowserBottomToolbar.visibility = View.VISIBLE
        btnUnhide.visibility = View.GONE

        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun resumeAllVideos() {
        val wv = activeTab?.webView ?: return
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
        wv.evaluateJavascript(js, null)
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
        val current = activeTab ?: return
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

        current.lastFailedUrl = null
        current.url = targetUrl
        layoutError.visibility = View.GONE
        edtUrl.setText(targetUrl)
        current.webView.loadUrl(targetUrl)
    }

    fun openBrowser() {
        service.setMenuFocusable(true)
        applyAdaptiveDimensions()
        repositionBrowserWindow()

        if (tabsList.isEmpty()) {
            addNewTab(DEFAULT_HOME_URL)
        } else {
            if (isTabSwitcherOpen) {
                closeTabSwitcher()
            }
            activeTab?.webView?.onResume()
            activeTab?.webView?.resumeTimers()
            if (activeTab?.webView?.url.isNullOrEmpty()) {
                loadSearchOrUrl(DEFAULT_HOME_URL)
            }
            updateNavigationButtonsState()
        }
    }

    fun onConfigurationChanged() {
        applyAdaptiveDimensions()
        repositionBrowserWindow()
        injectMediaKeepAliveScript()
        resumeAllVideos()
    }

    private fun closeBrowserPanel() {
        WebViewFileChooserActivity.cancelPendingCallback()
        hideKeyboard()
        service.setMenuFocusable(false)
        showToolbars()

        if (isTabSwitcherOpen) {
            closeTabSwitcher()
        }

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
            // Landscape: Optimal ratio leaving ample screen space
            val maxAllowedH = (screenHeightDp - 48).coerceAtLeast(200)
            if (isExpanded) {
                val targetW = (screenWidthDp * 0.50f).toInt().coerceIn(360, 440)
                val targetH = (screenHeightDp - 32).coerceIn(240, (screenHeightDp - 24).coerceAtLeast(220))
                WindowDimensions(targetW, targetH)
            } else {
                val targetW = (screenWidthDp * 0.38f).toInt().coerceIn(300, 350)
                val targetH = (screenHeightDp * 0.72f).toInt().coerceIn(210, maxAllowedH)
                WindowDimensions(targetW, targetH)
            }
        } else {
            // Portrait
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
        val totalWidthPx = ((dims.widthDp + 24) * density).toInt()
        val totalHeightPx = ((dims.heightDp + 24) * density).toInt()

        val isLandscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                || displayMetrics.widthPixels > displayMetrics.heightPixels

        var targetX = service.menuParams.x
        var targetY = service.menuParams.y

        if (isLandscape) {
            val verticalSlack = (screenHeight - totalHeightPx).coerceAtLeast(0)
            targetY = (verticalSlack / 2).coerceIn(
                edgePadPx,
                (screenHeight - totalHeightPx - edgePadPx).coerceAtLeast(edgePadPx)
            )
            val maxX = (screenWidth - totalWidthPx - edgePadPx).coerceAtLeast(edgePadPx)
            targetX = targetX.coerceIn(edgePadPx, maxX)
        } else {
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
        val wv = activeTab?.webView
        val canGoBack = wv?.canGoBack() == true
        val canGoForward = wv?.canGoForward() == true

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
            for (tab in tabsList) {
                try {
                    tab.webView.stopLoading()
                    tab.webView.loadUrl("about:blank")
                    tab.webView.clearHistory()
                    (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
                    tab.webView.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                tab.thumbnail?.recycle()
                tab.thumbnail = null
            }
            tabsList.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
