package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BeijingTimeManager {
    private static final String PREF = "stra_beijing_time";
    private static final long NTP_EPOCH_OFFSET = 2208988800L;
    private static final long RESYNC_AFTER_MS = 10L * 60L * 1000L;

    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);

    private static volatile long baseEpochMs = 0L;
    private static volatile long baseElapsedMs = 0L;
    private static volatile long lastSyncElapsedMs = 0L;
    private static volatile long lastRttMs = -1L;
    private static volatile String source = "未联网校时";
    private static volatile boolean synced = false;
    private static volatile boolean loaded = false;

    private BeijingTimeManager() {}

    public static void ensureSync(Context context) {
        load(context);

        long nowElapsed = SystemClock.elapsedRealtime();
        boolean stale = !synced
                || lastSyncElapsedMs <= 0L
                || nowElapsed < lastSyncElapsedMs
                || nowElapsed - lastSyncElapsedMs >= RESYNC_AFTER_MS;

        if (!stale || !SYNCING.compareAndSet(false, true)) return;

        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                SyncResult result = queryBestTime();
                if (result != null) {
                    apply(app, result);
                }
            } finally {
                SYNCING.set(false);
            }
        }, "stra-beijing-time-sync").start();
    }

    public static void forceSync(Context context, Runnable onDone) {
        load(context);

        if (!SYNCING.compareAndSet(false, true)) {
            if (onDone != null) onDone.run();
            return;
        }

        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                SyncResult result = queryBestTime();
                if (result != null) {
                    apply(app, result);
                }
            } finally {
                SYNCING.set(false);
                if (onDone != null) onDone.run();
            }
        }, "stra-beijing-time-force-sync").start();
    }

    public static long nowMs(Context context) {
        load(context);

        if (synced && baseEpochMs > 0L && baseElapsedMs > 0L) {
            long elapsed = SystemClock.elapsedRealtime();
            if (elapsed >= baseElapsedMs) {
                return baseEpochMs + (elapsed - baseElapsedMs);
            }
        }

        return System.currentTimeMillis();
    }

    public static boolean isSynced(Context context) {
        load(context);
        return synced;
    }

    public static boolean isSyncing() {
        return SYNCING.get();
    }

    public static String source(Context context) {
        load(context);
        return source;
    }

    public static long rttMs(Context context) {
        load(context);
        return lastRttMs;
    }

    public static long ageMs(Context context) {
        load(context);
        long now = SystemClock.elapsedRealtime();
        if (!synced || lastSyncElapsedMs <= 0L || now < lastSyncElapsedMs) return -1L;
        return now - lastSyncElapsedMs;
    }

    private static synchronized void load(Context context) {
        if (loaded) return;

        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);

        long savedEpoch = p.getLong("base_epoch", 0L);
        long savedElapsed = p.getLong("base_elapsed", 0L);
        long savedSyncElapsed = p.getLong("sync_elapsed", 0L);

        long nowElapsed = SystemClock.elapsedRealtime();
        boolean sameBoot = savedElapsed > 0L && nowElapsed >= savedElapsed;

        if (sameBoot) {
            baseEpochMs = savedEpoch;
            baseElapsedMs = savedElapsed;
            lastSyncElapsedMs = savedSyncElapsed;
            lastRttMs = p.getLong("rtt", -1L);
            source = p.getString("source", "未联网校时");
            synced = p.getBoolean("synced", false);
        } else {
            synced = false;
            source = "等待联网校时";
        }

        loaded = true;
    }

    private static synchronized void apply(Context context, SyncResult r) {
        baseEpochMs = r.epochAtElapsedMs;
        baseElapsedMs = r.elapsedReferenceMs;
        lastSyncElapsedMs = r.elapsedReferenceMs;
        lastRttMs = r.rttMs;
        source = r.source;
        synced = true;

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putLong("base_epoch", baseEpochMs)
                .putLong("base_elapsed", baseElapsedMs)
                .putLong("sync_elapsed", lastSyncElapsedMs)
                .putLong("rtt", lastRttMs)
                .putString("source", source)
                .putBoolean("synced", true)
                .apply();
    }

    private static SyncResult queryBestTime() {
        String[] ntpServers = new String[]{
                "ntp.aliyun.com",
                "ntp1.aliyun.com",
                "ntp.tencent.com",
                "ntp1.tencent.com"
        };

        SyncResult best = null;

        for (String server : ntpServers) {
            SyncResult r = queryNtp(server);
            if (r != null && (best == null || r.rttMs < best.rttMs)) {
                best = r;
            }

            if (best != null && best.rttMs <= 35L) {
                break;
            }
        }

        if (best != null) return best;

        String[] httpSources = new String[]{
                "https://www.aliyun.com/",
                "https://www.qq.com/"
        };

        for (String url : httpSources) {
            SyncResult r = queryHttpDate(url);
            if (r != null && (best == null || r.rttMs < best.rttMs)) {
                best = r;
            }
        }

        return best;
    }

    private static SyncResult queryNtp(String host) {
        DatagramSocket socket = null;

        try {
            byte[] buffer = new byte[48];
            buffer[0] = 0x1B;

            InetAddress address = InetAddress.getByName(host);
            socket = new DatagramSocket();
            socket.setSoTimeout(1400);

            long t1 = System.currentTimeMillis();

            writeTimestamp(buffer, 40, t1);

            DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, 123);
            socket.send(request);

            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            long t4 = System.currentTimeMillis();
            long elapsed4 = SystemClock.elapsedRealtime();

            long t2 = readTimestamp(buffer, 32);
            long t3 = readTimestamp(buffer, 40);

            if (t2 <= 0L || t3 <= 0L) return null;

            long offset = ((t2 - t1) + (t3 - t4)) / 2L;
            long networkDelay = (t4 - t1) - (t3 - t2);
            if (networkDelay < 0L) networkDelay = t4 - t1;

            long authoritativeAtReceive = t4 + offset;

            return new SyncResult(
                    authoritativeAtReceive,
                    elapsed4,
                    Math.max(0L, networkDelay),
                    "NTP · " + host);

        } catch (Throwable ignored) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private static SyncResult queryHttpDate(String urlString) {
        HttpURLConnection c = null;

        try {
            long startWall = System.currentTimeMillis();
            long startElapsed = SystemClock.elapsedRealtime();

            c = (HttpURLConnection) new URL(urlString).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(1800);
            c.setReadTimeout(1800);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.connect();

            long serverDate = c.getDate();
            long endElapsed = SystemClock.elapsedRealtime();

            if (serverDate <= 0L) return null;

            long rtt = Math.max(0L, endElapsed - startElapsed);
            long estimatedAtReceive = serverDate + rtt / 2L;

            String host = new URL(urlString).getHost();

            return new SyncResult(
                    estimatedAtReceive,
                    endElapsed,
                    rtt,
                    "HTTPS Date · " + host);

        } catch (Throwable ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static long readTimestamp(byte[] buffer, int offset) {
        long seconds = readUnsignedInt(buffer, offset);
        long fraction = readUnsignedInt(buffer, offset + 4);

        if (seconds == 0L && fraction == 0L) return 0L;

        long unixSeconds = seconds - NTP_EPOCH_OFFSET;
        long millis = (fraction * 1000L) >>> 32;

        return unixSeconds * 1000L + millis;
    }

    private static long readUnsignedInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFFL) << 24)
                | ((buffer[offset + 1] & 0xFFL) << 16)
                | ((buffer[offset + 2] & 0xFFL) << 8)
                | (buffer[offset + 3] & 0xFFL);
    }

    private static void writeTimestamp(byte[] buffer, int offset, long timeMs) {
        long seconds = timeMs / 1000L + NTP_EPOCH_OFFSET;
        long millis = timeMs % 1000L;
        long fraction = (millis << 32) / 1000L;

        buffer[offset] = (byte) (seconds >> 24);
        buffer[offset + 1] = (byte) (seconds >> 16);
        buffer[offset + 2] = (byte) (seconds >> 8);
        buffer[offset + 3] = (byte) seconds;

        buffer[offset + 4] = (byte) (fraction >> 24);
        buffer[offset + 5] = (byte) (fraction >> 16);
        buffer[offset + 6] = (byte) (fraction >> 8);
        buffer[offset + 7] = (byte) fraction;
    }

    private static final class SyncResult {
        final long epochAtElapsedMs;
        final long elapsedReferenceMs;
        final long rttMs;
        final String source;

        SyncResult(long epochAtElapsedMs, long elapsedReferenceMs, long rttMs, String source) {
            this.epochAtElapsedMs = epochAtElapsedMs;
            this.elapsedReferenceMs = elapsedReferenceMs;
            this.rttMs = rttMs;
            this.source = source;
        }
    }
}
