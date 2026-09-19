package com.kchpolicedirectory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
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

    public UpdateChecker(Activity activity) {
        this.activity = activity;
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
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() != 200) return null;

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
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
                    showDownloadProgressDialog(info.downloadUrl, info.version);
                }
            })
            .setNegativeButton("পরে করব", null)
            .setCancelable(true)
            .show();
    }

    private void showDownloadProgressDialog(final String downloadUrl, final String version) {
        // Build progress dialog
        View dialogView = activity.getLayoutInflater()
            .inflate(android.R.layout.activity_list_item, null);

        final AlertDialog progressDialog = new AlertDialog.Builder(activity)
            .setTitle("ডাউনলোড হচ্ছে...")
            .setMessage("KCH Police Directory v" + version + " ডাউনলোড হচ্ছে, অপেক্ষা করুন...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        // Start download via DownloadManager
        String fileName = "KCH-Police-Directory-v" + version + ".apk";

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("KCH Police Directory v" + version)
            .setDescription("আপডেট ডাউনলোড হচ্ছে...")
            .setDestinationInExternalFilesDir(activity,
                Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager downloadManager =
            (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = downloadManager.enqueue(request);

        // Listen for download completion
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    progressDialog.dismiss();
                    activity.unregisterReceiver(this);
                    installApk(fileName);
                }
            }
        };

        activity.registerReceiver(receiver,
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void installApk(String fileName) {
        File apkFile = new File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        if (!apkFile.exists()) return;

        Uri apkUri = FileProvider.getUriForFile(
            activity,
            "com.kchpolicedirectory.app.fileprovider",
            apkFile);

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(installIntent);
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
