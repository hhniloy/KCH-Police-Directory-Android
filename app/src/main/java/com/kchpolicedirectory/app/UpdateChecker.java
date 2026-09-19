package com.kchpolicedirectory.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    // GitHub repository info
    private static final String GITHUB_USER = "hhniloy";
    private static final String GITHUB_REPO = "KCH-Police-Directory-Android";
    private static final String API_URL =
        "https://api.github.com/repos/" + GITHUB_USER + "/" + GITHUB_REPO + "/releases/latest";

    private static final String CURRENT_VERSION = BuildConfig.VERSION_NAME;

    private final Activity activity;

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

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) return null;

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
                String releaseNotes = json.getString("body");

                return new UpdateInfo(latestVersion, downloadUrl, releaseNotes);

            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateInfo info) {
            if (info == null) return;
            if (activity.isFinishing() || activity.isDestroyed()) return;

            if (isNewerVersion(info.version, CURRENT_VERSION)) {
                showUpdateDialog(info);
            }
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(latestParts.length, currentParts.length);

            for (int i = 0; i < length; i++) {
                int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
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
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
                    activity.startActivity(intent);
                }
            })
            .setNegativeButton("পরে করব", null)
            .setCancelable(true)
            .show();
    }

    private static class UpdateInfo {
        String version;
        String downloadUrl;
        String releaseNotes;

        UpdateInfo(String version, String downloadUrl, String releaseNotes) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
        }
    }
}
