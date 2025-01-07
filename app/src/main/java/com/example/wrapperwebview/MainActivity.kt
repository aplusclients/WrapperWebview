package com.example.wrapperwebview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.URL

// fullscreen
import android.webkit.WebChromeClient
import android.view.View
import android.widget.FrameLayout

import android.util.Log

class MainActivity : ComponentActivity() {
    private val downloadViewModel by viewModels<DownloadViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WrapperWebviewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box {
                        WebViewScreen(url = "https://seashell-app-gxd5i.ondigitalocean.app")
                        DownloadStatusOverlay()
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStatusOverlay(
    viewModel: DownloadViewModel = viewModel()
) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    
    if (downloadState != null) {
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
                                onClick = { viewModel.hideDownload() }
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
                                        viewModel.hideDownload()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Install Now")
                                }
                                TextButton(
                                    onClick = { viewModel.hideDownload() }
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
                                onClick = { viewModel.hideDownload() }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                null -> { /* Nothing to show */ }
            }
        }
    }
}

sealed class DownloadState {
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
    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState = _downloadState.asStateFlow()
    
    fun startDownload(fileName: String, contentLength: Long) {
        _downloadState.value = DownloadState.Downloading(fileName, 0, contentLength)
    }
    
    fun updateProgress(progress: Int) {
        val current = _downloadState.value
        if (current is DownloadState.Downloading) {
            _downloadState.value = current.copy(progress = progress)
        }
    }
    
    fun completeDownload(fileName: String, onInstall: () -> Unit) {
        _downloadState.value = DownloadState.Complete(fileName, onInstall)
    }
    
    fun failDownload(fileName: String) {
        _downloadState.value = DownloadState.Failed(fileName)
    }
    
    fun hideDownload() {
        _downloadState.value = null
    }
}

@Composable
fun WebViewScreen(url: String) {

    val allowlist = listOf(
        "https://seashell-app-gxd5i.ondigitalocean.app",
        "https://f-droid.org",
        "https://www.allowedsite.com",
        "https://allowedsite.com"
    )

    val context = LocalContext.current
    val viewModel: DownloadViewModel = viewModel()
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Configure WebView settings safely
                val webSettings: WebSettings = settings
                webSettings.javaScriptEnabled = true
                webSettings.domStorageEnabled = true

                // Enable caching
                webSettings.cacheMode = WebSettings.LOAD_DEFAULT

                webSettings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

                webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // enable cookies
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // settings to allow the study music fullscreen to work NO EFFECT IT SEEMS
                webSettings.mediaPlaybackRequiresUserGesture = false
                webSettings.loadWithOverviewMode = true
                webSettings.useWideViewPort = true


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
                                DownloadedFilesScreen(view.context)
                            }
                            true
                        } else if (isAllowed) {
                            false // Allow allowed URLs
                        } else {
                            view?.loadData(
                                """
                                <html>
                                    <body style="font-family: Arial, sans-serif; text-align: center; padding: 20px;">
                                        <h1>Blocked</h1>
                                        <p>Navigation to this URL is not allowed.</p>
                                        <p>$url</p>
                                        <button onclick="history.back()" style="margin-top: 20px; padding: 10px 20px; font-size: 16px;">
                                            Go Back
                                        </button>
                                    </body>
                                </html>
                                """.trimIndent(),
                                "text/html",
                                "UTF-8"
                            )
                            true // Block Webview from continuing to load this url

                        } .also {
                            if (!isAllowed && view != null) {
                                super.shouldOverrideUrlLoading(view, request)
                            }
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
                                    <a href="https://seashell-app-gxd5i.ondigitalocean.app" class="button">Go to Example</a>
                                    </body></html>
                                """.trimIndent(),
                                "text/html",
                                "UTF-8"
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        println("Page loaded: $url")
                    }
                }

                // Download Listener for APK files
                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                    val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                    
                    viewModel.startDownload(fileName, contentLength.toLong())
                    
                    downloadApk(
                        context = context,
                        url = downloadUrl,
                        fileName = fileName,
                        onProgress = { progress -> 
                            viewModel.updateProgress(progress)
                        },
                        onComplete = { success ->
                            if (success) {
                                viewModel.completeDownload(fileName) {
                                    val apkDir = File(context.filesDir, "apks")
                                    val apkFile = File(apkDir, fileName)
                                    openApk(context, apkFile)
                                }
                            } else {
                                viewModel.failDownload(fileName)
                            }
                        }
                    )
                }

                // Load the URL
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
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

@Composable
fun DownloadedFilesScreen(context: Context) {
    val downloadedFiles = remember { getDownloadedApks(context)}

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Downloaded Files",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(downloadedFiles) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openApk(context, file)
                        }
                        .padding(16.dp)
                ) {
                    Text(text = file.name)
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