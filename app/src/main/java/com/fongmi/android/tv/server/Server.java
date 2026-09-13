package com.fongmi.android.tv.server;

import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Util;

public class Server {

    private final Discovery discovery = new Discovery();
    private volatile PlaybackService service;
    private volatile Nano nano;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized void start() {
        if (nano != null) return;
        for (int i = 9978; i < 9999; i++) {
            try {
                nano = new Nano(i);
                nano.start(500);
                Proxy.set(i);
                break;
            } catch (Throwable e) {
                nano = null;
            }
        }
        // Answer LAN discovery broadcasts too, otherwise a peer has to guess both our subnet
        // and our port before it can find us.
        if (nano != null) {
            discovery.start();
            Nsd.create().register(getPort());
        }
    }

    public void stop() {
        Task.execute(() -> {
            Nsd.create().unregister();
            discovery.stop();
            if (nano != null) nano.stop();
            service = null;
            nano = null;
        });
    }

    /** Port the HTTP server actually listens on, -1 when it never started. */
    public int getPort() {
        return nano == null ? -1 : Proxy.getPort();
    }
}
