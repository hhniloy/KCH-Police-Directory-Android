package com.kchpolicedirectory.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.ValueCallback;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PRIMARY_URL = "https://kchpolicedirectory.vercel.app";
    private static final String FALLBACK_URL = "https://pdkch.netlify.app";
    private static final int TIMEOUT_SECONDS = 5;
    private static final int PERMISSION_REQUEST_CODE = 100;

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

    // File chooser variables
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

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

        // Initialize file chooser launcher
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback == null) return;
                
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String dataString = data.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        );

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
                        // If still loading when network lost, show error screen
                        if (!pageLoaded) {
                            cancelTimeout();
                            showError();
                        }
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
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Handle Facebook links - try to open in Facebook app
                if (url.contains("facebook.com") || url.contains("fb.com")) {
                    try {
                        // Try to open in Facebook app first
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.setPackage("com.facebook.katana");
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // Facebook app not installed, try browser
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(intent);
                            return true;
                        } catch (Exception ex) {
                            // Couldn't open, let WebView handle it
                            return false;
                        }
                    }
                }
                
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
                // If network was lost during loading, don't show webview
                if (!isNetworkAvailable()) {
                    showError();
                    return;
                }
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

            // For Android 5.0+
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                            FileChooserParams fileChooserParams) {
                // Check if we already have a file chooser callback
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                // Check permissions
                if (!checkPermissions()) {
                    requestPermissions();
                    return true;
                }

                // Launch file chooser
                launchFileChooser(fileChooserParams);
                return true;
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Below Android 13
            return ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},
                PERMISSION_REQUEST_CODE);
        } else {
            // Below Android 13
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, launch file chooser
                launchFileChooser(null);
            } else {
                // Permission denied
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
                Toast.makeText(this, "ফাইল আপলোড করার জন্য অনুমতি প্রয়োজন", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams fileChooserParams) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        
        Intent chooserIntent = Intent.createChooser(intent, "ছবি নির্বাচন করুন");
        
        try {
            fileChooserLauncher.launch(chooserIntent);
        } catch (Exception e) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
            Toast.makeText(this, "ফাইল চুজার খুলতে পারেনি", Toast.LENGTH_SHORT).show();
        }
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
