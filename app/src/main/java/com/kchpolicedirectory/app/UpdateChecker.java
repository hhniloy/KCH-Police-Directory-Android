package com.kchpolicedirectory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String GITHUB_USER = "hhniloy";
    private static final String GITHUB_REPO = "KCH-Police-Directory-Android";
    private static final String API_URL =
        "https://api.github.com/repos/" + GITHUB_USER + "/" + GITHUB_REPO + "/releases/latest";

    private final Activity activity;
    private final Handler mainHandler = new Handler();
    private boolean downloadCancelled = false;

    public UpdateChecker(Activity activity) {
        this.activity = activity;
    }

    public void checkForUpdate() {
        new CheckUpdateTask().execute();
    }

    // ── Step 1: Check GitHub for latest version ──────────────────────────────
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {

        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                HttpURLConnection conn = openConnection(API_URL);
                if (conn.getResponseCode() != 200) return null;

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                String tag = json.getString("tag_name").replace("v", "");
                String url = json.getJSONArray("assets")
                    .getJSONObject(0)
                    .getString("browser_download_url");

                return new UpdateInfo(tag, url);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateInfo info) {
            if (info == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (isNewer(info.version, BuildConfig.VERSION_NAME)) {
                showUpdateDialog(info);
            }
        }
    }

    // ── Step 2: Show update dialog ────────────────────────────────────────────
    private void showUpdateDialog(final UpdateInfo info) {
        new AlertDialog.Builder(activity)
            .setTitle("নতুন আপডেট পাওয়া গেছে! 🎉")
            .setMessage("ভার্সন " + info.version + " পাওয়া গেছে।\n\nএখনই আপডেট করবেন?")
            .setPositiveButton("আপডেট করুন", (d, w) -> startDownload(info))
            .setNegativeButton("পরে করব", null)
            .setCancelable(true)
            .show();
    }

    // ── Step 3: Download APK with progress ───────────────────────────────────
    private void startDownload(final UpdateInfo info) {
        // Build progress dialog
        View view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_download_progress, null);
        final ProgressBar bar    = view.findViewById(R.id.downloadProgressBar);
        final TextView tvPercent = view.findViewById(R.id.tvDownloadPercent);
        final TextView tvStatus  = view.findViewById(R.id.tvDownloadStatus);

        tvStatus.setText("সংযোগ স্থাপন হচ্ছে...");
        downloadCancelled = false;

        final AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("আপডেট ডাউনলোড হচ্ছে")
            .setView(view)
            .setCancelable(false)
            .setNegativeButton("বাতিল করুন", (d, w) -> {
                downloadCancelled = true;
                d.dismiss();
            })
            .create();
        dialog.show();

        final String fileName = "KCH-Police-Directory-v" + info.version + ".apk";
        final File dest = new File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        new AsyncTask<Void, int[], Boolean>() {
            @Override
            protected Boolean doInBackground(Void... v) {
                try {
                    // Follow redirects manually to get final URL
                    String finalUrl = info.downloadUrl;
                    for (int i = 0; i < 10; i++) {
                        HttpURLConnection c = openConnection(finalUrl);
                        c.setInstanceFollowRedirects(false);
                        int code = c.getResponseCode();
                        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                            finalUrl = c.getHeaderField("Location");
                            c.disconnect();
                        } else {
                            // Got the real URL - download it
                            long total = c.getContentLengthLong();
                            InputStream in = c.getInputStream();
                            FileOutputStream out = new FileOutputStream(dest);
                            byte[] buf = new byte[8192];
                            long downloaded = 0;
                            int n;
                            while ((n = in.read(buf)) != -1) {
                                if (downloadCancelled) {
                                    out.close();
                                    in.close();
                                    c.disconnect();
                                    dest.delete();
                                    return false;
                                }
                                out.write(buf, 0, n);
                                downloaded += n;
                                if (total > 0) {
                                    int pct = (int) (downloaded * 100 / total);
                                    publishProgress(new int[]{
                                        pct,
                                        (int) downloaded,
                                        (int) total
                                    });
                                }
                            }
                            out.close();
                            in.close();
                            c.disconnect();
                            return true;
                        }
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            protected void onProgressUpdate(int[]... values) {
                if (downloadCancelled || !dialog.isShowing()) return;
                int pct  = values[0][0];
                long dl  = values[0][1];
                long tot = values[0][2];
                bar.setProgress(pct);
                tvPercent.setText(pct + "%");
                tvStatus.setText(fmtSize(dl) + " / " + fmtSize(tot));
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (dialog.isShowing()) dialog.dismiss();
                if (success) {
                    installApk(dest);
                } else if (!downloadCancelled) {
                    showErrorDialog("ডাউনলোড ব্যর্থ হয়েছে। আবার চেষ্টা করুন।");
                }
            }
        }.execute();
    }

    // ── Step 4: Install downloaded APK ───────────────────────────────────────
    private void installApk(final File apkFile) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!apkFile.exists()) {
                        showErrorDialog("ফাইল পাওয়া যাচ্ছে না: " + apkFile.getAbsolutePath());
                        return;
                    }

                    Uri uri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + ".fileprovider",
                        apkFile);

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    activity.startActivity(intent);

                } catch (Exception e) {
                    showErrorDialog("ইনস্টল করতে সমস্যা হয়েছে: " + e.getMessage());
                }
            }
        });
    }

    private void showErrorDialog(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                new AlertDialog.Builder(activity)
                    .setTitle("ত্রুটি")
                    .setMessage(message)
                    .setPositiveButton("ঠিক আছে", null)
                    .show();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private HttpURLConnection openConnection(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "KCH-Police-Directory-App");
        return c;
    }

    private boolean isNewer(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(l.length, c.length);
            for (int i = 0; i < len; i++) {
                int lv = i < l.length ? Integer.parseInt(l[i].trim()) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i].trim()) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static class UpdateInfo {
        final String version, downloadUrl;
        UpdateInfo(String v, String u) { version = v; downloadUrl = u; }
    }
}
