/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on effective License.
 * Set the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.notizen

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.google.android.samples.notizen.ui.theme.NotizenTheme
import java.util.UUID

data class TabInstance(
    val id: String = UUID.randomUUID().toString(),
    val webView: WebView,
    val title: MutableState<String> = mutableStateOf("Loading..."),
    val url: MutableState<String>
)

class MainActivity : ComponentActivity() {
    private var activeWebView: WebView? = null
    private val providerAuthTab = ProviderAuthTab(this)
    private lateinit var notificationBridge: NotificationBridge
    private var notificationNote by mutableStateOf<String?>(null)

    private fun handleNotificationIntent(intent: Intent) {
        if (intent.action != NotificationBridge.OPEN_NOTE) return
        val id = intent.getStringExtra(NotificationBridge.NOTE_ID) ?: return
        if (NotificationBridge.validNoteId(id)) notificationNote = id
        intent.removeExtra(NotificationBridge.NOTE_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationBridge = NotificationBridge(this) { view ->
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                view === activeWebView && view.isShown && view.hasWindowFocus()
        }
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        showWebsite(url = "https://notizen.dev", enableTabs = true)
    }

    override fun onResume() {
        super.onResume()
        // WebView background execution is not guaranteed. Wake the web-owned outbox on return.
        activeWebView?.evaluateJavascript(
            "window.dispatchEvent(new Event('notizen-resume'));",
            null
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun showWebsite(url: String, enableTabs: Boolean) {
        setContent {
            NotizenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        initialUrl = url,
                        enableTabs = enableTabs,
                        onWebViewCreated = { activeWebView = it },
                        initializeWebView = { notificationBridge.install(it) },
                        notificationNote = notificationNote,
                        consumeNotification = { notificationNote = null },
                        modifier = Modifier.padding(innerPadding).fillMaxSize()
                    )
                }
            }
        }
    }

    fun openProviderAuthTab(view: WebView, uri: Uri): Boolean = providerAuthTab.open(view, uri)

    override fun onStop() {
        // Persist session refreshes/logout from SPA requests before the process can be killed.
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onDestroy() {
        providerAuthTab.clear()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    initialUrl: String,
    enableTabs: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    initializeWebView: (WebView) -> Unit,
    notificationNote: String?,
    consumeNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tabState = remember { mutableStateListOf<TabInstance>() }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    val isOfflineState = remember { mutableStateOf(false) }

    fun createAndAddTab(url: String = initialUrl) {
        var newTab: TabInstance? = null
        val webView = createWebView(
            context = context,
            initialUrl = url,
            onTitleReceived = { title -> newTab?.title?.value = title },
            isOfflineState = isOfflineState,
            initialize = initializeWebView
        )
        val tab = TabInstance(webView = webView, url = mutableStateOf(url))
        newTab = tab
        tabState.add(tab)
        activeTabIndex = tabState.lastIndex
    }

    fun removeTab(tab: TabInstance) {
        val index = tabState.indexOf(tab)
        if (index != -1) {
            tabState.removeAt(index)
            if (tabState.isEmpty()) {
                createAndAddTab()
            } else if (activeTabIndex >= tabState.size) {
                activeTabIndex = tabState.size - 1
            }
        }
    }

    LaunchedEffect(Unit) {
        if (tabState.isEmpty()) {
            createAndAddTab()
        }
    }

    LaunchedEffect(notificationNote) {
        notificationNote?.let { id ->
            val target = "${NotificationBridge.ORIGIN}/notes/$id"
            val existing = tabState.indexOfFirst { it.webView.url == target }
            if (existing >= 0) activeTabIndex = existing else createAndAddTab(target)
            consumeNotification()
        }
    }

    LaunchedEffect(activeTabIndex, tabState.size) {
        if (tabState.isNotEmpty() && activeTabIndex in tabState.indices) {
            onWebViewCreated(tabState[activeTabIndex].webView)
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (enableTabs) {
                TabBar(
                    tabState = tabState,
                    activeTabIndex = activeTabIndex,
                    onTabClick = { activeTabIndex = it },
                    onCloseClick = { removeTab(it) },
                    onAddClick = { createAndAddTab() },
                    modifier = Modifier
                )
            }

            WebViewContainer(
                tabState = tabState,
                activeTabIndex = activeTabIndex,
                modifier = Modifier.weight(1f)
            )
        }

        if (isOfflineState.value) {
            NoInternetScreen(onRetry = {
                if (tabState.isNotEmpty() && activeTabIndex in tabState.indices) {
                    tabState[activeTabIndex].webView.reload()
                }
            })
        }
    }
}

/**
 * Shared WebViewClient for page state and the Auth Tab experiment.
 */
open class NotizenWebViewClient(
    private val context: Context,
    private val isOfflineState: MutableState<Boolean>? = null
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        isOfflineState?.value = false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (AuthTabContract.trustedPage(url)) {
            // The Auth.js callback sets the session cookie in WebView and redirects to /notes.
            // Save those completed network cookie writes, not just the pre-login PKCE cookie.
            CookieManager.getInstance().flush()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val code = error?.errorCode
            val isNoInternet = code == ERROR_HOST_LOOKUP ||
                               code == ERROR_CONNECT ||
                               code == ERROR_TIMEOUT
            if (isNoInternet) {
                isOfflineState?.value = true
            }
        }
    }

    // Auth.js begins in WebView, so its CSRF/PKCE cookies stay there.
    // Only the provider's authorization UI moves to Auth Tab.
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (view == null || request == null || !request.isForMainFrame) return false
        if (!AuthTabContract.isProviderAuthorization(request.url)) return false
        return (context as? MainActivity)?.openProviderAuthTab(view, request.url) ?: true
    }

}

fun applyBaseWebViewSettings(webView: WebView, context: Context) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        setSupportMultipleWindows(true)
        mediaPlaybackRequiresUserGesture = false
        userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        useWideViewPort = true
        loadWithOverviewMode = true
    }

    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        val request = DownloadManager.Request(url.toUri())
        request.setMimeType(mimetype)
        val cookies = CookieManager.getInstance().getCookie(url)
        request.addRequestHeader("cookie", cookies)
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Downloading file...")
        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Downloading...", Toast.LENGTH_LONG).show()
    }
}

fun createWebView(
    context: Context,
    initialUrl: String,
    onTitleReceived: (String) -> Unit = {},
    isOfflineState: MutableState<Boolean>? = null,
    initialize: (WebView) -> Unit = {}
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        applyBaseWebViewSettings(this, context)

        webViewClient = NotizenWebViewClient(context, isOfflineState)

        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onTitleReceived(it) }
            }

            /**
             * Required to support websites that open popups or new tabs (e.g. window.open() or target="_blank").
             * Many modern OAuth workflows spawn inside secondary windows.
             * We intercept this by creating a new WebView inside a Dialog.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(context)
                applyBaseWebViewSettings(newWebView, context)

                val dialog = Dialog(context)
                dialog.setContentView(newWebView)
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                dialog.show()

                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                    override fun onCloseWindow(window: WebView?) {
                        dialog.dismiss()
                    }
                }

                newWebView.webViewClient = NotizenWebViewClient(context)

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        initialize(this)
        loadUrl(initialUrl)
    }
}

@Composable
fun NoInternetScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No Internet Connection", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Text("Please check your network settings.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            androidx.compose.material3.Button(onClick = { onRetry() }) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun TabBar(
    tabState: List<TabInstance>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onCloseClick: (TabInstance) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFEEEEEE))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tabState) { index, tab ->
            val isSelected = index == activeTabIndex
            Card(
                onClick = { onTabClick(index) },
                modifier = Modifier.width(140.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFE5E1DC) else Color.White
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tab.title.value,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                    IconButton(
                        onClick = { onCloseClick(tab) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        item {
            IconButton(onClick = onAddClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Tab", tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun WebViewContainer(
    tabState: List<TabInstance>,
    activeTabIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        tabState.forEachIndexed { index, tab ->
            key(tab.id) {
                val isVisible = index == activeTabIndex
                AndroidView(
                    factory = { tab.webView },
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = if (isVisible) 0.dp else 10000.dp)
                )
            }
        }
    }
}
