package com.fongmi.android.tv.utils;

import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

public class Github {

    public static final String REPO = "cookie-kangd/listenDTV";
    public static final String PROXY = "https://v4.gh-proxy.org/";
    public static final String PROXY_ALT = "https://gh-proxy.org/";
    private static final String DOWNLOAD = "https://github.com/" + REPO + "/releases/download/";

    public static String getApi() {
        return "https://api.github.com/repos/" + REPO + "/releases/latest";
    }

    /**
     * Fetches the latest release info. Tries the gh-proxy mirror first (works in
     * mainland China where api.github.com is unreachable), then falls back to a
     * direct GitHub API call. Every request carries a unique query so no caching
     * layer in between (gh-proxy, CDN, device HTTP cache) can serve a stale JSON
     * that would point the OTA at an older release.
     */
    public static JSONObject fetchLatest() throws Exception {
        try {
            return new JSONObject(OkHttp.string(bust(PROXY + getApi())));
        } catch (Exception e) {
            return new JSONObject(OkHttp.string(bust(getApi())));
        }
    }

    public static String getApk(String tag, String name) {
        // Proxy URLs are cache-busted per attempt: the release asset URL itself
        // is unique per tag, but shared mirrors have been observed serving stale
        // cached bodies, so each download request must look fresh to every cache.
        return bust(PROXY + getApkDirect(tag, name));
    }

    /**
     * Same asset through the alternate mirror. CDN nodes cache independently,
     * so when one node serves stale bytes the alternate is a different cache
     * pool and very likely a cache miss.
     */
    public static String getApkAlt(String tag, String name) {
        return bust(PROXY_ALT + getApkDirect(tag, name));
    }

    public static String getApkDirect(String tag, String name) {
        return DOWNLOAD + tag + "/" + name;
    }

    private static String bust(String url) {
        return url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
    }
}
