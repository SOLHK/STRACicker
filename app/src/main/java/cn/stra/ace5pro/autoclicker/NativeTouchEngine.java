package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeTouchEngine {
    static final ExecutorService CONTROL =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "stra-control"));

    public interface Listener {
        void onFinished(String message);
    }

    private static final Object HELPER_LOCK = new Object();
    private static volatile boolean helperPrepared;
    private static volatile boolean helperRunning;
    private static volatile long probeAt;
    private static volatile boolean probeResult;
    private final Context context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process process;
    private Thread waiter;
    private File stopFile;

    public NativeTouchEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean probeSupport() {
        if (helperRunning) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        if (probeResult && now - probeAt < 60_000L) return true;
        if (!TouchDeviceDetector.hasRoot()) return false;
        Process p = null;
        try {
            String rootExec = prepareRootHelper();
            if (rootExec == null) return false;

            Point size = displaySize();
            p = new ProcessBuilder(
                    "su", "-c",
                    rootExec + " --probe " + size.x + " " + size.y)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = waitForLine(p, br, 3000L);

            if (!p.waitFor(4, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }

            probeResult = p.exitValue() == 0 && line != null && line.contains("READY");
            probeAt = android.os.SystemClock.elapsedRealtime();
            return probeResult;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    public synchronized boolean start(
            List<TapPoint> displayPoints,
            double intervalMs,
            long cycles,
            Listener listener) {

        if (running.get() || displayPoints == null || displayPoints.isEmpty()) {
            return false;
        }

        try {
            String rootExec = prepareRootHelper();
            if (rootExec == null) return false;

            stopFile = new File(context.getFilesDir(), "stra_uinput.stop");
            if (stopFile.exists()) stopFile.delete();

            Point size = displaySize();

            long holdUs = 250L;
            // The value is a complete start-to-start period. Fast mode targets 0.5 ms.
            long periodUs = intervalMs <= 0.0
                    ? 500L
                    : Math.max(500L, Math.min(2_000_000L,
                            Math.round(intervalMs * 1000.0)));

            StringBuilder cmd = new StringBuilder();
            cmd.append(rootExec).append(' ')
                    .append(shellQuote(stopFile.getAbsolutePath())).append(' ')
                    .append(size.x).append(' ')
                    .append(size.y).append(' ')
                    .append(holdUs).append(' ')
                    .append(periodUs).append(' ')
                    .append(Math.max(0L, cycles)).append(' ')
                    .append(displayPoints.size());

            for (TapPoint p : displayPoints) {
                int x = Math.max(0, Math.min(size.x - 1, p.x));
                int y = Math.max(0, Math.min(size.y - 1, p.y));
                cmd.append(' ').append(x).append(' ').append(y);
            }

            process = new ProcessBuilder("su", "-c", cmd.toString())
                    .redirectErrorStream(true)
                    .start();

            BufferedReader br =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            String ready = waitForLine(process, br, 3500L);

            if (ready == null || !ready.contains("READY")) {
                try { process.destroyForcibly(); } catch (Throwable ignored) {}
                hardStopBlocking(context);
                process = null;
                return false;
            }

            running.set(true); helperRunning = true;
            probeResult = true; probeAt = android.os.SystemClock.elapsedRealtime();
            final Process ownedProcess = process;
            final BufferedReader output = br;
            waiter = new Thread(() -> {
                String end = "已停止";
                String lastError = null;
                try {
                    String line;
                    while ((line = output.readLine()) != null) {
                        if (!line.trim().isEmpty()) lastError = line.trim();
                    }

                    int exit = ownedProcess.waitFor();

                    if (exit != 0 && running.get()) {
                        end = lastError == null
                                ? "引擎异常退出：" + exit
                                : "引擎异常：" + lastError;
                    }
                } catch (Throwable ignored) {
                } finally {
                    synchronized (NativeTouchEngine.this) {
                        if (process == ownedProcess) {
                            running.set(false); helperRunning = false;
                            process = null;
                            waiter = null;
                        }
                    }
                    try { output.close(); } catch (java.io.IOException ignored) {}
                    if (listener != null) listener.onFinished(end);
                }
            }, "stra-uinput-waiter");
            waiter.start();
            return true;

        } catch (Throwable t) {
            running.set(false);
            try { if (process != null) process.destroyForcibly(); } catch (Throwable ignored) {}
            process = null;
            return false;
        }
    }

    public synchronized void stop() {
        running.set(false);

        try {
            File f = stopFile != null
                    ? stopFile
                    : new File(context.getFilesDir(), "stra_uinput.stop");
            new FileOutputStream(f, false).close();
        } catch (Throwable ignored) {}

        final Process p = process;
        if (p != null) {
            new Thread(() -> {
                try {
                    if (!p.waitFor(450, TimeUnit.MILLISECONDS)) p.destroy();
                    if (!p.waitFor(350, TimeUnit.MILLISECONDS)) p.destroyForcibly();
                } catch (Throwable ignored) {}
            }, "stra-uinput-stop").start();
        }
    }

    /** Only call on CONTROL, never the Android main thread. */
    public void stopBlocking() {
        final Process owned;
        synchronized (this) { owned = process; }
        stop();
        if (owned != null) {
            try {
                if (!owned.waitFor(500, TimeUnit.MILLISECONDS)) {
                    hardStopBlocking(context);
                    if (!owned.waitFor(500, TimeUnit.MILLISECONDS)) owned.destroyForcibly();
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        synchronized (this) {
            if (process == owned) { process = null; running.set(false); helperRunning = false; }
        }
    }

    public static void hardStop(Context context) {
        Context app = context.getApplicationContext();
        signalStopFile(app);
        CONTROL.execute(() -> {
            terminateRootHelper();
        });
    }

    static void hardStopBlocking(Context context) {
        signalStopFile(context.getApplicationContext());
        terminateRootHelper();
    }

    static void signalStopFile(Context app) {
        try {
            File stop = new File(app.getFilesDir(), "stra_uinput.stop");
            new FileOutputStream(stop, false).close();
        } catch (Throwable ignored) {}
    }

    private static void terminateRootHelper() {
        String cmd = "helper=/data/local/tmp/stra_touch_ace5pro; "
                + "pidfile=/data/local/tmp/stra_touch_ace5pro.pid; "
                + "pid=$(cat \"$pidfile\" 2>/dev/null); "
                + "case \"$pid\" in ''|*[!0-9]*) exit 0;; esac; "
                + "exe=$(readlink \"/proc/$pid/exe\" 2>/dev/null); "
                + "[ \"$exe\" = \"$helper\" ] || exit 0; "
                + "kill -TERM \"$pid\" 2>/dev/null; sleep 0.08; "
                + "exe=$(readlink \"/proc/$pid/exe\" 2>/dev/null); "
                + "[ \"$exe\" = \"$helper\" ] && kill -KILL \"$pid\" 2>/dev/null; "
                + "rm -f \"$pidfile\"";
        TouchDeviceDetector.execRoot(cmd, 2500L);
    }

    private String prepareRootHelper() {
        synchronized (HELPER_LOCK) {
        if (helperPrepared) return "/data/local/tmp/stra_touch_ace5pro";
        try {
            String local = installHelper();
            if (local == null) return null;

            String rootExec = "/data/local/tmp/stra_touch_ace5pro";
            String command = "cp " + shellQuote(local) + " " + rootExec + ".new"
                    + " && chmod 755 " + rootExec + ".new && mv -f " + rootExec + ".new " + rootExec;

            String out = TouchDeviceDetector.execRoot(command);
            helperPrepared = out != null;
            return out == null ? null : rootExec;
        } catch (Throwable t) {
            return null;
        }
    }

    }

    private String installHelper() {
        File dst = new File(context.getFilesDir(), "stra_touch_ace5pro.bin");

        int resId = context.getResources().getIdentifier(
                "stra_touch_arm64", "raw", context.getPackageName());
        if (resId == 0) return null;

        try (InputStream in = context.getResources().openRawResource(resId);
             FileOutputStream out = new FileOutputStream(dst, false)) {

            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            dst.setReadable(true, true);
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private Point displaySize() {
        WindowManager wm =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        Point p = new Point();
        display.getRealSize(p);
        p.x = Math.max(2, p.x);
        p.y = Math.max(2, p.y);
        return p;
    }

    private static String waitForLine(
            Process p,
            BufferedReader br,
            long timeoutMs) throws Exception {

        long end = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < end) {
            if (br.ready()) return br.readLine();

            if (!p.isAlive()) {
                if (br.ready()) return br.readLine();
                return null;
            }

            Thread.sleep(25L);
        }

        return null;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
