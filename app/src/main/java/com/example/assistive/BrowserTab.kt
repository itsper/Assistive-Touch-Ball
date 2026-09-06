package com.example.assistive

import android.graphics.Bitmap
import android.webkit.WebView
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "https://www.google.com",
    val webView: WebView,
    var thumbnail: Bitmap? = null,
    var lastFailedUrl: String? = null
)
