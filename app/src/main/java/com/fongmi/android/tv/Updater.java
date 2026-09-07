package com.fongmi.android.tv;

import android.content.Context;
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
    private String apkUrl;
    private String directUrl;
    private boolean proxyFailed;
    private boolean manual;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        // Cache the downloaded APK in an app-private directory that never requires
        // storage permission (covers low Android versions where writing elsewhere
        // triggers "permission defined" errors). Fall back to internal cache if the
        // external cache is unavailable or not writable.
        Context ctx = App.get();
        File dir = ctx.getExternalCacheDir();
        if (dir == null || !dir.canWrite()) dir = ctx.getCacheDir();
        return new File(dir, "update.apk");
    }

    private void deleteFile() {
        try {
            File f = getFile();
            if (f != null && f.exists() && !f.delete()) f.deleteOnExit();
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
            apkUrl = Github.getApk(tag, asset);
            String url = apkUrl;
            App.post(() -> show(activity, tag, desc, url));
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

    private void show(FragmentActivity activity, String version, String desc, String url) {
        dismiss();
        apkUrl = url;
        directUrl = Github.getApkDirect(version, getApkName());
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        deleteFile();
        download = Download.create(apkUrl, getFile());
        download.start(this);
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
        // gh-proxy can be flaky; fall back to the direct GitHub URL once.
        if (!proxyFailed && directUrl != null) {
            proxyFailed = true;
            download = Download.create(directUrl, getFile());
            download.start(this);
            return;
        }
        deleteFile();
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
