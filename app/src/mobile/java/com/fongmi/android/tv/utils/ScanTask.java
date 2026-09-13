package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Discovery;
import com.fongmi.android.tv.server.Nsd;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Finds other listenDTV devices on the local network.
 *
 * <p>Things that used to make this fail silently on a perfectly good Wi-Fi:</p>
 * <ul>
 *   <li>the sweep reused the shared OkHttp client, so every {@code 192.168.x.x} probe went
 *       through DoH and the user's host/proxy rules - a LAN address is not a hostname and
 *       must never be resolved or proxied;</li>
 *   <li>it only walked the single /24 taken from {@code Util.getIp()} and only ever tried
 *       port 9978, while the embedded HTTP server may end up on any port in 9978..9998;</li>
 *   <li>the embedded server only starts when a config loads, so a device sitting in this
 *       dialog right after a fresh install had nothing listening and could never be found;</li>
 *   <li>the broadcast responder cannot hear anything without a MulticastLock on the peer.</li>
 * </ul>
 *
 * <p>Discovery now runs three passes, first to last resort: mDNS browsing through the system
 * NSD stack (no subnet or port guessing, immune to vendor broadcast filtering), then a UDP
 * broadcast the peer answers directly, then a TCP sweep across every local subnet and both
 * plausible ports. The sweep uses a dedicated wide pool so the probe count never outruns the
 * deadline. Failures are reported back through {@link Listener#onScanEnd(int)} so the UI can
 * say something useful instead of showing an empty list forever.</p>
 */
public class ScanTask {

    /** Default port of the embedded HTTP server, still first choice. */
    public static final int DEFAULT_PORT = 9978;

    /** How long we browse mDNS for advertised peers. */
    private static final long NSD_MS = 2500;

    /** How long we listen for broadcast replies. */
    private static final long BROADCAST_MS = 1200;

    /** Upper bound for the whole sweep, so the dialog always gets an answer. */
    private static final long SWEEP_MS = 30_000;

    /** More than this many local subnets usually means VPN/emulator noise. */
    private static final int MAX_SUBNETS = 3;

    /**
     * A 20-thread pool needs ~30s for 254 hosts x 2 ports at 1.2s timeouts, right at the
     * deadline; 48 threads at 800ms finishes the same sweep in under 10s.
     */
    private static final int SWEEP_THREADS = 48;

    private final CopyOnWriteArrayList<Future<?>> future;
    private final Set<String> found;
    private final AtomicInteger count;
    private final OkHttpClient client;
    private final ExecutorService pool;
    private Listener listener;

    public ScanTask(Listener listener) {
        // Direct connections only: Dns.SYSTEM handles IP literals locally and NO_PROXY
        // keeps the LAN traffic away from whatever proxy the content config installed.
        this.client = OkHttp.client(800).newBuilder()
                .dns(Dns.SYSTEM)
                .proxy(java.net.Proxy.NO_PROXY)
                .build();
        this.future = new CopyOnWriteArrayList<>();
        this.found = new LinkedHashSet<>();
        this.count = new AtomicInteger();
        this.listener = listener;
        this.pool = Executors.newFixedThreadPool(SWEEP_THREADS, r -> {
            Thread thread = new Thread(r, "lan-scan");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        Task.execute(this::run);
    }

    public void start(String url) {
        Task.execute(() -> {
            String target = normalize(url);
            if (!target.isEmpty()) probe(target, null);
        });
    }

    public void stop() {
        listener = null;
        OkHttp.cancel(client, "scan");
        future.forEach(f -> f.cancel(true));
        future.clear();
        pool.shutdownNow();
    }

    private void run() {
        try {
            // Be discoverable ourselves: the embedded server otherwise only starts when a
            // config loads, which may never have happened on the device being scanned for.
            Server.get().start();
            List<String> self = localIps();
            notifyStart();
            browseNsd(self);
            List<String> hosts = hosts(self);
            if (!hosts.isEmpty()) {
                broadcast(hosts, self);
                int[] ports = ports();
                for (String host : hosts)
                    for (int port : ports)
                        future.add(pool.submit(() -> probe("http://" + host + ":" + port, self)));
                await();
            } else {
                Thread.sleep(BROADCAST_MS);
            }
        } catch (Throwable e) {
            // Interruption from stop() lands here; still report the outcome.
        }
        finish();
    }

    /** Pass 1: mDNS browse through the system NSD stack, then probe what it resolved. */
    private void browseNsd(List<String> self) {
        for (String url : Nsd.create().discover(NSD_MS)) {
            future.add(pool.submit(() -> probe(url, self)));
        }
    }

    /** Walks every local /24, skipping anything that is not a plain IPv4 LAN address. */
    private List<String> hosts(List<String> self) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String ip : self) {
            int index = ip.lastIndexOf('.');
            if (index > 0) prefixes.add(ip.substring(0, index + 1));
            if (prefixes.size() >= MAX_SUBNETS) break;
        }
        List<String> hosts = new ArrayList<>();
        for (String prefix : prefixes) for (int i = 1; i <= 254; i++) hosts.add(prefix + i);
        return hosts;
    }

    /** Our own addresses, used both to build the host list and to skip ourselves. */
    private List<String> localIps() {
        Set<String> ips = new LinkedHashSet<>();
        String own = host(Server.get().getAddress());
        if (!own.isEmpty()) ips.add(own);
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface nif = en.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isPointToPoint()) continue;
                for (Enumeration<InetAddress> addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)) continue;
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    ips.add(address.getHostAddress());
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(ips);
    }

    /** Ports worth probing: whatever our own server bound, plus the historical default. */
    private int[] ports() {
        int own = Server.get().getPort();
        if (own <= 0 || own == DEFAULT_PORT) return new int[]{DEFAULT_PORT};
        return new int[]{own, DEFAULT_PORT};
    }

    /**
     * One broadcast, then collect unicast replies. This is the path that actually works when
     * the subnet or the port guess would have been wrong.
     */
    private void broadcast(List<String> hosts, List<String> self) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            byte[] magic = Discovery.MAGIC.getBytes(StandardCharsets.UTF_8);
            Set<String> targets = new LinkedHashSet<>();
            targets.add("255.255.255.255");
            for (String item : hosts) {
                int index = item.lastIndexOf('.');
                if (index > 0) targets.add(item.substring(0, index + 1) + "255");
            }
            for (String target : targets) {
                try {
                    socket.send(new DatagramPacket(magic, magic.length, InetAddress.getByName(target), Discovery.PORT));
                } catch (Throwable ignored) {
                }
            }
            byte[] buffer = new byte[4096];
            long deadline = System.currentTimeMillis() + BROADCAST_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket in = new DatagramPacket(buffer, buffer.length);
                    socket.receive(in);
                    accept(new String(in.getData(), in.getOffset(), in.getLength(), StandardCharsets.UTF_8), self);
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (socket != null) socket.close();
        }
    }

    /** Blocks until the sweep is done or the deadline passes, so onScanEnd is meaningful. */
    private void await() {
        long deadline = System.currentTimeMillis() + SWEEP_MS;
        while (System.currentTimeMillis() < deadline) {
            boolean busy = false;
            for (Future<?> item : future) if (!item.isDone()) {
                busy = true;
                break;
            }
            if (!busy) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private boolean probe(String url, List<String> self) {
        if (self != null && self.contains(host(url))) return false;
        try (Response response = OkHttp.newCall(client, url.concat("/device"), "scan").execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            String json = response.body().string();
            accept(json, self);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void accept(String json, List<String> self) {
        if (json == null || json.isEmpty()) return;
        Device device;
        try {
            device = Device.objectFrom(json);
        } catch (Throwable e) {
            return;
        }
        if (device == null) return;
        // Our own broadcast can loop back to us, and a self entry in the list is useless.
        if (device.getUuid().isEmpty()) return;
        if (self != null && self.contains(host(device.getIp()))) return;
        synchronized (found) {
            if (!found.add(device.getUuid())) return;
        }
        count.incrementAndGet();
        App.post(() -> {
            if (listener != null) listener.onFind(device.save());
        });
    }

    private void notifyStart() {
        App.post(() -> {
            if (listener != null) listener.onScanStart();
        });
    }

    private void finish() {
        int total = count.get();
        App.post(() -> {
            if (listener != null) listener.onScanEnd(total);
        });
    }

    /** Extracts "192.168.1.7" from "http://192.168.1.7:9978". */
    private static String host(String url) {
        if (url == null) return "";
        String text = url;
        int scheme = text.indexOf("://");
        if (scheme >= 0) text = text.substring(scheme + 3);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        int colon = text.lastIndexOf(':');
        if (colon >= 0) text = text.substring(0, colon);
        return text.trim();
    }

    /** Turns what a user typed into a probe URL: {@code 192.168.1.7 -> http://192.168.1.7:9978}. */
    public static String normalize(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return "";
        if (!text.startsWith("http")) text = "http://" + text;
        String rest = text.substring(text.indexOf("://") + 3);
        if (rest.isEmpty() || rest.startsWith(":") || !rest.contains(".")) return "";
        if (!rest.contains(":")) text = text + ":" + DEFAULT_PORT;
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    public interface Listener {

        void onFind(Device device);

        /** A scan round started, the UI can show progress. */
        default void onScanStart() {
        }

        /** A scan round ended; {@code count} is how many devices were found in total. */
        default void onScanEnd(int count) {
        }
    }
}
