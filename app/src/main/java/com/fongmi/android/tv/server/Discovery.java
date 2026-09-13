package com.fongmi.android.tv.server;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * LAN discovery responder.
 *
 * <p>The TCP sweep in {@code ScanTask} has to guess both the subnet and the port, which is why
 * two devices on the same Wi-Fi sometimes never see each other. This responder answers a single
 * broadcast with this device's JSON, so a peer can find us in one round trip without knowing
 * anything about us.</p>
 *
 * <p>Android Wi-Fi drivers drop packets that are not explicitly addressed to the device unless
 * the app holds a {@link WifiManager.MulticastLock} - the manifest permission alone changes
 * nothing. Without the lock the responder never hears the broadcast at all, which is exactly
 * the "same Wi-Fi but never found" symptom, so the lock is acquired for the responder's
 * lifetime and released when it stops.</p>
 *
 * <p>It is a pure addition: peers that do not understand the magic packet are ignored, and the
 * TCP sweep keeps working for older builds.</p>
 */
public class Discovery {

    /** UDP port used for discovery. Deliberately outside the HTTP 9978..9998 range. */
    public static final int PORT = 45678;

    /** Payload a peer must send for us to answer. */
    public static final String MAGIC = "LISTENDTV/DISCOVER/1";

    /** Device info is rebuilt at most this often, it shells out for the serial number. */
    private static final long CACHE_MS = 5_000;

    private static WifiManager.MulticastLock lock;

    private volatile DatagramSocket socket;
    private volatile Thread thread;

    private volatile long stamp;
    private volatile String cached = "";

    public synchronized void start() {
        if (socket != null) return;
        try {
            acquireLock();
            DatagramSocket s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(PORT));
            socket = s;
            thread = new Thread(this::loop, "lan-discovery");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable e) {
            socket = null;
            thread = null;
        }
    }

    public synchronized void stop() {
        DatagramSocket s = socket;
        socket = null;
        thread = null;
        if (s != null) s.close();
        releaseLock();
    }

    /** Without this the Wi-Fi stack silently discards broadcast packets on many devices. */
    private static void acquireLock() {
        if (lock != null && lock.isHeld()) return;
        try {
            WifiManager wifi = (WifiManager) App.get().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            lock = wifi.createMulticastLock("listendtv-discovery");
            lock.setReferenceCounted(false);
            lock.acquire();
        } catch (Throwable e) {
            lock = null;
        }
    }

    private static void releaseLock() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (Throwable ignored) {
        }
        lock = null;
    }

    private void loop() {
        byte[] buffer = new byte[1024];
        while (socket != null) {
            DatagramSocket s = socket;
            if (s == null || s.isClosed()) return;
            try {
                DatagramPacket in = new DatagramPacket(buffer, buffer.length);
                s.receive(in);
                String text = new String(in.getData(), in.getOffset(), in.getLength(), StandardCharsets.UTF_8);
                if (!text.startsWith(MAGIC)) continue;
                byte[] out = self().getBytes(StandardCharsets.UTF_8);
                s.send(new DatagramPacket(out, out.length, in.getAddress(), in.getPort()));
            } catch (Throwable e) {
                if (socket == null || s.isClosed()) return;
            }
        }
    }

    private String self() {
        String value = cached;
        if (!value.isEmpty() && System.currentTimeMillis() - stamp < CACHE_MS) return value;
        try {
            value = Device.get().toString();
        } catch (Throwable e) {
            value = "";
        }
        stamp = System.currentTimeMillis();
        cached = value;
        return value;
    }
}
