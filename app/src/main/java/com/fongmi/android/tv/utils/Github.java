package com.fongmi.android.tv.utils;

public class Github {

    public static final String REPO = "cookie-kangd/listenDTV";
    public static final String PROXY = "https://v4.gh-proxy.org/";
    private static final String DOWNLOAD = "https://github.com/" + REPO + "/releases/download/";

    public static String getApi() {
        return "https://api.github.com/repos/" + REPO + "/releases/latest";
    }

    public static String getApk(String tag, String name) {
        return PROXY + getApkDirect(tag, name);
    }

    public static String getApkDirect(String tag, String name) {
        return DOWNLOAD + tag + "/" + name;
    }
}
