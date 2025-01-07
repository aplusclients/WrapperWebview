package com.example.wrapperwebview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wrapperwebview.ui.theme.WrapperWebviewTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material3.ExperimentalMaterial3Api

// fullscreen
import android.webkit.WebChromeClient
import android.view.View
import android.widget.FrameLayout

import android.util.Log

class MainActivity : ComponentActivity() {
    var lastUrl = ""
    private val downloadViewModel by viewModels<DownloadViewModel>()

    companion object {
        const val INITIAL_URL = "https://seashell-app-gxd5i.ondigitalocean.app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastUrl = INITIAL_URL
        setContent {
            WrapperWebviewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box {
                        WebViewScreen(url = lastUrl, downloadViewModel = downloadViewModel)
                        DownloadStatusOverlayScreen(downloadViewModel = downloadViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStatusOverlayScreen(
    modifier: Modifier = Modifier,
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val downloadState = downloadViewModel.downloadState.collectAsStateWithLifecycle().value
    
    if (downloadState != DownloadState.Idle) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Downloading ${state.fileName}...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.contentLength > 0) {
                                LinearProgressIndicator(
                                    progress = state.progress / 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(
                                onClick = { downloadViewModel.hideDownload() }
                            ) {
                                Text("Hide")
                            }
                        }
                    }
                }
                is DownloadState.Complete -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Download Complete",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                state.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                Button(
                                    onClick = {
                                        state.onInstall()
                                        downloadViewModel.hideDownload()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Install Now")
                                }
                                TextButton(
                                    onClick = { downloadViewModel.hideDownload() }
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
                is DownloadState.Failed -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Download Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                state.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = { downloadViewModel.hideDownload() }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                is DownloadState.Idle -> { /* Nothing to show */ }
            }
        }
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val fileName: String,
        val progress: Int,
        val contentLength: Long
    ) : DownloadState()
    
    data class Complete(
        val fileName: String,
        val onInstall: () -> Unit
    ) : DownloadState()
    
    data class Failed(
        val fileName: String
    ) : DownloadState()
}

class DownloadViewModel : ViewModel() {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()
    
    fun startDownload(fileName: String, contentLength: Long) {
        _downloadState.value = DownloadState.Downloading(fileName, 0, contentLength)
    }
    
    fun updateProgress(progress: Int) {
        val currentState = _downloadState.value
        if (currentState is DownloadState.Downloading) {
            _downloadState.value = currentState.copy(progress = progress)
        }
    }
    
    fun completeDownload(fileName: String, onInstall: () -> Unit) {
        _downloadState.value = DownloadState.Complete(fileName, onInstall)
    }
    
    fun failDownload(fileName: String) {
        _downloadState.value = DownloadState.Failed(fileName)
    }
    
    fun hideDownload() {
        _downloadState.value = DownloadState.Idle
    }
}

@Composable
fun WebViewScreen(url: String, downloadViewModel: DownloadViewModel) {

    val allowlist = listOf(
        "https://seashell-app-gxd5i.ondigitalocean.app",
        "https://f-droid.org",
        "https://www.allowedsite.com",
        "https://allowedsite.com"
    )

    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val activity = context as? MainActivity
    
    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            (context as? Activity)?.finish()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    // Configure WebView settings safely
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    }

                    // Enable cookies
                    CookieManager.getInstance().also { cookieManager ->
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }
                    
                    // Enable swipe to go back
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                v.parent.requestDisallowInterceptTouchEvent(true)
                                false
                            }
                            MotionEvent.ACTION_UP -> {
                                v.parent.requestDisallowInterceptTouchEvent(false)
                                false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (canGoBack() && event.getX() > v.width * 0.8f) {
                                    goBack()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }

                    // Enable fullscreen video handling
                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        private var customViewCallback: CustomViewCallback? = null
                        private var originalSystemUiVisibility: Int = 0

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (customView != null) {
                                onHideCustomView()
                                return
                            }

                            // Save original UI visibility
                            originalSystemUiVisibility = (context as ComponentActivity).window.decorView.systemUiVisibility
                            customView = view
                            customViewCallback = callback

                            // Add the custom view to the activity's content view
                            (context.window.decorView as FrameLayout).addView(
                                customView,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )

                            // Hide system UI for fullscreen
                            context.window.decorView.systemUiVisibility =
                                View.SYSTEM_UI_FLAG_FULLSCREEN or
                                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        }

                        override fun onHideCustomView() {
                            // Remove custom view and restore original UI visibility
                            (context as ComponentActivity).window.decorView.systemUiVisibility = originalSystemUiVisibility
                            (context.window.decorView as FrameLayout).removeView(customView)
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                        }
                    }

                    // Configure WebViewClient to handle loading errors
                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url.toString()
                            val isAllowed = allowlist.any { url.startsWith(it) }
                            
                            return if (url.endsWith("/downloads/")) {
                                // open the android file list screen when the websites url ends with /downloads
                                (view?.context as? ComponentActivity)?.setContent {
                                    WrapperWebviewTheme {
                                        DownloadedFilesScreen(context = view.context, downloadViewModel = downloadViewModel)
                                    }
                                }
                                true
                            } else if (!isAllowed) {
                                view?.loadData(
                                    """
                                    <html>
                                        <body style="font-family: Arial, sans-serif; text-align: center; padding: 20px;">
                                            <h1>Blocked</h1>
                                            <p>This URL is not in the allowlist.</p>
                                            <p>Allowed domains:</p>
                                            <ul style="list-style-type: none; padding: 0;">
                                                ${allowlist.joinToString("") { "<li>$it</li>" }}
                                            </ul>
                                        </body>
                                    </html>
                                    """.trimIndent(),
                                    "text/html",
                                    "UTF-8"
                                )
                                true // Block Webview from continuing to load this url
                            } else {
                                false // Allow allowed URLs
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // Only load error page for main frame
                            if (request?.isForMainFrame == true) {

                                Log.e("WEBVIEW_ERROR", "Error loading URL: ${request?.url}, Code: ${error?.errorCode}, Description: ${error?.description}")
                                println("WebView Error: ${error?.description} - Code: ${error?.errorCode}")
                                view?.loadData(
                                    """
                                        <html>
                                        <head>

                                        </head>

                                        <body>
                                        <h1>Page not available</h1>
                                        <p>Please check your internet connection.</p>
                                        <button onclick="window.location.reload()">Retry</button>
                                        </body></html>
                                    """.trimIndent(),
                                    "text/html",
                                    "UTF-8"
                                )
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { activity?.lastUrl = it }
                            println("Page loaded: $url")
                            
                            // Check if page contains "404 Page Not Found"
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    return document.documentElement.innerText.includes('404 Page Not Found');
                                })();
                                """.trimIndent()
                            ) { result ->
                                if (result == "true") {
                                    downloadViewModel.failDownload("App")
                                    // Go back to previous page instead of showing 404
                                    view?.post {
                                        if (view.canGoBack()) {
                                            view.goBack()
                                        } else {
                                            // If we can't go back, just load a blank page
                                            view.loadUrl("about:blank")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Download Listener for APK files
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                        val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                        val apkDir = File(context.filesDir, "apks")
                        val apkFile = File(apkDir, fileName)
                        
                        when {
                            // If file exists, show install button
                            apkFile.exists() -> {
                                downloadViewModel.completeDownload(fileName) {
                                    openApk(context, apkFile)
                                }
                            }
                            // If download is in progress, show current progress
                            downloadViewModel.downloadState.value is DownloadState.Downloading -> {
                                // Download already in progress, do nothing
                            }
                            // Start new download
                            else -> {
                                downloadViewModel.startDownload(fileName, contentLength.toLong())
                                downloadApk(
                                    context = context,
                                    url = downloadUrl,
                                    fileName = fileName,
                                    onProgress = { progress -> 
                                        downloadViewModel.updateProgress(progress)
                                    },
                                    onComplete = { success ->
                                        if (success) {
                                            downloadViewModel.completeDownload(fileName) {
                                                openApk(context, apkFile)
                                            }
                                        } else {
                                            downloadViewModel.failDownload(fileName)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Load the URL
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun downloadApk(context: Context, url: String, fileName: String, onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
    // Create the "apks" directory in private storage
    val apkDir = File(context.filesDir, "apks")
    if (!apkDir.exists()) apkDir.mkdir()

    // Destination file
    val apkFile = File(apkDir, fileName)

    // Start the download in a background thread
    Thread {
        try {
            val urlConnection = URL(url).openConnection()
            urlConnection.connect()

            val contentLength = urlConnection.contentLength.toLong()
            var downloadedLength = 0L

            val inputStream = urlConnection.getInputStream()
            val outputStream = FileOutputStream(apkFile.absolutePath)

            val buffer = ByteArray(8192) // Increased buffer size for better performance
            var bytesRead: Int

            // Read data in chunks and write to the file
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedLength += bytesRead
                
                // Update progress on UI thread
                if (contentLength > 0) {
                    val progress = ((downloadedLength * 100) / contentLength).toInt()
                    (context as ComponentActivity).runOnUiThread { 
                        onProgress(progress)
                    }
                }
            }

            // Close streams after completion
            outputStream.close()
            inputStream.close()

            // Notify completion on the UI thread
            (context as ComponentActivity).runOnUiThread { onComplete(true) }
        } catch (e: Exception) {
            e.printStackTrace()
            // Notify failure on the UI thread
            (context as ComponentActivity).runOnUiThread { onComplete(false) }
            // Delete partial file if download failed
            if (apkFile.exists()) {
                apkFile.delete()
            }
        }
    }.start()
}

fun getDownloadedApks(context: Context): List<File> {
    val apkDir = File(context.filesDir, "apks")
    return apkDir.listFiles()?.toList() ?: emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedFilesScreen(
    context: Context,
    downloadViewModel: DownloadViewModel = viewModel()
) {
    var downloadedFiles by remember { mutableStateOf(getDownloadedApks(context)) }
    val activity = LocalContext.current as? MainActivity

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Downloaded Files") },
            navigationIcon = {
                IconButton(onClick = { 
                    activity?.setContent {
                        WrapperWebviewTheme {
                            WebViewScreen(url = activity.lastUrl, downloadViewModel = downloadViewModel)
                        }
                    }
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (downloadedFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloaded files")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(downloadedFiles) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { 
                            Column {
                                Text("Size: ${file.length() / 1024} KB")
                                Text("Created: ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(file.lastModified())}")
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { 
                                    file.delete()
                                    downloadedFiles = getDownloadedApks(context)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                                Button(onClick = { openApk(context, file) }) {
                                    Text("Install")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

fun openApk(context: Context, apkFile: File) {
    try {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider", // Ensure this matches the FileProvider in the manifest
            apkFile
        )
        Log.d("DEBUG_OPEN_APK", "APK URI: $apkUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = apkUri // Don't specify MIME type, let Android infer it
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d("DEBUG_OPEN_APK", "Intent prepared: $intent")

        context.startActivity(intent)
        Log.d("DEBUG_OPEN_APK", "Intent launched successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to open APK: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}