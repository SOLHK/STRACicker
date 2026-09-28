package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Bounded app-only event log. Never records point coordinates or input contents. */
final class DiagnosticLog {
    private static final String NAME = "stra-events.txt";
    private static final int LIMIT = 128 * 1024;
    private DiagnosticLog() {}

    static synchronized void record(Context context, String event) {
        try {
            File file = new File(context.getFilesDir(), NAME);
            if (file.length() > LIMIT) {
                byte[] all = Files.readAllBytes(file.toPath());
                int start = Math.max(0, all.length - LIMIT / 2);
                while (start < all.length && all[start] != '\n') start++;
                if (start < all.length) start++;
                try (FileOutputStream out = new FileOutputStream(file, false)) {
                    out.write(all, start, all.length - start);
                }
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            String line = fmt.format(new Date()) + " +08 " + event.replace('\n', ' ') + "\n";
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    static synchronized File export(Context context) throws Exception {
        File target = new File(context.getCacheDir(), "STRACicker-diagnostic.txt");
        File source = new File(context.getFilesDir(), NAME);
        String header = "STRACicker diagnostic log\n"
                + "App: " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "Beijing time synced: " + BeijingTimeManager.isSynced(context) + "\n"
                + "No coordinates or typed values are recorded.\n\n";
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
            if (source.exists()) out.write(Files.readAllBytes(source.toPath()));
        }
        return target;
    }
}
