package com.kchpolicedirectory.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Primary and fallback URLs
    private static final String PRIMARY_URL = "https://kchpolicedirectory.vercel.app";
    private static final String FALLBACK_URL = "https://pdkch.netlify.app";
    private static final int TIMEOUT_SECONDS = 5;
    
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private Button retryButton;
    
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
        errorLayout = findViewById(R.id.errorLayout);
        retryButton = findViewById(R.id.retryButton);
        
        // Initialize timeout handler
        timeoutHandler = new Handler();

        // Configure WebView settings
        setupWebView();

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
            public void onPageFinished(WebView view, String url) {
                // Cancel timeout when page loads successfully
                cancelTimeout();
                pageLoaded = true;
                
                // Hide loading indicator when page finishes loading
                progressBar.setVisibility(View.GONE);
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Ignore errors for external schemes (WhatsApp, tel, etc.)
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
            
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                // Handle HTTP errors (404, 500, etc.)
                super.onReceivedHttpError(view, request, errorResponse);
            }
        });

        // Enable progress tracking
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // You could add a horizontal progress bar here if desired
            }
        });
    }

    private void loadWebsite() {
        // Hide error layout and show loading indicator
        errorLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        
        // Reset timeout flag
        pageLoaded = false;
        
        // Set timeout for primary URL
        startTimeout();
        
        // Load the primary URL (Vercel)
        webView.loadUrl(PRIMARY_URL);
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
        // Stop loading and clear WebView
        webView.stopLoading();
        webView.loadUrl("about:blank");
        
        // Hide WebView and progress, show error layout
        webView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
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
