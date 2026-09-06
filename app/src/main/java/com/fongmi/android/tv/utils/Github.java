package com.fongmi.android.tv.utils;

import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

public class Github {

    public static final String REPO = "cookie-kangd/listenDTV";
    public static final String PROXY = "https://v4.gh-proxy.org/";
    private static final String DOWNLOAD = "https://github.com/" + REPO + "/releases/download/";

    public static String getApi() {
        return "https://api.github.com/repos/" + REPO + "/releases/latest";
    }

    /**
     * Fetches the latest release info. Tries the gh-proxy mirror first (works in
     * mainland China where api.github.com is unreachable), then falls back to a
     * direct GitHub API call.
     */
    public static JSONObject fetchLatest() throws Exception {
        try {
            return new JSONObject(OkHttp.string(PROXY + getApi()));
        } catch (Exception e) {
            return new JSONObject(OkHttp.string(getApi()));
        }
    }

    public static String getApk(String tag, String name) {
        return PROXY + getApkDirect(tag, name);
    }

    public static String getApkDirect(String tag, String name) {
        return DOWNLOAD + tag + "/" + name;
    }
}
