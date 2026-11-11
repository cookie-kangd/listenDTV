package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

import java.io.File;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;
    private int started;
    private boolean pendingClean;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Synchronous and BEFORE any Activity / config loading starts.
        // The old Task.execute() version raced with VodConfig / Glide / OkHttp
        // initialization, so files created after the snapshot survived and the
        // settings page never showed 0.
        if (Setting.getExitClean()) clearAppCache();
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
    }

    /**
     * Wipe both cache roots recursively. Two passes reduce the chance that a
     * file written concurrently by another initializer is missed.
     */
    public static void clearAppCache() {
        App app = get();
        for (int pass = 0; pass < 2; pass++) {
            app.clearDir(app.getCacheDir());
            File external = app.getExternalCacheDir();
            if (external != null) app.clearDir(external);
        }
    }

    private void clearDir(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) delete(file);
        } catch (Exception ignored) {
        }
    }

    private void delete(File file) {
        try {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
            // deleteOnExit() is a no-op on Android: the process is killed
            // without running JVM shutdown hooks. Leftovers are retried on the
            // next launch / next return to foreground instead.
            file.delete();
        } catch (Exception ignored) {
        }
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // No cache cleanup here: configuration changes briefly drop the
        // activity count to zero, and the process may stay alive for background
        // audio playback, so onDestroy is not a reliable "user exited" signal.
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // Covers the case where the process was NOT killed (background listen
        // playback keeps it alive), so Application.onCreate() runs only once.
        if (++started != 1 || !pendingClean) return;
        pendingClean = false;
        if (Setting.getExitClean()) clearAppCache();
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // Defer the wipe until the user actually comes back. Deleting while a
        // background playback session is still writing to the cache would drop
        // files under an active player.
        if (--started <= 0) {
            started = 0;
            pendingClean = true;
        }
    }
}