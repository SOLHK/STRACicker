package cn.stra.ace5pro.autoclicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.system.Os;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private TextView rootState;
    private TextView overlayState;
    private TextView engineState;
    private TextView beijingTime;
    private TextView beijingSource;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshTime();
            handler.postDelayed(this, 250L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        timeFmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }

        buildUi();
        BeijingTimeManager.ensureSync(this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(55, 255, 255, 255));
        return d;
    }

    private TextView text(String s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private TextView action(String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        return null;
    }

    private LinearLayout actionCard(String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(13), dp(18), dp(13));

        int top = title.contains("急停") ? Color.rgb(163, 43, 59)
                : title.contains("开启") ? Color.rgb(24, 111, 235)
                : Color.rgb(35, 48, 69);
        int bottom = title.contains("急停") ? Color.rgb(111, 34, 52)
                : title.contains("开启") ? Color.rgb(27, 72, 158)
                : Color.rgb(20, 28, 43);
        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), Color.argb(72, 210, 229, 255));
        card.setBackground(cardBg);

        TextView t = text(title, 17, Color.WHITE);
        t.setTypeface(null, 1);
        TextView s = text(sub, 13, Color.rgb(205, 216, 233));
        s.setPadding(0, dp(4), 0, 0);

        card.addView(t);
        card.addView(s);
        return card;
    }

    private void buildUi() {
        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(6, 9, 16),
                        Color.rgb(11, 18, 31),
                        Color.rgb(7, 10, 16)
                });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(30), dp(18), dp(28));
        root.setBackground(rootBg);

        TextView brand = text("STRA", 15, Color.rgb(104, 172, 255));
        brand.setTypeface(null, 1);
        root.addView(brand);

        TextView title = text("连点控制台", 31, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = text(
                "STRA  ·  Ace 5 Pro 专用  ·  Root / uinput",
                13.5f,
                Color.rgb(177, 194, 220));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(4), 0, dp(16));
        root.addView(sub, subLp);

        LinearLayout clockCard = new LinearLayout(this);
        clockCard.setOrientation(LinearLayout.VERTICAL);
        clockCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        clockCard.setBackground(bg(Color.argb(225, 20, 29, 47), 24));
        clockCard.setOnClickListener(v ->
                startActivity(new Intent(this, BeijingTimeActivity.class)));

        TextView clockLabel = text("北京时间", 12.5f, Color.rgb(125, 185, 255));
        beijingTime = text("--:--:--", 38, Color.WHITE);
        beijingTime.setTypeface(null, 1);
        beijingSource = text("正在联网校时…", 11.5f, Color.rgb(150, 164, 188));

        clockCard.addView(clockLabel);
        clockCard.addView(beijingTime);

        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLp.setMargins(0, dp(2), 0, 0);
        clockCard.addView(beijingSource, sourceLp);

        root.addView(clockCard, new LinearLayout.LayoutParams(-1, dp(126)));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setBackground(bg(Color.argb(208, 23, 28, 39), 20));

        rootState = text("Root    检测中…", 14, Color.WHITE);
        overlayState = text("悬浮窗  检测中…", 14, Color.WHITE);
        engineState = text("引擎    检测中…", 14, Color.WHITE);

        statusCard.addView(rootState);

        LinearLayout.LayoutParams st2 = new LinearLayout.LayoutParams(-1, -2);
        st2.setMargins(0, dp(7), 0, 0);
        statusCard.addView(overlayState, st2);

        LinearLayout.LayoutParams st3 = new LinearLayout.LayoutParams(-1, -2);
        st3.setMargins(0, dp(7), 0, 0);
        statusCard.addView(engineState, st3);

        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(10), 0, 0);
        root.addView(statusCard, statusLp);

        LinearLayout start = actionCard(
                "开启悬浮控制器",
                "添加点位、开始 / 停止、强制结束、显示北京时间");
        start.setOnClickListener(v -> startOverlay());

        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-1, dp(76));
        a1.setMargins(0, dp(12), 0, 0);
        root.addView(start, a1);

        LinearLayout emergency = actionCard(
                "紧急停止 / 关闭点击",
                "立即发出停止信号，并结束 Root 点击进程");
        emergency.setOnClickListener(v -> emergencyStop());
        LinearLayout.LayoutParams emergencyLp = new LinearLayout.LayoutParams(-1, dp(76));
        emergencyLp.setMargins(0, dp(8), 0, 0);
        root.addView(emergency, emergencyLp);

        LinearLayout speed = actionCard(
                "点击速度测试",
                "实时 CPS、峰值、1 秒均速、平均点击间隔");
        speed.setOnClickListener(v ->
                startActivity(new Intent(this, SpeedTestActivity.class)));

        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-1, dp(76));
        a2.setMargins(0, dp(8), 0, 0);
        root.addView(speed, a2);

        LinearLayout time = actionCard(
                "北京时间详情",
                "NTP 时间源、毫秒显示、RTT、手动重新校时");
        time.setOnClickListener(v ->
                startActivity(new Intent(this, BeijingTimeActivity.class)));

        LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(-1, dp(76));
        a3.setMargins(0, dp(8), 0, 0);
        root.addView(time, a3);

        LinearLayout permissions = actionCard(
                "悬浮窗权限",
                "未授权时点这里进入系统设置");
        permissions.setOnClickListener(v -> openOverlaySettings());

        LinearLayout.LayoutParams a4 = new LinearLayout.LayoutParams(-1, dp(70));
        a4.setMargins(0, dp(8), 0, 0);
        root.addView(permissions, a4);

        TextView device = text(deviceText(), 11.5f, Color.rgb(111, 125, 149));
        device.setLineSpacing(0, 1.14f);

        LinearLayout.LayoutParams deviceLp = new LinearLayout.LayoutParams(-1, -2);
        deviceLp.setMargins(dp(2), dp(16), dp(2), 0);
        root.addView(device, deviceLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String deviceText() {
        String kernel;
        try {
            kernel = Os.uname().release;
        } catch (Throwable t) {
            kernel = System.getProperty("os.version", "unknown");
        }

        return Build.MANUFACTURER + " " + Build.MODEL
                + "  ·  Android " + Build.VERSION.RELEASE
                + "\n" + kernel;
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }

        BeijingTimeManager.ensureSync(this);

        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }

        Toast.makeText(this, "STRA 悬浮控制器已开启", Toast.LENGTH_SHORT).show();
    }

    private void emergencyStop() {
        NativeTouchEngine.hardStop(this);
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "已发送急停信号", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void openOverlaySettings() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void refresh() {
        if (overlayState != null) {
            overlayState.setText(Settings.canDrawOverlays(this)
                    ? "悬浮窗  已授权 ✓"
                    : "悬浮窗  未授权");
        }

        new Thread(() -> {
            boolean root = TouchDeviceDetector.hasRoot();

            runOnUiThread(() ->
                    rootState.setText(root
                            ? "Root    已授权 ✓"
                            : "Root    未授权"));

            if (!root) {
                runOnUiThread(() ->
                        engineState.setText("引擎    等待 Root"));
                return;
            }

            NativeTouchEngine engine = new NativeTouchEngine(this);
            boolean ok = engine.probeSupport();

            runOnUiThread(() ->
                    engineState.setText(ok
                            ? "引擎    uinput 可用 ✓"
                            : "引擎    uinput 不可用"));
        }, "stra-status").start();

        BeijingTimeManager.ensureSync(this);
    }

    private void refreshTime() {
        BeijingTimeManager.ensureSync(this);

        long now = BeijingTimeManager.nowMs(this);
        beijingTime.setText(timeFmt.format(new Date(now)));

        if (BeijingTimeManager.isSyncing()) {
            beijingSource.setText("正在联网校时…");
        } else if (BeijingTimeManager.isSynced(this)) {
            beijingSource.setText(
                    "已校时 · "
                            + BeijingTimeManager.source(this)
                            + " · RTT "
                            + BeijingTimeManager.rttMs(this)
                            + " ms");
        } else {
            beijingSource.setText("未完成联网校时 · 暂用系统时钟");
        }
    }
}
