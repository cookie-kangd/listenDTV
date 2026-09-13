package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.net.OkHttp;

import java.nio.charset.StandardCharsets;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Minimal WebDAV client for cloud sync. Only GET/PUT/MKCOL with Basic auth - exactly what a
 *坚果云/Nextcloud/InfiniCloud account needs and nothing more.
 *
 * <p>The client is direct (Dns.SYSTEM + NO_PROXY): a WebDAV host is a real internet host, but
 * keeping the same policy as LAN traffic avoids surprises when the content config has installed
 * a global proxy.</p>
 */
public class WebDav {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static OkHttpClient client() {
        return OkHttp.client(15000).newBuilder().dns(Dns.SYSTEM).proxy(java.net.Proxy.NO_PROXY).build();
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(Setting.getWebDavUrl());
    }

    private static String auth() {
        String cred = Setting.getWebDavUser() + ":" + Setting.getWebDavPass();
        return "Basic " + Base64.encodeToString(cred.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String url(String file) {
        String base = Setting.getWebDavUrl().trim();
        if (!base.startsWith("http")) base = "https://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + file;
    }

    /** Downloads a file; throws with the HTTP code on failure. */
    public static String get(String file) throws Exception {
        Request request = new Request.Builder().url(url(file)).header("Authorization", auth()).build();
        try (Response response = client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("HTTP " + response.code());
            return response.body().string();
        }
    }

    /** Uploads a file, creating the folder chain first when the server rejects the PUT. */
    public static void put(String file, String body) throws Exception {
        String target = url(file);
        if (putOnce(target, body)) return;
        mkdirs(target);
        if (!putOnce(target, body)) throw new IllegalStateException("HTTP error");
    }

    private static boolean putOnce(String target, String body) throws Exception {
        Request request = new Request.Builder().url(target).header("Authorization", auth()).post(RequestBody.create(JSON, body)).build();
        try (Response response = client().newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /** Creates every missing folder between the WebDAV root and the file itself. */
    private static void mkdirs(String target) throws Exception {
        int scheme = target.indexOf("://");
        String rest = target.substring(scheme + 3);
        int slash = rest.indexOf('/');
        String prefix = target.substring(0, scheme + 3 + slash);
        String[] parts = rest.substring(slash + 1).split("/");
        StringBuilder path = new StringBuilder(prefix);
        OkHttpClient client = client();
        for (int i = 0; i < parts.length - 1; i++) {
            path.append("/").append(parts[i]);
            Request request = new Request.Builder().url(path.toString()).header("Authorization", auth()).method("MKCOL", null).build();
            // 201 = created, 405 = already exists, both fine; anything else still worth a retry.
            try (Response response = client.newCall(request).execute()) {
                response.close();
            }
        }
    }
}
