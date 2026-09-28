package cn.stra.ace5pro.autoclicker;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class SpeedTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Deque<Long> taps = new ArrayDeque<>();

    private TextView cpsNow;
    private TextView cps5;
    private TextView peak;
    private TextView total;
    private TextView interval;
    private TextView testArea;

    private long totalCount = 0L;
    private long firstTap = 0L;
    private long lastTap = 0L;
    private int peakCps = 0;

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            handler.postDelayed(this, 100L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updater);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(updater);
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

    private TextView metric(String title) {
        TextView v = text(title + "\n0", 14, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.12f);
        v.setBackground(bg(Color.argb(210, 25, 30, 43), 18));
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        return v;
    }

    private void buildUi() {
        GradientDrawable rootBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(8, 11, 18), Color.rgb(13, 19, 31), Color.rgb(7, 9, 14)});

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(28), dp(18), dp(28));
        content.setBackground(rootBg);

        TextView back = text("‹  返回", 16, Color.rgb(166, 196, 255));
        back.setPadding(0, dp(4), 0, dp(8));
        back.setOnClickListener(v -> finish());
        content.addView(back);

        TextView title = text("点击速度测试", 30, Color.WHITE);
        title.setTypeface(null, 1);
        content.addView(title);

        TextView sub = text("把 STRA 点位放到下面测试区，或直接手点。实时统计 CPS 和点击间隔。", 13, Color.rgb(156, 168, 192));
        sub.setLineSpacing(0, 1.16f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(18));
        content.addView(sub, subLp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        cpsNow = metric("实时 CPS");
        peak = metric("峰值 CPS");
        row1.addView(cpsNow, new LinearLayout.LayoutParams(0, dp(82), 1f));
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, dp(82), 1f);
        m2.setMargins(dp(8), 0, 0, 0);
        row1.addView(peak, m2);
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        cps5 = metric("5 秒均速");
        total = metric("总点击");
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, dp(82));
        row2Lp.setMargins(0, dp(8), 0, 0);
        row2.addView(cps5, new LinearLayout.LayoutParams(0, dp(82), 1f));
        LinearLayout.LayoutParams m4 = new LinearLayout.LayoutParams(0, dp(82), 1f);
        m4.setMargins(dp(8), 0, 0, 0);
        row2.addView(total, m4);
        content.addView(row2, row2Lp);

        interval = metric("平均间隔");
        LinearLayout.LayoutParams intLp = new LinearLayout.LayoutParams(-1, dp(76));
        intLp.setMargins(0, dp(8), 0, 0);
        content.addView(interval, intLp);

        testArea = text("点击测试区\n\n把连点目标放在这里", 24, Color.WHITE);
        testArea.setTypeface(null, 1);
        testArea.setGravity(Gravity.CENTER);
        testArea.setBackground(bg(Color.argb(230, 26, 101, 220), 28));
        testArea.setPadding(dp(16), dp(24), dp(16), dp(24));
        testArea.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                registerTap();
                return true;
            }
            return true;
        });

        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, dp(270));
        testLp.setMargins(0, dp(16), 0, 0);
        content.addView(testArea, testLp);

        TextView reset = text("清零重新测试", 15, Color.WHITE);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(bg(Color.argb(220, 37, 43, 58), 18));
        reset.setOnClickListener(v -> reset());

        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, dp(52));
        resetLp.setMargins(0, dp(12), 0, 0);
        content.addView(reset, resetLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    private synchronized void registerTap() {
        long now = android.os.SystemClock.elapsedRealtimeNanos();

        taps.addLast(now);
        while (!taps.isEmpty() && now - taps.peekFirst() > 5_000_000_000L) {
            taps.removeFirst();
        }

        totalCount++;
        if (firstTap == 0L) firstTap = now;
        lastTap = now;
    }

    private synchronized void refreshStats() {
        long now = android.os.SystemClock.elapsedRealtimeNanos();

        while (!taps.isEmpty() && now - taps.peekFirst() > 5_000_000_000L) {
            taps.removeFirst();
        }

        int oneSecond = 0;
        for (Long t : taps) {
            if (now - t <= 1_000_000_000L) oneSecond++;
        }

        if (oneSecond > peakCps) peakCps = oneSecond;

        long elapsed = firstTap == 0L ? 0L : now - firstTap;
        double windowSeconds = Math.min(5.0, elapsed / 1_000_000_000.0);
        double fiveSecondRate = windowSeconds > 0.0
                ? taps.size() / windowSeconds
                : 0.0;
        double avgInterval = totalCount > 1 && lastTap > firstTap
                ? (lastTap - firstTap) / 1_000_000.0 / (double) (totalCount - 1)
                : 0.0;

        cpsNow.setText("实时 CPS\n" + oneSecond);
        peak.setText("峰值 CPS\n" + peakCps);
        cps5.setText(String.format(Locale.US, "5 秒均速\n%.1f", fiveSecondRate));
        total.setText("总点击\n" + totalCount);
        interval.setText(String.format(Locale.US, "平均间隔\n%.3f ms", avgInterval));
    }

    private synchronized void reset() {
        taps.clear();
        totalCount = 0L;
        firstTap = 0L;
        lastTap = 0L;
        peakCps = 0;
        refreshStats();
    }
}
