package cn.stra.ace5pro.autoclicker;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class SpeedTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Deque<Long> taps = new ArrayDeque<>();

    private TextView cpsNow;
    private TextView cps1;
    private TextView peak;
    private TextView total;
    private TextView interval;
    private TextView testArea;

    private long totalCount = 0L;
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
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bgColor = Color.rgb(dark ? 17 : 246, dark ? 19 : 247, dark ? 25 : 251);
        surfaceColor = Color.rgb(dark ? 30 : 255, dark ? 33 : 255, dark ? 42 : 255);
        inkColor = Color.rgb(dark ? 240 : 27, dark ? 237 : 30, dark ? 246 : 42);
        mutedColor = Color.rgb(dark ? 181 : 100, dark ? 184 : 105, dark ? 197 : 121);
        primaryColor = Color.rgb(dark ? 171 : 62, dark ? 190 : 91, dark ? 255 : 214);
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

    private int bgColor = Color.rgb(246, 247, 251);
    private int surfaceColor = Color.WHITE;
    private int inkColor = Color.rgb(27, 30, 42);
    private int mutedColor = Color.rgb(100, 105, 121);
    private int primaryColor = Color.rgb(62, 91, 214);

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(CharSequence s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private TextView metric(String title) {
        TextView v = text(metricText(title, "0"), 14, inkColor);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.14f);
        v.setBackground(bg(surfaceColor, 23));
        v.setPadding(dp(10), dp(12), dp(10), dp(12));
        return v;
    }

    private SpannableString metricText(String title, String value) {
        String valueText = title + "\n" + value;
        SpannableString styled = new SpannableString(valueText);
        int split = title.length();
        styled.setSpan(new RelativeSizeSpan(.76f), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(mutedColor), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(Typeface.BOLD), split + 1, valueText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new RelativeSizeSpan(1.22f), split + 1, valueText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    private void buildUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(50), dp(20), dp(30));
        content.setBackgroundColor(bgColor);

        TextView back = text("‹   返回", 14, primaryColor);
        back.setPadding(0, dp(4), 0, dp(12));
        back.setOnClickListener(v -> finish());
        content.addView(back);

        TextView title = text("点击速度", 30, inkColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView sub = text("把悬浮点位放进测试区，实时查看点击节奏。", 13, mutedColor);
        sub.setLineSpacing(0, 1.16f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(5), 0, dp(18));
        content.addView(sub, subLp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        cpsNow = metric("实时 CPS");
        peak = metric("峰值 CPS");
        row1.addView(cpsNow, new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, dp(92), 1f);
        m2.setMargins(dp(8), 0, 0, 0);
        row1.addView(peak, m2);
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        cps1 = metric("1 秒均速");
        total = metric("总点击");
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, dp(92));
        row2Lp.setMargins(0, dp(9), 0, 0);
        row2.addView(cps1, new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams m4 = new LinearLayout.LayoutParams(0, dp(92), 1f);
        m4.setMargins(dp(8), 0, 0, 0);
        row2.addView(total, m4);
        content.addView(row2, row2Lp);

        interval = metric("平均间隔");
        LinearLayout.LayoutParams intLp = new LinearLayout.LayoutParams(-1, dp(72));
        intLp.setMargins(0, dp(9), 0, 0);
        content.addView(interval, intLp);

        testArea = text("点击测试区\n\n将点位放在这里", 23, Color.WHITE);
        testArea.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        testArea.setGravity(Gravity.CENTER);
        GradientDrawable testBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(62, 91, 214), Color.rgb(79, 126, 237)});
        testBg.setCornerRadius(dp(30));
        testArea.setBackground(testBg);
        testArea.setPadding(dp(16), dp(24), dp(16), dp(24));
        testArea.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                registerTap();
                return true;
            }
            return true;
        });

        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, dp(260));
        testLp.setMargins(0, dp(16), 0, 0);
        content.addView(testArea, testLp);

        TextView reset = text("清零重新测试", 14, primaryColor);
        reset.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(bg(surfaceColor, 20));
        reset.setOnClickListener(v -> reset());

        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, dp(54));
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
        while (!taps.isEmpty() && now - taps.peekFirst() > 1_000_000_000L) {
            taps.removeFirst();
        }

        totalCount++;
    }

    private synchronized void refreshStats() {
        long now = android.os.SystemClock.elapsedRealtimeNanos();

        while (!taps.isEmpty() && now - taps.peekFirst() > 1_000_000_000L) {
            taps.removeFirst();
        }

        int oneSecond = taps.size();

        if (oneSecond > peakCps) peakCps = oneSecond;

        Long oldest = taps.peekFirst();
        Long newest = taps.peekLast();
        double avgInterval = taps.size() > 1 && newest != null && oldest != null
                ? (newest - oldest) / 1_000_000.0 / (double) (taps.size() - 1)
                : 0.0;

        cpsNow.setText(metricText("实时 CPS", String.valueOf(oneSecond)));
        peak.setText(metricText("峰值 CPS", String.valueOf(peakCps)));
        cps1.setText(metricText("1 秒均速", oneSecond + " 次/秒"));
        total.setText(metricText("总点击", String.valueOf(totalCount)));
        interval.setText(metricText("平均间隔", String.format(Locale.US, "%.3f ms", avgInterval)));
    }

    private synchronized void reset() {
        taps.clear();
        totalCount = 0L;
        peakCps = 0;
        refreshStats();
    }
}
