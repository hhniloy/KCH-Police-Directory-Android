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
import android.view.Gravity;
import android.view.LayoutInflater;
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
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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

        timeoutHandler = new Handler();

        setupWebView();
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

        // Check for app updates
        new UpdateChecker(this).checkForUpdate();
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
                            showCustomToast(getString(R.string.network_available), true);
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
                        showCustomToast(getString(R.string.no_internet), false);
                    }
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
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
        // Check network before loading fallback
        if (!isNetworkAvailable()) {
            showError();
            return;
        }
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
                    if (!isNetworkAvailable()) {
                        showError();
                    } else {
                        loadFallbackUrl();
                    }
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

    private void showCustomToast(String message, boolean isConnected) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast,
            (android.view.ViewGroup) findViewById(android.R.id.content), false);

        View toastLayout = layout.findViewById(R.id.toastLayout);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(isConnected ? 0xFF2E7D32 : 0xFFC62828);
        bg.setCornerRadius(48f);
        toastLayout.setBackground(bg);

        TextView text = layout.findViewById(R.id.toastText);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(isConnected ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    private void showError() {
        webView.stopLoading();
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
