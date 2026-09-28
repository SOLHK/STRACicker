package cn.stra.ace5pro.autoclicker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TouchDeviceDetector {
    private TouchDeviceDetector() {}

    public static final class DeviceInfo {
        public String path = "";
        public String name = "";
        public int minX = 0, maxX = 0, minY = 0, maxY = 0;
        public boolean hasSlot;
        public boolean hasTracking;
        public boolean hasTouchMajor;
        public boolean hasPressure;
        public boolean hasBtnTouch;
        public boolean hasBtnToolFinger;
        public boolean direct;

        public boolean valid() {
            return path.startsWith("/dev/input/event")
                    && maxX > minX
                    && maxY > minY;
        }

        public int flags() {
            int f = 0;
            if (hasSlot) f |= 1;
            if (hasTouchMajor) f |= 2;
            if (hasPressure) f |= 4;
            if (hasBtnTouch) f |= 8;
            if (hasBtnToolFinger) f |= 16;
            return f;
        }

        public String shortName() {
            String n = (name == null || name.isEmpty()) ? "touchscreen" : name;
            return n + " · " + path + " · "
                    + (maxX - minX + 1) + "×" + (maxY - minY + 1);
        }
    }

    private static final Pattern DEVICE =
            Pattern.compile("^add device \\d+:\\s*(/dev/input/event\\d+)");
    private static final Pattern NAME =
            Pattern.compile("^name:\\s*\"(.*)\"");
    private static final Pattern MIN_MAX =
            Pattern.compile("min\\s+(-?\\d+),\\s*max\\s+(-?\\d+)");

    public static DeviceInfo detect() {
        String out = execRoot("/system/bin/getevent -pl 2>/dev/null");
        if (out == null || out.trim().isEmpty()) {
            out = execRoot("getevent -pl 2>/dev/null");
        }
        if (out == null || out.trim().isEmpty()) return null;

        List<DeviceInfo> devices = new ArrayList<>();
        DeviceInfo current = null;

        for (String raw : out.split("\\n")) {
            String line = raw.trim();

            Matcher deviceMatcher = DEVICE.matcher(line);
            if (deviceMatcher.find()) {
                if (current != null) devices.add(current);
                current = new DeviceInfo();
                current.path = deviceMatcher.group(1);
                continue;
            }
            if (current == null) continue;

            Matcher nameMatcher = NAME.matcher(line);
            if (nameMatcher.find()) {
                current.name = nameMatcher.group(1);
                continue;
            }

            String upper = line.toUpperCase();
            String lower = line.toLowerCase();

            if (upper.contains("INPUT_PROP_DIRECT")) current.direct = true;

            if (upper.contains("ABS_MT_SLOT") || lower.contains("002f")) {
                current.hasSlot = true;
            }
            if (upper.contains("ABS_MT_TRACKING_ID") || lower.contains("0039")) {
                current.hasTracking = true;
            }
            if (upper.contains("ABS_MT_TOUCH_MAJOR") || lower.contains("0030")) {
                current.hasTouchMajor = true;
            }
            if (upper.contains("ABS_MT_PRESSURE") || lower.contains("003a")) {
                current.hasPressure = true;
            }
            if (upper.contains("BTN_TOUCH") || lower.contains("014a")) {
                current.hasBtnTouch = true;
            }
            if (upper.contains("BTN_TOOL_FINGER") || lower.contains("0145")) {
                current.hasBtnToolFinger = true;
            }

            boolean xLine = upper.contains("ABS_MT_POSITION_X")
                    || lower.matches("^0035\\s*:.*");
            boolean yLine = upper.contains("ABS_MT_POSITION_Y")
                    || lower.matches("^0036\\s*:.*");

            if (xLine || yLine) {
                Matcher range = MIN_MAX.matcher(line);
                if (range.find()) {
                    int min = safeInt(range.group(1));
                    int max = safeInt(range.group(2));
                    if (xLine) {
                        current.minX = min;
                        current.maxX = max;
                    } else {
                        current.minY = min;
                        current.maxY = max;
                    }
                }
            }
        }
        if (current != null) devices.add(current);

        DeviceInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (DeviceInfo d : devices) {
            if (!d.valid()) continue;

            String n = d.name == null ? "" : d.name.toLowerCase();
            int score = 0;

            if (d.direct) score += 30;
            if (d.hasTracking) score += 12;
            if (d.hasSlot) score += 6;
            if (d.hasBtnTouch) score += 4;
            if (n.contains("touch")) score += 15;
            if (n.contains("screen")) score += 8;
            if (n.contains("goodix") || n.contains("fts")
                    || n.contains("synapt") || n.contains("novatek")
                    || n.contains("himax")) score += 8;
            if (n.contains("fingerprint") || n.contains("fp")) score -= 60;
            if (n.contains("pen") || n.contains("stylus")) score -= 30;
            if ((d.maxX - d.minX) >= 1000) score += 4;
            if ((d.maxY - d.minY) >= 2000) score += 4;

            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    public static boolean hasRoot() {
        String out = execRoot("id");
        return out != null && out.contains("uid=0");
    }

    public static String execRoot(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            if (!p.waitFor(6, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }

            BufferedReader br =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }
}
