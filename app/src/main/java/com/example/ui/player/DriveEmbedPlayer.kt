package com.example.ui.player

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays shared Google Drive video preview links cleanly inside a WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DriveEmbedPlayer(
    previewUrl: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(previewUrl) {
        val safeUrl = previewUrl.trim()
        if (safeUrl.startsWith("https://drive.google.com/")) {
            webViewRef?.loadUrl(safeUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF0A0B10.toInt())
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                if (previewUrl.startsWith("https://drive.google.com/")) {
                    loadUrl(previewUrl)
                }
                webViewRef = this
            }
        }
    )
}
