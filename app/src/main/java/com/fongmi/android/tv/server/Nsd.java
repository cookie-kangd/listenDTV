package com.fongmi.android.tv.server;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * mDNS service advertisement and browsing over Android's built-in NSD stack.
 *
 * <p>This is the same discovery family Chromecast, AirPlay and network printers use, and it
 * solves what UDP broadcast and TCP sweeps cannot:</p>
 * <ul>
 *   <li>the system mdnsd daemon sends and receives the multicast frames, so no app-level
 *       {@code MulticastLock} is involved and vendor Wi-Fi filtering does not apply;</li>
 *   <li>the advertisement carries the real port, so there is no 9978..9998 guessing;</li>
 *   <li>it works across the whole link, including subnets a /24 sweep would miss.</li>
 * </ul>
 *
 * <p>Two devices need this build on both ends for the mDNS path to trigger, which is why
 * broadcast and the TCP sweep stay in place as fallbacks.</p>
 */
public class Nsd {

    /** mDNS service type advertised by listenDTV peers. */
    public static final String TYPE = "_listendtv._tcp.";

    private static volatile Nsd instance;

    private NsdManager manager;
    private NsdManager.RegistrationListener registration;

    public static synchronized Nsd create() {
        if (instance == null) instance = new Nsd();
        return instance;
    }

    /** Advertises the embedded server on the local network. Idempotent. */
    public synchronized void register(int port) {
        if (registration != null || port <= 0) return;
        try {
            manager = (NsdManager) App.get().getSystemService(Context.NSD_SERVICE);
            if (manager == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(name());
            info.setServiceType(TYPE);
            info.setPort(port);
            registration = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }
            };
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
        } catch (Throwable e) {
            registration = null;
        }
    }

    /** Removes the advertisement. Safe to call when never registered. */
    public synchronized void unregister() {
        NsdManager.RegistrationListener listener = registration;
        registration = null;
        if (listener == null || manager == null) return;
        try {
            manager.unregisterService(listener);
        } catch (Throwable ignored) {
        }
    }

    /** Collision-safe, stable name: the user-visible device name plus a short uuid hash. */
    private String name() {
        String base = Device.get().getName();
        if (base == null || base.trim().isEmpty()) base = "listenDTV";
        return base.trim() + "-" + String.format(Locale.US, "%04x", Device.get().getUuid().hashCode() & 0xffff);
    }

    /**
     * Browses the local network for advertised peers for {@code windowMs} and resolves them to
     * {@code http://ip:port} URLs. Android resolves one service at a time, so lookups are
     * serialized on the calling (scan) thread; NSD callbacks arrive on the main thread and hand
     * work over through the queue.
     */
    public Set<String> discover(long windowMs) {
        Set<String> result = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        try {
            NsdManager manager = (NsdManager) App.get().getSystemService(Context.NSD_SERVICE);
            if (manager == null) return result;
            Deque<NsdServiceInfo> pending = new ArrayDeque<>();
            Resolver resolver = new Resolver(manager, pending, result);
            NsdManager.DiscoveryListener browse = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String serviceType) {
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    wake(pending);
                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    synchronized (pending) {
                        pending.add(serviceInfo);
                        pending.notifyAll();
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                }
            };
            try {
                manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, browse);
            } catch (Throwable ignored) {
                return result;
            }
            resolver.run(System.currentTimeMillis() + windowMs);
            try {
                manager.stopServiceDiscovery(browse);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static void wake(Deque<NsdServiceInfo> pending) {
        synchronized (pending) {
            pending.notifyAll();
        }
    }

    /**
     * Drains the found queue, one {@code resolveService} at a time (Android allows a single
     * pending resolve). The host lookup happens here on the scan thread, never on main.
     */
    private static class Resolver {

        private final NsdManager manager;
        private final Deque<NsdServiceInfo> pending;
        private final Set<String> result;
        private final ConcurrentLinkedQueue<NsdServiceInfo> resolved = new ConcurrentLinkedQueue<>();
        private volatile boolean settle;

        Resolver(NsdManager manager, Deque<NsdServiceInfo> pending, Set<String> result) {
            this.manager = manager;
            this.pending = pending;
            this.result = result;
        }

        void run(long deadline) {
            while (System.currentTimeMillis() < deadline) {
                NsdServiceInfo info = next(deadline);
                if (info == null) continue;
                settle = false;
                try {
                    manager.resolveService(info, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            settle();
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            // Extracting the address here (main thread) can block; defer it.
                            resolved.add(serviceInfo);
                            settle();
                        }
                    });
                } catch (Throwable e) {
                    settle();
                }
                waitSettled(deadline);
                flush();
            }
            flush();
        }

        private NsdServiceInfo next(long deadline) {
            synchronized (pending) {
                NsdServiceInfo info = pending.poll();
                if (info != null) return info;
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return null;
                try {
                    pending.wait(Math.min(150, left));
                } catch (InterruptedException e) {
                    return null;
                }
                return null;
            }
        }

        /** Moves resolved services into the result set; runs on the scan thread. */
        private void flush() {
            NsdServiceInfo info;
            while ((info = resolved.poll()) != null) {
                try {
                    InetAddress host = info.getHost();
                    if (host == null || host.getHostAddress() == null) continue;
                    result.add("http://" + host.getHostAddress() + ":" + info.getPort());
                } catch (Throwable ignored) {
                }
            }
        }

        private void settle() {
            synchronized (resolved) {
                settle = true;
                resolved.notifyAll();
            }
        }

        private void waitSettled(long deadline) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return;
            synchronized (resolved) {
                while (!settle && left > 0) {
                    try {
                        left = deadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        resolved.wait(Math.min(200, left));
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }
}
