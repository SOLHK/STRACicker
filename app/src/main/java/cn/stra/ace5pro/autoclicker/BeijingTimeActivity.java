package cn.stra.ace5pro.autoclicker;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class BeijingTimeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView timeText;
    private TextView msText;
    private TextView dateText;
    private TextView statusText;
    private TextView sourceText;

    private final TimeZone beijing = TimeZone.getTimeZone("Asia/Shanghai");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy年MM月dd日  EEEE", Locale.CHINA);
    private int bgColor, surfaceColor, inkColor, mutedColor, primaryColor;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshClock();
            handler.postDelayed(this, 50L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        timeFmt.setTimeZone(beijing);
        dateFmt.setTimeZone(beijing);

        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bgColor = Color.rgb(dark ? 17 : 246, dark ? 19 : 247, dark ? 25 : 251);
        surfaceColor = Color.rgb(dark ? 30 : 255, dark ? 33 : 255, dark ? 42 : 255);
        inkColor = Color.rgb(dark ? 240 : 27, dark ? 237 : 30, dark ? 246 : 42);
        mutedColor = Color.rgb(dark ? 181 : 100, dark ? 184 : 105, dark ? 197 : 121);
        primaryColor = Color.rgb(dark ? 171 : 62, dark ? 190 : 91, dark ? 255 : 214);

        buildUi();
        BeijingTimeManager.ensureSync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        return d;
    }

    private TextView text(String s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(50), dp(20), dp(30));
        root.setBackgroundColor(bgColor);

        TextView back = text("‹   返回", 14, primaryColor);
        back.setPadding(0, dp(4), 0, dp(12));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = text("北京时间", 30, inkColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView sub = text("联网校时  ·  Asia/Shanghai  ·  UTC+8", 13, mutedColor);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(18));
        root.addView(sub, subLp);

        LinearLayout clockCard = new LinearLayout(this);
        clockCard.setOrientation(LinearLayout.VERTICAL);
        clockCard.setGravity(Gravity.CENTER);
        clockCard.setPadding(dp(16), dp(24), dp(16), dp(24));
        GradientDrawable clockBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(62, 91, 214), Color.rgb(83, 132, 238)});
        clockBg.setCornerRadius(dp(30));
        clockCard.setBackground(clockBg);

        timeText = text("--:--:--", 48, Color.WHITE);
        timeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        timeText.setGravity(Gravity.CENTER);

        msText = text(".000", 19, Color.rgb(227, 237, 255));
        msText.setGravity(Gravity.CENTER);

        dateText = text("等待时间…", 14, Color.rgb(237, 243, 255));
        dateText.setGravity(Gravity.CENTER);

        clockCard.addView(timeText);
        clockCard.addView(msText);

        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, -2);
        dateLp.setMargins(0, dp(10), 0, 0);
        clockCard.addView(dateText, dateLp);

        root.addView(clockCard, new LinearLayout.LayoutParams(-1, dp(224)));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        infoCard.setBackground(bg(surfaceColor, 24));

        statusText = text("校时状态：正在连接…", 14, inkColor);
        sourceText = text("时间源：等待中", 13, mutedColor);

        infoCard.addView(statusText);

        LinearLayout.LayoutParams srcLp = new LinearLayout.LayoutParams(-1, -2);
        srcLp.setMargins(0, dp(8), 0, 0);
        infoCard.addView(sourceText, srcLp);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.setMargins(0, dp(12), 0, 0);
        root.addView(infoCard, infoLp);

        TextView sync = text("立即重新校时", 14, Color.WHITE);
        sync.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sync.setGravity(Gravity.CENTER);
        sync.setBackground(bg(primaryColor, 22));
        sync.setOnClickListener(v -> {
            statusText.setText("校时状态：正在重新同步…");
            BeijingTimeManager.forceSync(this, () ->
                    runOnUiThread(this::refreshClock));
        });

        LinearLayout.LayoutParams syncLp = new LinearLayout.LayoutParams(-1, dp(54));
        syncLp.setMargins(0, dp(12), 0, 0);
        root.addView(sync, syncLp);

        TextView note = text(
                "时间优先从阿里云 / 腾讯云公网 NTP 获取。校时成功后使用 Android 单调时钟持续走时，" +
                "避免手机系统时间被手动修改或轻微漂移影响显示。",
                12.5f,
                mutedColor);
        note.setLineSpacing(0, 1.18f);

        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(2), dp(16), dp(2), 0);
        root.addView(note, noteLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void refreshClock() {
        BeijingTimeManager.ensureSync(this);

        long now = BeijingTimeManager.nowMs(this);
        Date date = new Date(now);

        timeText.setText(timeFmt.format(date));
        msText.setText(String.format(Locale.US, ".%03d", now % 1000L));
        dateText.setText(dateFmt.format(date));

        boolean synced = BeijingTimeManager.isSynced(this);
        boolean syncing = BeijingTimeManager.isSyncing();

        if (syncing) {
            statusText.setText("校时状态：正在联网同步…");
        } else if (synced) {
            long age = BeijingTimeManager.ageMs(this);
            long rtt = BeijingTimeManager.rttMs(this);

            String ageText;
            if (age < 0) {
                ageText = "刚刚";
            } else if (age < 60_000L) {
                ageText = (age / 1000L) + " 秒前";
            } else {
                ageText = (age / 60_000L) + " 分钟前";
            }

            statusText.setText("校时状态：已同步 ✓  ·  " + ageText + "  ·  RTT " + rtt + " ms");
        } else {
            statusText.setText("校时状态：未联网校时，当前暂用系统时钟");
        }

        sourceText.setText("时间源：" + BeijingTimeManager.source(this));
    }
}
