package com.kchpolicedirectory.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

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
    private boolean wasConnected = true;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        horizontalProgress = findViewById(R.id.horizontalProgress);
        errorLayout = findViewById(R.id.errorLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        retryButton = findViewById(R.id.retryButton);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        timeoutHandler = new Handler();

        setupWebView();
        setupSwipeRefresh();
        setupNetworkMonitor();

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fallbackAttempted = false;
                pageLoaded = false;
                loadWebsite();
            }
        });

        loadWebsite();
    }

    private void setupNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        wasConnected = isNetworkAvailable();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (!wasConnected) {
                            wasConnected = true;
                            Toast.makeText(MainActivity.this,
                                getString(R.string.network_available),
                                Toast.LENGTH_SHORT).show();
                            if (errorLayout.getVisibility() == View.VISIBLE) {
                                fallbackAttempted = false;
                                pageLoaded = false;
                                loadWebsite();
                            }
                        }
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        wasConnected = false;
                        Toast.makeText(MainActivity.this,
                            getString(R.string.no_internet),
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setEnabled(false);
        swipeRefresh.setColorSchemeColors(
            getResources().getColor(R.color.colorPrimary),
            getResources().getColor(R.color.colorAccent)
        );
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
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

        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                swipeRefresh.setEnabled(scrollY == 0 && pageLoaded);
            }
        });
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
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
                    } catch (Exception e) {
                        // App not installed, ignore
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url == null || url.equals("about:blank") || url.isEmpty()) return;
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url == null || url.equals("about:blank") || url.isEmpty()) return;
                cancelTimeout();
                pageLoaded = true;
                swipeRefresh.setRefreshing(false);
                webView.setVisibility(View.VISIBLE);
                loadingLayout.setVisibility(View.GONE);
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                String url = request.getUrl().toString();
                if (url.startsWith("whatsapp://") || url.startsWith("tel:") ||
                    url.startsWith("mailto:") || url.startsWith("sms:")) return;
                if (!fallbackAttempted && url.contains(PRIMARY_URL)) {
                    cancelTimeout();
                    fallbackAttempted = true;
                    loadFallbackUrl();
                } else {
                    cancelTimeout();
                    showError();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl == null) return;
                if (failingUrl.startsWith("whatsapp://") || failingUrl.startsWith("tel:") ||
                    failingUrl.startsWith("mailto:") || failingUrl.startsWith("sms:")) return;
                if (!fallbackAttempted && failingUrl.contains(PRIMARY_URL)) {
                    cancelTimeout();
                    fallbackAttempted = true;
                    loadFallbackUrl();
                } else {
                    cancelTimeout();
                    showError();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
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
        if (!isNetworkAvailable()) {
            swipeRefresh.setRefreshing(false);
            loadingLayout.setVisibility(View.GONE);
            showError();
            return;
        }
        errorLayout.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        pageLoaded = false;
        startTimeout();
        webView.loadUrl(PRIMARY_URL);
    }

    private void loadFallbackUrl() {
        webView.loadUrl(FALLBACK_URL);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void startTimeout() {
        cancelTimeout();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pageLoaded && !fallbackAttempted) {
                    fallbackAttempted = true;
                    loadFallbackUrl();
                }
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_SECONDS * 1000);
    }

    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void showError() {
        webView.stopLoading();
        swipeRefresh.setRefreshing(false);
        cancelTimeout();
        webView.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.GONE);
        horizontalProgress.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        cancelTimeout();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
