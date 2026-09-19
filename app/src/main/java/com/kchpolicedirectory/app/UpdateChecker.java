package com.kchpolicedirectory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String GITHUB_USER = "hhniloy";
    private static final String GITHUB_REPO = "KCH-Police-Directory-Android";
    private static final String API_URL =
        "https://api.github.com/repos/" + GITHUB_USER + "/" + GITHUB_REPO + "/releases/latest";

    private final Activity activity;
    private long downloadId = -1;
    private Handler progressHandler;
    private Runnable progressRunnable;

    public UpdateChecker(Activity activity) {
        this.activity = activity;
        this.progressHandler = new Handler();
    }

    public void checkForUpdate() {
        new CheckUpdateTask().execute();
    }

    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {

        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                if (connection.getResponseCode() != 200) return null;

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String latestVersion = json.getString("tag_name").replace("v", "");
                String downloadUrl = json.getJSONArray("assets")
                    .getJSONObject(0)
                    .getString("browser_download_url");

                return new UpdateInfo(latestVersion, downloadUrl);

            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateInfo info) {
            if (info == null) return;
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (isNewerVersion(info.version, BuildConfig.VERSION_NAME)) {
                showUpdateDialog(info);
            }
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(l.length, c.length);
            for (int i = 0; i < len; i++) {
                int lv = i < l.length ? Integer.parseInt(l[i]) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void showUpdateDialog(final UpdateInfo info) {
        new AlertDialog.Builder(activity)
            .setTitle("নতুন আপডেট পাওয়া গেছে! 🎉")
            .setMessage("ভার্সন " + info.version + " পাওয়া গেছে।\n\nএখনই আপডেট করবেন?")
            .setPositiveButton("আপডেট করুন", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startDownload(info.downloadUrl, info.version);
                }
            })
            .setNegativeButton("পরে করব", null)
            .setCancelable(true)
            .show();
    }

    private void startDownload(final String downloadUrl, final String version) {
        // Build custom progress dialog
        View dialogView = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_download_progress, null);

        final TextView tvStatus = dialogView.findViewById(R.id.tvDownloadStatus);
        final TextView tvPercent = dialogView.findViewById(R.id.tvDownloadPercent);
        final ProgressBar progressBar = dialogView.findViewById(R.id.downloadProgressBar);

        final AlertDialog progressDialog = new AlertDialog.Builder(activity)
            .setTitle("আপডেট ডাউনলোড হচ্ছে")
            .setView(dialogView)
            .setCancelable(false)
            .create();
        progressDialog.show();

        // Delete old APK if exists
        String fileName = "KCH-Police-Directory-v" + version + ".apk";
        File oldFile = new File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        if (oldFile.exists()) oldFile.delete();

        // Start DownloadManager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("KCH Police Directory v" + version)
            .setDescription("আপডেট ডাউনলোড হচ্ছে...")
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

        final DownloadManager downloadManager =
            (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = downloadManager.enqueue(request);

        // Poll progress every 500ms
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = downloadManager.query(query);

                if (cursor != null && cursor.moveToFirst()) {
                    int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusCol);

                    if (status == DownloadManager.STATUS_RUNNING ||
                        status == DownloadManager.STATUS_PAUSED) {
                        int bytesCol = cursor.getColumnIndex(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalCol = cursor.getColumnIndex(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        long downloaded = cursor.getLong(bytesCol);
                        long total = cursor.getLong(totalCol);

                        if (total > 0) {
                            int percent = (int) (downloaded * 100 / total);
                            progressBar.setProgress(percent);
                            tvPercent.setText(percent + "%");
                            tvStatus.setText(
                                formatSize(downloaded) + " / " + formatSize(total));
                        }
                        cursor.close();
                        progressHandler.postDelayed(this, 500);

                    } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close();
                        progressBar.setProgress(100);
                        tvPercent.setText("100%");
                        tvStatus.setText("ডাউনলোড সম্পন্ন!");
                        progressDialog.dismiss();
                        installApk(fileName);

                    } else if (status == DownloadManager.STATUS_FAILED) {
                        cursor.close();
                        progressDialog.dismiss();
                        showError("ডাউনলোড ব্যর্থ হয়েছে। পুনরায় চেষ্টা করুন।");
                    }
                } else {
                    if (cursor != null) cursor.close();
                    progressHandler.postDelayed(this, 500);
                }
            }
        };
        progressHandler.postDelayed(progressRunnable, 500);
    }

    private void installApk(String fileName) {
        File apkFile = new File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        if (!apkFile.exists()) {
            showError("ফাইল পাওয়া যাচ্ছে না।");
            return;
        }

        Uri apkUri = FileProvider.getUriForFile(
            activity,
            "com.kchpolicedirectory.app.fileprovider",
            apkFile);

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(installIntent);
    }

    private void showError(final String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(activity)
                    .setTitle("ত্রুটি")
                    .setMessage(message)
                    .setPositiveButton("ঠিক আছে", null)
                    .show();
            }
        });
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static class UpdateInfo {
        String version;
        String downloadUrl;

        UpdateInfo(String version, String downloadUrl) {
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
