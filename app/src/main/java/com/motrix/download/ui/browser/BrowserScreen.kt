package com.motrix.download.ui.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.motrix.download.R
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onUrlPaste: (String) -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val downloadCapturedText = stringResource(R.string.browser_download_captured)
    val unsupportedDownloadText = stringResource(R.string.browser_unsupported_download)

    fun sendToDownloader(downloadUrl: String) {
        val cleanUrl = downloadUrl.trim()
        if (cleanUrl.startsWith("blob:", ignoreCase = true)) {
            Toast.makeText(context, unsupportedDownloadText, Toast.LENGTH_LONG).show()
            return
        }
        if (cleanUrl.isNotEmpty()) {
            Toast.makeText(context, downloadCapturedText, Toast.LENGTH_SHORT).show()
            onUrlPaste(cleanUrl)
        }
    }

    fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) return trimmed
        return if (trimmed.contains('.') && !trimmed.any { it.isWhitespace() }) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }

    fun isDownloadLike(link: String): Boolean {
        val lower = link.lowercase()
        return lower.startsWith("magnet:") ||
            listOf(".apk", ".zip", ".rar", ".7z", ".tar", ".gz", ".iso", ".mp4", ".mkv", ".mp3", ".pdf", ".torrent")
                .any { lower.substringBefore('?').substringBefore('#').endsWith(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browser_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.browser_enter_url)) }
                        )
                        IconButton(onClick = {
                            val fixedUrl = normalizeInput(url)
                            currentUrl = fixedUrl
                            pageError = null
                            webView?.loadUrl(fixedUrl)
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Go")
                        }
                        IconButton(onClick = {
                            onUrlPaste(currentUrl.ifEmpty { url })
                        }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Download")
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                        IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        IconButton(onClick = { webView?.stopLoading() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (progress < 100) {
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
            pageError?.let {
                AssistChip(
                    onClick = { pageError = null },
                    label = { Text(it) },
                    leadingIcon = { Icon(Icons.Default.ErrorOutline, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = settings.userAgentString +
                            " MotrixAndroid/1.0"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString().orEmpty()
                                if (targetUrl.isBlank()) return false

                                if (targetUrl.startsWith("blob:", ignoreCase = true)) {
                                    Toast.makeText(context, unsupportedDownloadText, Toast.LENGTH_LONG).show()
                                    return true
                                }

                                if (targetUrl.startsWith("magnet:", ignoreCase = true) || isDownloadLike(targetUrl)) {
                                    sendToDownloader(targetUrl)
                                    return true
                                }

                                if (targetUrl.startsWith("intent:", ignoreCase = true)) {
                                    return try {
                                        val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                        val fallback = intent.getStringExtra("browser_fallback_url")
                                        if (!fallback.isNullOrBlank()) {
                                            view?.loadUrl(fallback)
                                        } else {
                                            context.startActivity(intent)
                                        }
                                        true
                                    } catch (_: ActivityNotFoundException) {
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }

                                val scheme = request?.url?.scheme.orEmpty()
                                if (scheme !in listOf("http", "https", "about")) {
                                    return try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }

                                return false
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                currentUrl = url ?: ""
                                pageError = null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = error?.description?.toString()
                                        ?: context.getString(R.string.error_connection)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                            sendToDownloader(downloadUrl)
                        }

                        loadUrl(currentUrl.ifEmpty { url })
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
