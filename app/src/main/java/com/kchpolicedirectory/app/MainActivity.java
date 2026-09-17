package com.kchpolicedirectory.app;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String WEBSITE_URL = "https://kchpolicedirectory.vercel.app";
    
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        retryButton = findViewById(R.id.retryButton);

        // Configure WebView settings
        setupWebView();

        // Set up retry button
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            public void onPageFinished(WebView view, String url) {
                // Hide loading indicator when page finishes loading
                progressBar.setVisibility(View.GONE);
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Show error layout if page fails to load
                showError();
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
        
        // Load the KCH Police Directory website
        webView.loadUrl(WEBSITE_URL);
    }

    private void showError() {
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
        // Clean up WebView
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
