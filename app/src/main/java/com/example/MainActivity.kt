package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private var customView: View? = null
  private var customViewCallback: WebChromeClient.CustomViewCallback? = null
  private var fullscreenContainer: FrameLayout? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainScreen(
          onShowCustomView = { view, callback ->
            showFullscreen(view, callback)
          },
          onHideCustomView = {
            hideFullscreen()
          },
          isFullscreenActive = customView != null
        )
      }
    }
  }

  private fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
    if (customView != null) {
      callback.onCustomViewHidden()
      return
    }

    customView = view
    customViewCallback = callback

    // Request sensor landscape for immersive video viewing
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    // Set up full screen container in the DecorView
    val decorView = window.decorView as FrameLayout
    val container = FrameLayout(this).apply {
      setBackgroundColor(android.graphics.Color.BLACK)
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }

    decorView.addView(container)
    container.addView(view, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    ))

    fullscreenContainer = container

    // Keep screen on during video playback and hide system bars
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    hideSystemUI()
  }

  private fun hideFullscreen() {
    val container = fullscreenContainer ?: return
    val decorView = window.decorView as FrameLayout

    container.removeAllViews()
    decorView.removeView(container)

    fullscreenContainer = null
    customView = null

    customViewCallback?.onCustomViewHidden()
    customViewCallback = null

    // Restore portrait/sensor rotation
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    showSystemUI()
  }

  @Suppress("DEPRECATION")
  private fun hideSystemUI() {
    window.decorView.systemUiVisibility = (
      View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    )
  }

  @Suppress("DEPRECATION")
  private fun showSystemUI() {
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    if (customView != null) {
      hideFullscreen()
    } else {
      super.onBackPressed()
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(
  onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
  onHideCustomView: () -> Unit,
  isFullscreenActive: Boolean
) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var loadProgress by remember { mutableStateOf(0) }
  var isOffline by remember { mutableStateOf(!isNetworkAvailable(context)) }
  var loadErrorOccurred by remember { mutableStateOf(false) }

  // Manage navigation inside WebView or system exits
  BackHandler(enabled = true) {
    if (isFullscreenActive) {
      onHideCustomView()
    } else {
      val webView = webViewRef
      if (webView != null && webView.canGoBack()) {
        webView.goBack()
      } else {
        (context as? ComponentActivity)?.finish()
      }
    }
  }

  // Pure dark cinematic background matching bawea.com
  val bqBackgroundColor = Color(0xFF07090A)

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .background(bqBackgroundColor),
    containerColor = bqBackgroundColor
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(bqBackgroundColor)
    ) {
      if (isOffline || loadErrorOccurred) {
        OfflineScreen(
          onRetry = {
            isOffline = !isNetworkAvailable(context)
            loadErrorOccurred = false
            webViewRef?.reload()
          }
        )
      } else {
        AndroidView(
          factory = { ctx ->
            WebView(ctx).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )

              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              }

              setLayerType(View.LAYER_TYPE_HARDWARE, null)

              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  isLoading = false
                }

                override fun onReceivedError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  error: WebResourceError?
                ) {
                  super.onReceivedError(view, request, error)
                  if (request?.isForMainFrame == true) {
                    loadErrorOccurred = true
                  }
                }

                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?
                ): Boolean {
                  val url = request?.url?.toString() ?: return false
                  return if (url.contains("bawea.com")) {
                    false
                  } else {
                    try {
                      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                      ctx.startActivity(intent)
                      true
                    } catch (e: Exception) {
                      false
                    }
                  }
                }
              }

              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  super.onProgressChanged(view, newProgress)
                  loadProgress = newProgress
                  if (newProgress >= 100) {
                    isLoading = false
                  }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                  if (view != null && callback != null) {
                    onShowCustomView(view, callback)
                  }
                }

                override fun onHideCustomView() {
                  onHideCustomView()
                }
              }

              loadUrl("https://www.bawea.com/")
              webViewRef = this
            }
          },
          modifier = Modifier.fillMaxSize()
        )

        // Custom golden-colored cinematic indicator matching bawea PWA brand color
        AnimatedVisibility(
          visible = isLoading,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
          ) {
            LinearProgressIndicator(
              progress = { loadProgress / 100f },
              modifier = Modifier.fillMaxWidth(),
              color = Color(0xFFFFD700),
              trackColor = Color(0xFF1E2225)
            )
          }
        }
      }
    }
  }
}

@Composable
fun OfflineScreen(onRetry: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF07090A))
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .size(130.dp)
        .background(Color(0xFF16191B), shape = RoundedCornerShape(65.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "اتصال منقطع",
        modifier = Modifier.size(56.dp),
        tint = Color(0xFFFFD700)
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    Text(
      text = "لا يوجد اتصال بالإنترنت",
      color = Color.White,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.SansSerif,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = "يتعذر تحميل منصة باوع حالياً. يرجى التحقق من اتصالك بالإنترنت وإعادة المحاولة للبدء بالمشاهدة.",
      color = Color(0xFF9EAFBA),
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      fontFamily = FontFamily.SansSerif,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 12.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
      onClick = onRetry,
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFFFD700),
        contentColor = Color.Black
      ),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth(0.65f)
        .height(48.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "إعادة المحاولة",
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.size(8.dp))
      Text(
        text = "إعادة المحاولة",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

fun isNetworkAvailable(context: Context): Boolean {
  val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val network = connectivityManager.activeNetwork ?: return false
  val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
  return when {
    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
    else -> false
  }
}
