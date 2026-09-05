package com.example.assistive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
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
        private const val NORMAL_WIDTH_DP = 290
        private const val NORMAL_HEIGHT_DP = 380
        private const val EXPANDED_WIDTH_DP = 340
        private const val EXPANDED_HEIGHT_DP = 500
    }

    // Containers
    private lateinit var layoutBrowserContainer: FrameLayout
    private lateinit var layoutError: View

    // Header elements
    private lateinit var btnBackToMenu: ImageButton
    private lateinit var txtTitle: TextView
    private lateinit var btnSizeToggle: ImageButton
    private lateinit var btnClose: ImageButton

    // Omnibox / Search elements
    private lateinit var edtUrl: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnGo: TextView
    private lateinit var progressBar: ProgressBar

    // WebView
    private lateinit var webView: WebView
    private lateinit var txtErrorMsg: TextView
    private lateinit var btnRetry: TextView

    // Bottom Navigation toolbar
    private lateinit var btnWebBack: ImageButton
    private lateinit var btnWebForward: ImageButton
    private lateinit var btnWebRefresh: ImageButton
    private lateinit var btnWebHome: ImageButton
    private lateinit var btnWebOpenExternal: ImageButton

    // State
    private var isExpanded = false
    private var isLoading = false
    private var lastFailedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        layoutBrowserContainer = menuView.findViewById(R.id.layout_browser_container)

        // Header
        btnBackToMenu = layoutBrowserContainer.findViewById(R.id.btn_browser_back_to_menu)
        txtTitle = layoutBrowserContainer.findViewById(R.id.txt_browser_title)
        btnSizeToggle = layoutBrowserContainer.findViewById(R.id.btn_browser_size_toggle)
        btnClose = layoutBrowserContainer.findViewById(R.id.btn_browser_close)

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
        updateNavigationButtonsState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
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
                // External protocol intents (tel:, mailto:, market:, etc.)
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
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    txtTitle.text = title
                }
            }
        }
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
        clampWindowToScreen()

        if (webView.url.isNullOrEmpty()) {
            loadSearchOrUrl(DEFAULT_HOME_URL)
        }
        updateNavigationButtonsState()
    }

    private fun closeBrowserPanel() {
        hideKeyboard()
        service.setMenuFocusable(false)
        layoutBrowserContainer.visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
    }

    private fun toggleWindowSize() {
        isExpanded = !isExpanded
        val density = service.resources.displayMetrics.density
        val targetW = if (isExpanded) EXPANDED_WIDTH_DP else NORMAL_WIDTH_DP
        val targetH = if (isExpanded) EXPANDED_HEIGHT_DP else NORMAL_HEIGHT_DP

        val params = layoutBrowserContainer.layoutParams
        params.width = (targetW * density).toInt()
        params.height = (targetH * density).toInt()
        layoutBrowserContainer.layoutParams = params

        btnSizeToggle.setImageResource(if (isExpanded) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
        clampWindowToScreen()
    }

    private fun clampWindowToScreen() {
        val displayMetrics = service.resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val padPx = (8 * density).toInt()

        val currentWidthDp = if (isExpanded) EXPANDED_WIDTH_DP else NORMAL_WIDTH_DP
        val currentHeightDp = if (isExpanded) EXPANDED_HEIGHT_DP else NORMAL_HEIGHT_DP
        val targetWidthPx = ((currentWidthDp + 24) * density).toInt()
        val targetHeightPx = ((currentHeightDp + 24) * density).toInt()

        var newX = service.menuParams.x
        var newY = service.menuParams.y

        if (newX + targetWidthPx > screenWidth - padPx) {
            newX = screenWidth - targetWidthPx - padPx
        }
        if (newX < padPx) newX = padPx

        if (newY + targetHeightPx > screenHeight - padPx) {
            newY = screenHeight - targetHeightPx - padPx
        }
        if (newY < padPx) newY = padPx

        service.menuParams.x = newX
        service.menuParams.y = newY
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
