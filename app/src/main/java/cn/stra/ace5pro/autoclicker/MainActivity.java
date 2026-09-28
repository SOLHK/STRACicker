package cn.stra.ace5pro.autoclicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.system.Os;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
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
    private int bg, surface, surfaceAlt, ink, muted, primary, primaryInk, outline, good, danger;
    private View rootState, overlayState, engineState;
    private TextView beijingTime, beijingSource;

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refreshTime(); handler.postDelayed(this, 250L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        timeFmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        resolvePalette();
        styleSystemBars();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        buildUi();
        BeijingTimeManager.ensureSync(this);
    }

    @Override protected void onResume() { super.onResume(); refresh(); handler.post(ticker); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }

    private void resolvePalette() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bg = Color.rgb(dark ? 17 : 246, dark ? 19 : 247, dark ? 25 : 251);
        surface = Color.rgb(dark ? 30 : 255, dark ? 33 : 255, dark ? 42 : 255);
        surfaceAlt = Color.rgb(dark ? 39 : 235, dark ? 43 : 239, dark ? 54 : 247);
        ink = Color.rgb(dark ? 240 : 27, dark ? 237 : 30, dark ? 246 : 42);
        muted = Color.rgb(dark ? 181 : 100, dark ? 184 : 105, dark ? 197 : 121);
        primary = Color.rgb(dark ? 171 : 62, dark ? 190 : 91, dark ? 255 : 214);
        primaryInk = Color.rgb(dark ? 23 : 255, dark ? 36 : 255, dark ? 61 : 255);
        outline = Color.rgb(dark ? 72 : 222, dark ? 77 : 225, dark ? 91 : 234);
        good = Color.rgb(dark ? 131 : 31, dark ? 213 : 113, dark ? 164 : 87);
        danger = Color.rgb(dark ? 255 : 179, dark ? 180 : 38, dark ? 171 : 54);
    }

    private void styleSystemBars() {
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable shape(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private GradientDrawable gradient(int a, int b, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setCornerRadius(dp(radius)); return d;
    }

    private TextView label(String s, float size, int color) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); return v;
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(50), dp(20), dp(28)); page.setBackgroundColor(bg);

        LinearLayout brandRow = new LinearLayout(this); brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label("S", 16, Color.WHITE); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER); mark.setBackground(shape(primary, 15));
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView brand = label("STRA  ·  CLICK TOOLS", 12, muted); brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-2, -2); brandLp.leftMargin = dp(10);
        brandRow.addView(brand, brandLp); page.addView(brandRow);

        TextView title = label("连点控制台", 30, ink); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2); titleLp.topMargin = dp(18);
        page.addView(title, titleLp);
        TextView subtitle = label("管理点位、节奏与运行状态", 14, muted);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(4); subLp.bottomMargin = dp(18);
        page.addView(subtitle, subLp);

        LinearLayout clock = new LinearLayout(this); clock.setOrientation(LinearLayout.VERTICAL);
        clock.setPadding(dp(20), dp(17), dp(20), dp(17)); clock.setGravity(Gravity.CENTER_VERTICAL);
        clock.setBackground(gradient(primary, Color.rgb(126, 173, 255), 27));
        TextView clockLabel = label("北京时间  ·  NTP 校时", 12, Color.rgb(235, 243, 255));
        beijingTime = label("--:--:--", 38, Color.WHITE); beijingTime.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        beijingSource = label("正在联网校时…", 11.5f, Color.rgb(238, 245, 255));
        clock.addView(clockLabel); LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-1, -2); timeLp.topMargin = dp(2);
        clock.addView(beijingTime, timeLp); LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2); sourceLp.topMargin = dp(2);
        clock.addView(beijingSource, sourceLp); clock.setOnClickListener(v -> startActivity(new Intent(this, BeijingTimeActivity.class)));
        page.addView(clock, new LinearLayout.LayoutParams(-1, dp(126)));

        LinearLayout section = new LinearLayout(this); section.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = label("运行环境", 16, ink); sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.addView(sectionTitle); TextView live = label("实时状态", 11, muted); live.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        section.addView(live, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, -2); sectionLp.topMargin = dp(20); sectionLp.bottomMargin = dp(9);
        page.addView(section, sectionLp);

        LinearLayout statusCard = new LinearLayout(this); statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(7), dp(16), dp(7)); statusCard.setBackground(shape(surface, 23));
        rootState = statusRow("ROOT 权限", "正在检测…");
        overlayState = statusRow("悬浮窗", "正在检测…");
        engineState = statusRow("点击引擎", "正在检测…");
        statusCard.addView(rootState); statusCard.addView(divider()); statusCard.addView(overlayState); statusCard.addView(divider()); statusCard.addView(engineState);
        page.addView(statusCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout start = action("开启悬浮控制器", "添加点位并开始点击", "↗", primary, primaryInk);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(76)); startLp.topMargin = dp(18);
        page.addView(start, startLp); start.setOnClickListener(v -> startOverlay());

        LinearLayout emergency = action("紧急停止", "结束正在运行的点击任务", "■", surface, danger);
        LinearLayout.LayoutParams emergencyLp = new LinearLayout.LayoutParams(-1, dp(70)); emergencyLp.topMargin = dp(9);
        page.addView(emergency, emergencyLp); emergency.setOnClickListener(v -> emergencyStop());

        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout speed = tile("速度测试", "CPS 与点击间隔", "↗");
        LinearLayout access = tile("悬浮权限", "授权与窗口管理", "◉");
        tools.addView(speed, new LinearLayout.LayoutParams(0, dp(108), 1));
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(0, dp(108), 1); accessLp.leftMargin = dp(10);
        tools.addView(access, accessLp); LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, -2); toolsLp.topMargin = dp(10);
        page.addView(tools, toolsLp);
        speed.setOnClickListener(v -> startActivity(new Intent(this, SpeedTestActivity.class)));
        access.setOnClickListener(v -> openOverlaySettings());

        TextView device = label(deviceText(), 11, muted); device.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams deviceLp = new LinearLayout.LayoutParams(-1, -2); deviceLp.topMargin = dp(18); page.addView(device, deviceLp);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.addView(page); setContentView(scroll);
    }

    private View divider() { View v = new View(this); v.setBackgroundColor(outline); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.leftMargin = dp(38); v.setLayoutParams(p); return v; }

    private LinearLayout statusRow(String name, String initial) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(10), 0, dp(10));
        TextView dot = label("●", 10, primary); row.addView(dot, new LinearLayout.LayoutParams(dp(22), -2));
        TextView title = label(name, 13, ink); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = label(initial, 12, muted); state.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); row.addView(state);
        row.setTag(state); return row;
    }

    private LinearLayout action(String title, String subtitle, String iconText, int fill, int fg) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(17), dp(10), dp(14), dp(10)); row.setBackground(shape(fill, 23));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 16, fg); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); TextView s = label(subtitle, 12, fg == primaryInk ? Color.rgb(236, 244, 255) : muted);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2); sLp.topMargin = dp(3); copy.addView(t); copy.addView(s, sLp);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); TextView icon = label(iconText, 21, fg); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(fill == primary ? Color.rgb(80, 133, 237) : surfaceAlt, 18));
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46))); return row;
    }

    private LinearLayout tile(String title, String sub, String iconText) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(13), dp(14), dp(12)); card.setBackground(shape(surface, 22));
        TextView icon = label(iconText, 18, primary); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(surfaceAlt, 16)); card.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = label(title, 14, ink); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2); tLp.topMargin = dp(7); card.addView(t, tLp);
        TextView s = label(sub, 10.5f, muted); LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2); sLp.topMargin = dp(2); card.addView(s, sLp); return card;
    }

    private String deviceText() {
        String kernel; try { kernel = Os.uname().release; } catch (Throwable t) { kernel = System.getProperty("os.version", "unknown"); }
        return Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE + "\n" + kernel;
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show(); return; }
        BeijingTimeManager.ensureSync(this); Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "STRA 悬浮控制器已开启", Toast.LENGTH_SHORT).show();
    }

    private void emergencyStop() {
        NativeTouchEngine.hardStop(this); stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "已发送急停信号", Toast.LENGTH_SHORT).show(); refresh();
    }

    private void openOverlaySettings() {
        if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        else Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show();
    }

    private void setState(View row, String value, boolean positive) {
        TextView state = (TextView) row.getTag(); state.setText(value); state.setTextColor(positive ? good : muted);
    }

    private void refresh() {
        if (overlayState != null) setState(overlayState, Settings.canDrawOverlays(this) ? "已授权" : "未授权", Settings.canDrawOverlays(this));
        NativeTouchEngine.CONTROL.execute(() -> {
            if (isFinishing() || isDestroyed()) return;
            boolean root = TouchDeviceDetector.hasRoot();
            runOnUiThread(() -> setState(rootState, root ? "已授权" : "未授权", root));
            if (!root) { runOnUiThread(() -> setState(engineState, "等待 Root", false)); return; }
            boolean ok = new NativeTouchEngine(this).probeSupport();
            runOnUiThread(() -> setState(engineState, ok ? "uinput 可用" : "uinput 不可用", ok));
        });
        BeijingTimeManager.ensureSync(this);
    }

    private void refreshTime() {
        BeijingTimeManager.ensureSync(this); long now = BeijingTimeManager.nowMs(this); beijingTime.setText(timeFmt.format(new Date(now)));
        if (BeijingTimeManager.isSyncing()) beijingSource.setText("正在联网校时…");
        else if (BeijingTimeManager.isSynced(this)) beijingSource.setText("已校时  ·  " + BeijingTimeManager.source(this) + "  ·  RTT " + BeijingTimeManager.rttMs(this) + " ms");
        else beijingSource.setText("未完成联网校时 · 暂用系统时钟");
    }
}
