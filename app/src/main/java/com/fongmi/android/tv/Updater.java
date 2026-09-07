package com.fongmi.android.tv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private String tag;
    private String asset;
    // 0 = primary mirror, 1 = alternate mirror, 2 = direct GitHub. Advanced
    // whenever a download fails OR the downloaded bytes turn out to be stale.
    private int attempt;
    private boolean manual;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        // Cache the downloaded APK in an app-private directory that never requires
        // storage permission (covers low Android versions where writing elsewhere
        // triggers "permission defined" errors). The name is unique per target
        // version so a file left by an older OTA can never be mixed up with the
        // new download. Fall back to internal cache if external is unavailable.
        Context ctx = App.get();
        File dir = ctx.getExternalCacheDir();
        if (dir == null || !dir.canWrite()) dir = ctx.getCacheDir();
        return new File(dir, "update_" + BuildConfig.VERSION_NAME + ".apk");
    }

    private void deleteFile() {
        // Wipe the current target file plus any legacy update APKs (older naming
        // scheme) so a previous download can never be mistaken for the new one.
        try {
            Context ctx = App.get();
            File[] roots = {ctx.getExternalCacheDir(), ctx.getCacheDir()};
            for (File dir : roots) {
                if (dir == null) continue;
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    String name = f.getName();
                    if (name.equals("update.apk") || name.startsWith("update_")) f.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String getApkName() {
        return BuildConfig.FLAVOR + "-" + (android.os.Process.is64Bit() ? "arm64_v8a" : "armeabi_v7a") + ".apk";
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        manual = true;
        return this;
    }

    public void start(FragmentActivity activity) {
        // Silent auto-check only when the user-facing switch is on AND the
        // in-app "remind" flag hasn't been dismissed; manual checks bypass both.
        if (!manual && (!Setting.getAutoUpdate() || !Setting.getUpdate())) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject object = Github.fetchLatest();
            String tag = object.optString("tag_name");
            String desc = object.optString("body");
            if (!isNewer(tag)) {
                if (manual) App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            String asset = findApk(object);
            if (asset == null) {
                if (manual) App.post(() -> Notify.show(R.string.update_fail));
                return;
            }
            this.tag = tag;
            this.asset = asset;
            App.post(() -> show(activity, tag, desc));
        } catch (Exception e) {
            if (manual) App.post(() -> Notify.show(R.string.update_fail));
        }
    }

    private String findApk(JSONObject object) {
        String wanted = getApkName();
        JSONArray assets = object.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name");
            if (wanted.equals(name)) return name;
        }
        return null;
    }

    private boolean isNewer(String tag) {
        return compareVersion(tag, BuildConfig.VERSION_NAME) > 0;
    }

    private static int compareVersion(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("\\.");
        String[] b = right.replaceFirst("^[vV]", "").split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        attempt = 0;
        startAttempt();
    }

    /**
     * Downloads the release asset through the URL for the current attempt:
     * busted primary mirror, busted alternate mirror, then direct GitHub.
     */
    private void startAttempt() {
        deleteFile();
        String url;
        if (attempt == 0) url = Github.getApk(tag, asset);
        else if (attempt == 1) url = Github.getApkAlt(tag, asset);
        else url = Github.getApkDirect(tag, asset);
        download = Download.create(url, getFile());
        download.start(this);
    }

    /**
     * The installer refuses packages older than the installed one with a
     * confusing "higher version already installed" error, so every downloaded
     * APK is verified BEFORE it reaches the installer. Mirrors between the app
     * and GitHub have been observed handing out stale bytes under fresh URLs;
     * when that happens the download is retried through the next source.
     */
    private boolean isUsableApk(File file) {
        try {
            PackageInfo archive = App.get().getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            PackageInfo installed = App.get().getPackageManager().getPackageInfo(App.get().getPackageName(), 0);
            return archive != null && archive.versionCode >= installed.versionCode;
        } catch (Exception e) {
            return false;
        }
    }

    private void retryOrFail() {
        if (attempt < 2) {
            attempt++;
            startAttempt();
            return;
        }
        deleteFile();
        Notify.show(R.string.update_fail);
        dismiss();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        deleteFile();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        retryOrFail();
    }

    @Override
    public void success(File file) {
        if (!isUsableApk(file)) {
            retryOrFail();
            return;
        }
        FileUtil.openFile(file);
        dismiss();
    }
}
