package org.learningequality.Kolibri;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Main activity that displays Kolibri in a WebView using HTTP + Service Worker
 *
 * <p>First launch: waits for HTTP server and uses initialization URL with auth token Subsequent
 * launches: loads immediately, Service Worker serves cached content
 */
public class WebViewActivity extends AppCompatActivity {
  private static final String TAG = "WebViewActivity";
  private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

  private WebView webView;
  private FrameLayout fullscreenContainer;
  private ImageView splashImage;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_webview);

    requestNotificationPermission();
    setupWebView();
    setupBackNavigation();
    loadKolibri();
  }

  /** Request POST_NOTIFICATIONS permission on Android 13+ */
  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.POST_NOTIFICATIONS},
            REQUEST_NOTIFICATION_PERMISSION);
      }
    }
  }

  /**
   * Setup back navigation using OnBackPressedCallback. Handles WebView history navigation before
   * exiting activity.
   */
  private void setupBackNavigation() {
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                  webView.goBack();
                } else {
                  // Disable this callback and trigger default back behavior
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });
  }

  private void setupWebView() {
    WebView webView = findViewById(R.id.webview);
    fullscreenContainer = findViewById(R.id.fullscreen_container);
    splashImage = findViewById(R.id.splash_image);
    WebSettings settings = webView.getSettings();

    // Enable cookies and ensure persistence
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    cookieManager.setAcceptThirdPartyCookies(webView, true);

    // Enable DOM storage for Service Worker
    settings.setDomStorageEnabled(true);
    settings.setJavaScriptEnabled(true);

    // Enable Service Worker support (mixed content for local HTTP server)
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

    // Enable modern web features
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);

    // Set WebChromeClient for fullscreen video
    webView.setWebChromeClient(new KolibriWebChromeClient(this, fullscreenContainer));

    // Use default WebViewClient (no more WSGI interception)
    webView.setWebViewClient(new WebViewClient());

    this.webView = webView;
  }

  private void loadKolibri() {
    WebView webView = findViewById(R.id.webview);
    CookieManager cookieManager = CookieManager.getInstance();

    // Check if we have a session cookie (determines which URL to use when server is ready)
    String cookies =
        cookieManager.getCookie(
            KolibriConstants.getLocalUrl(KolibriConstants.DEFAULT_KOLIBRI_PORT));
    boolean hasSessionCookie =
        cookies != null && cookies.contains(KolibriConstants.KOLIBRI_SESSION_COOKIE + "=");

    Log.d(TAG, "Loading Kolibri, hasSessionCookie=" + hasSessionCookie);

    // Show loading HTML in WebView while waiting for server
    showLoadingHtml();

    // Start server service
    Intent serverIntent = new Intent(this, KolibriServerService.class);
    startService(serverIntent);

    // Observe server readiness from singleton
    // Note: Using getInstance() not ViewModelProvider to ensure we observe
    // the same instance that Python updates via setServerReady()
    KolibriServerViewModel viewModel = KolibriServerViewModel.getInstance();

    viewModel
        .getServerReadyLiveData()
        .observe(
            this,
            serverInfo -> {
              if (serverInfo.isReady()) {
                String url;
                if (hasSessionCookie) {
                  // Subsequent launch - use regular URL
                  url = KolibriConstants.getLocalUrl(serverInfo.getPort());
                  Log.d(TAG, "Server ready (subsequent launch), loading: " + url);
                } else {
                  // First launch - use initialization URL with auth token
                  url = serverInfo.getInitializationUrl();
                  Log.d(TAG, "Server ready (first launch), loading: " + url);
                }
                if (!url.isEmpty()) {
                  webView.loadUrl(url);
                }
              }
            });
  }

  /**
   * Show the loading HTML page in the WebView while waiting for server to start. Also hides the
   * splash image since the loading page has the animated Kolibri.
   */
  private void showLoadingHtml() {
    String loadingHtml = getString(R.string.loading_page_html);
    String encodedHtml = Base64.encodeToString(loadingHtml.getBytes(), Base64.NO_PADDING);
    webView.loadData(encodedHtml, "text/html", "base64");

    // Hide splash image after a short delay to ensure smooth transition
    webView.postDelayed(this::hideSplash, 500);
  }

  /** Hide the splash screen image */
  private void hideSplash() {
    if (splashImage != null) {
      splashImage.setVisibility(View.GONE);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    // Destroy WebView
    if (webView != null) {
      webView.destroy();
      webView = null;
    }
  }
}
