package com.kchpolicedirectory.app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    // Primary and fallback URLs
    private static final String PRIMARY_URL = "https://kchpolicedirectory.vercel.app";
    private static final String FALLBACK_URL = "https://pdkch.netlify.app";
    private static final int TIMEOUT_SECONDS = 5;
    
    private WebView webView;
    private ProgressBar progressBar;
    private ProgressBar horizontalProgress;
    private LinearLayout errorLayout;
    private LinearLayout loadingLayout;
    private Button retryButton;
    private SwipeRefreshLayout swipeRefresh;
    
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private boolean pageLoaded = false;
    private boolean fallbackAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        horizontalProgress = findViewById(R.id.horizontalProgress);
        errorLayout = findViewById(R.id.errorLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        retryButton = findViewById(R.id.retryButton);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        
        // Initialize timeout handler
        timeoutHandler = new Handler();

        // Configure WebView settings
        setupWebView();
        
        // Setup SwipeRefreshLayout
        setupSwipeRefresh();

        // Set up retry button
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fallbackAttempted = false;
                pageLoaded = false;
                loadWebsite();
            }
        });

        // Load the website
        loadWebsite();
    }
    
    private void setupSwipeRefresh() {
        // Disable default swipe refresh behavior
        swipeRefresh.setEnabled(false);
        
        swipeRefresh.setColorSchemeColors(
            getResources().getColor(R.color.colorPrimary),
            getResources().getColor(R.color.colorAccent)
        );
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Check network before refreshing
                if (!isNetworkAvailable()) {
                    swipeRefresh.setRefreshing(false);
                    showError();
                    return;
                }
                
                fallbackAttempted = false;
                pageLoaded = false;
                loadWebsite();
            }
        });
        
        // Enable swipe refresh only when page is loaded
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY == 0 && pageLoaded) {
                    swipeRefresh.setEnabled(true);
                } else {
                    swipeRefresh.setEnabled(false);
                }
            }
        });
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript (required for modern websites)
        webSettings.setJavaScriptEnabled(true);
        
        // Enable DOM storage (required for many web apps)
        webSettings.setDomStorageEnabled(true);
        
        // Enable database storage
        webSettings.setDatabaseEnabled(true);
        
        // Enable zoom controls but hide the zoom buttons
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // Load websites in this WebView instead of external browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Handle external schemes (WhatsApp, Phone, Email, etc.)
                if (url.startsWith("whatsapp://") || 
                    url.startsWith("https://wa.me/") ||
                    url.startsWith("https://api.whatsapp.com/") ||
                    url.startsWith("tel:") || 
                    url.startsWith("mailto:") ||
                    url.startsWith("sms:") ||
                    url.startsWith("intent:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // If app not installed, do nothing
                        return true;
                    }
                }
                
                // Load normal URLs in WebView
                return false;
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                // Ignore blank pages
                if (url.equals("about:blank") || url.isEmpty()) {
                    return;
                }
                super.onPageStarted(view, url, favicon);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                // Ignore blank/empty pages
                if (url == null || url.equals("about:blank") || url.isEmpty()) {
                    return;
                }
                
                // Cancel timeout when page loads successfully
                cancelTimeout();
                pageLoaded = true;
                swipeRefresh.setRefreshing(false);
                
                // NOW show WebView - only after page successfully loaded
                webView.setVisibility(View.VISIBLE);
                loadingLayout.setVisibility(View.GONE);
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Only handle errors for main page, not resources (images, css, etc.)
                if (request.isForMainFrame()) {
                    // Ignore errors for external schemes (WhatsApp, tel, etc.)
                    String url = request.getUrl().toString();
                    if (url.startsWith("whatsapp://") || 
                        url.startsWith("tel:") || 
                        url.startsWith("mailto:") ||
                        url.startsWith("sms:")) {
                        return;
                    }
                    
                    // Try fallback URL if primary fails
                    if (!fallbackAttempted && url.contains(PRIMARY_URL)) {
                        cancelTimeout();
                        fallbackAttempted = true;
                        loadFallbackUrl();
                    } else {
                        // Show error layout if both URLs fail
                        cancelTimeout();
                        showError();
                    }
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Legacy error handling for older Android versions
                // Ignore errors for external schemes
                if (failingUrl.startsWith("whatsapp://") || 
                    failingUrl.startsWith("tel:") || 
                    failingUrl.startsWith("mailto:") ||
                    failingUrl.startsWith("sms:")) {
                    return;
                }
                
                // Try fallback URL if primary fails
                if (!fallbackAttempted && failingUrl.contains(PRIMARY_URL)) {
                    cancelTimeout();
                    fallbackAttempted = true;
                    loadFallbackUrl();
                } else {
                    // Show error layout if both URLs fail
                    cancelTimeout();
                    showError();
                }
            }
        });

        // Enable progress tracking
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // Update horizontal progress bar
                if (newProgress < 100) {
                    horizontalProgress.setVisibility(View.VISIBLE);
                    horizontalProgress.setProgress(newProgress);
                } else {
                    horizontalProgress.setVisibility(View.GONE);
                }
            }
        });
    }

    private void loadWebsite() {
        // Check internet connection first
        if (!isNetworkAvailable()) {
            swipeRefresh.setRefreshing(false);
            loadingLayout.setVisibility(View.GONE);
            showError();
            return;
        }
        
        // Hide everything except loading
        errorLayout.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE); // Keep hidden until page loads successfully
        
        // Reset flags
        pageLoaded = false;
        
        // Set timeout for primary URL
        startTimeout();
        
        // Load the primary URL (Vercel)
        webView.loadUrl(PRIMARY_URL);
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    private void loadFallbackUrl() {
        // Load the fallback URL (Netlify) without timeout
        webView.loadUrl(FALLBACK_URL);
    }
    
    private void startTimeout() {
        cancelTimeout(); // Cancel any existing timeout
        
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                // If page hasn't loaded within timeout and fallback not attempted
                if (!pageLoaded && !fallbackAttempted) {
                    fallbackAttempted = true;
                    loadFallbackUrl();
                }
            }
        };
        
        // Start timeout (5 seconds)
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_SECONDS * 1000);
    }
    
    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void showError() {
        // Stop loading
        webView.stopLoading();
        swipeRefresh.setRefreshing(false);
        cancelTimeout();
        
        // Hide everything except error
        webView.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.GONE);
        horizontalProgress.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        // If WebView can go back, go back in browsing history
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // Otherwise, close the app
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        // Cancel timeout
        cancelTimeout();
        
        // Clean up WebView
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}

import android.widget.Toast;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.os.Build;
