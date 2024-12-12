package com.example.wrapperwebview

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.viewinterop.AndroidView
import com.example.wrapperwebview.ui.theme.WrapperWebviewTheme

import java.io.File
import java.io.FileOutputStream
import java.net.URL
import android.net.Uri

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider

// fullscreen
import android.webkit.WebChromeClient
import android.view.View
import android.widget.FrameLayout

import android.util.Log


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WrapperWebviewTheme {
                WebViewScreen("https://seashell-app-gxd5i.ondigitalocean.app")
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String) {
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
                        return if (url.endsWith("/downloads/")) {
                            // open the android file list screen when the websites url ends with /downloads
                            (view?.context as? ComponentActivity)?.setContent {
                                DownloadedFilesScreen(view.context)
                            }
                            true
                        } else {
                            super.shouldOverrideUrlLoading(view, request)
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
                                    <a href="https://google.com" class="button">Go to Example</a>
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
                    downloadApk(context, downloadUrl, fileName) {
                        Toast.makeText(context, "APK downloaded: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }

                // Load the URL
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun downloadApk(context: Context, url: String, fileName: String, onComplete: () -> Unit) {
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

            val inputStream = urlConnection.getInputStream()
            val outputStream = FileOutputStream(apkFile.absolutePath)

            val buffer = ByteArray(1024)
            var bytesRead: Int // Declare the variable without initialization

            // Read data in chunks and write to the file
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            // Close streams after completion
            outputStream.close()
            inputStream.close()

            // Notify completion on the UI thread
            (context as ComponentActivity).runOnUiThread { onComplete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start() // Start the thread
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