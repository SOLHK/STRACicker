package cn.stra.ace5pro.autoclicker;

import android.app.Activity;
import android.graphics.Color;
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

    private void buildUi() {
        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(7, 10, 17), Color.rgb(13, 20, 34), Color.rgb(7, 9, 14)});

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(28));
        root.setBackground(rootBg);

        TextView back = text("‹  返回", 16, Color.rgb(166, 196, 255));
        back.setPadding(0, dp(4), 0, dp(8));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = text("北京时间", 30, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = text("联网 NTP 校时 · Asia/Shanghai · UTC+8", 13, Color.rgb(156, 168, 192));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(18));
        root.addView(sub, subLp);

        LinearLayout clockCard = new LinearLayout(this);
        clockCard.setOrientation(LinearLayout.VERTICAL);
        clockCard.setGravity(Gravity.CENTER);
        clockCard.setPadding(dp(16), dp(24), dp(16), dp(24));
        clockCard.setBackground(bg(Color.argb(222, 22, 30, 48), 28));

        timeText = text("--:--:--", 54, Color.WHITE);
        timeText.setTypeface(null, 1);
        timeText.setGravity(Gravity.CENTER);

        msText = text(".000", 20, Color.rgb(119, 195, 255));
        msText.setGravity(Gravity.CENTER);

        dateText = text("等待时间…", 15, Color.rgb(189, 199, 218));
        dateText.setGravity(Gravity.CENTER);

        clockCard.addView(timeText);
        clockCard.addView(msText);

        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, -2);
        dateLp.setMargins(0, dp(10), 0, 0);
        clockCard.addView(dateText, dateLp);

        root.addView(clockCard, new LinearLayout.LayoutParams(-1, dp(230)));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        infoCard.setBackground(bg(Color.argb(214, 24, 29, 40), 20));

        statusText = text("校时状态：正在连接…", 14, Color.WHITE);
        sourceText = text("时间源：等待中", 13, Color.rgb(155, 169, 193));

        infoCard.addView(statusText);

        LinearLayout.LayoutParams srcLp = new LinearLayout.LayoutParams(-1, -2);
        srcLp.setMargins(0, dp(8), 0, 0);
        infoCard.addView(sourceText, srcLp);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.setMargins(0, dp(12), 0, 0);
        root.addView(infoCard, infoLp);

        TextView sync = text("立即重新联网校时", 15, Color.WHITE);
        sync.setGravity(Gravity.CENTER);
        sync.setBackground(bg(Color.argb(230, 28, 100, 216), 18));
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
                Color.rgb(138, 151, 175));
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
