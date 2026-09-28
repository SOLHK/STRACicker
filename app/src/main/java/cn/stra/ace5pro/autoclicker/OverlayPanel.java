package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

/** Compact start/stop and Beijing-time overlay. */
final class OverlayPanel extends LinearLayout {
    static final int MINI_WIDTH_DP = 122;
    static final int MINI_HEIGHT_DP = 46;
    final LinearLayout expanded, header, mini;
    final TextView title, clock, clockStatus, status, miniClock;
    final MaterialButton collapse, close, start, stop;
    final int surface, ink, muted, accent, onAccent, container;
    private final GradientDrawable expandedBackground;

    OverlayPanel(Context c) {
        super(c);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        surface = Color.parseColor(dark ? "#202735" : "#FFFFFF");
        container = Color.parseColor(dark ? "#313D50" : "#E5EBF7");
        ink = Color.parseColor(dark ? "#E7EAF0" : "#19202C");
        muted = Color.parseColor(dark ? "#ACB6C8" : "#596579");
        accent = Color.parseColor(dark ? "#ADC6FF" : "#315DA8");
        onAccent = Color.parseColor(dark ? "#122D56" : "#FFFFFF");
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(9), dp(14), dp(10));
        expandedBackground = shape(surface, 28);
        expandedBackground.setStroke(dp(1), Color.parseColor(dark ? "#46536A" : "#DCE4F2"));
        setBackground(expandedBackground);

        expanded = column(); addView(expanded, new LayoutParams(-1, -2));
        header = new LinearLayout(c); header.setGravity(Gravity.CENTER_VERTICAL);
        title = text("STRA · 点击控制", 15, ink); title.setTypeface(null, 1);
        header.addView(title, new LayoutParams(0, dp(42), 1));
        collapse = button("缩小", false); close = button("关闭", true);
        collapse.setContentDescription("缩小悬浮窗"); close.setContentDescription("关闭悬浮窗");
        header.addView(collapse, new LayoutParams(dp(58), dp(42)));
        header.addView(close, new LayoutParams(dp(52), dp(42))); expanded.addView(header);

        LinearLayout clockRow = new LinearLayout(c); clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clock = text("--:--:--", 27, ink); clock.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        clock.setFontFeatureSettings("tnum"); clockRow.addView(clock, new LayoutParams(0, dp(54), 1));
        clockStatus = text("北京时间", 11, muted); clockStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clockRow.addView(clockStatus); expanded.addView(clockRow);

        status = text("准备就绪", 12, muted);
        status.setPadding(dp(2), dp(3), 0, dp(8)); expanded.addView(status);
        LinearLayout actions = new LinearLayout(c);
        start = button("开始", false); start.setTextSize(15);
        start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent)); start.setTextColor(onAccent);
        stop = button("停止", false); stop.setTextSize(15); stop.setTextColor(ink);
        actions.addView(start, new LayoutParams(0, dp(50), 1));
        LayoutParams stopLp = new LayoutParams(0, dp(50), 1); stopLp.leftMargin = dp(8); actions.addView(stop, stopLp);
        expanded.addView(actions);

        mini = new LinearLayout(c); mini.setOrientation(HORIZONTAL); mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(7), 0, dp(10), 0);
        GradientDrawable miniBackground = shape(surface, 23); miniBackground.setStroke(dp(1), accent); mini.setBackground(miniBackground);
        ImageView mark = new ImageView(c); mark.setImageResource(R.drawable.ic_stra_mark);
        mini.addView(mark, new LayoutParams(dp(30), dp(30)));
        miniClock = text("--:--:--", 12, ink); miniClock.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0)); miniClock.setFontFeatureSettings("tnum");
        miniClock.setContentDescription("北京时间"); LayoutParams timeLp = new LayoutParams(-2, -2); timeLp.leftMargin = dp(6); mini.addView(miniClock, timeLp);
        mini.setContentDescription("轻点展开，拖动移动位置"); mini.setVisibility(GONE);
        addView(mini, new LayoutParams(dp(MINI_WIDTH_DP), dp(MINI_HEIGHT_DP)));
        setCollapsed(false);
    }
    void setCollapsed(boolean collapsed) {
        expanded.setVisibility(collapsed ? GONE : VISIBLE); mini.setVisibility(collapsed ? VISIBLE : GONE);
        setPadding(collapsed ? 0 : dp(14), collapsed ? 0 : dp(9), collapsed ? 0 : dp(14), collapsed ? 0 : dp(10));
        setBackground(collapsed ? null : expandedBackground); setElevation(dp(collapsed ? 8 : 14));
    }
    void busy(boolean active, boolean stopping) {
        start.setEnabled(!active); start.setAlpha(active ? .55f : 1f);
        start.setText(active ? (stopping ? "正在停止…" : "运行中") : "开始");
        stop.setEnabled(active && !stopping); stop.setAlpha(active && !stopping ? 1f : .45f);
    }
    private MaterialButton button(String value, boolean textOnly) {
        MaterialButton b = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonStyle);
        b.setText(value); b.setAllCaps(false); b.setTextSize(12); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(6), 0, dp(6), 0); b.setInsetTop(0); b.setInsetBottom(0); b.setCornerRadius(dp(24)); b.setStrokeWidth(0);
        b.setTextColor(accent); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(textOnly ? Color.TRANSPARENT : container)); return b;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(VERTICAL); return l; }
    private TextView text(String s, float sp, int color) { TextView t = new TextView(getContext()); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
}
