package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The only floating UI: a draggable app mark and the Beijing clock. */
final class OverlayPanel extends LinearLayout {
    static final int MINI_WIDTH_DP = 122;
    static final int MINI_HEIGHT_DP = 46;
    final LinearLayout mini;
    final ImageView mark;
    final TextView miniClock;
    private final GradientDrawable pillBackground;
    private final int idleStroke;
    private final int runningStroke;
    private final int timeColor;

    OverlayPanel(Context context) {
        super(context);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int surface = Color.parseColor(dark ? "#202735" : "#FFFFFF");
        timeColor = Color.parseColor(dark ? "#E7EAF0" : "#19202C");
        idleStroke = Color.parseColor(dark ? "#ADC6FF" : "#315DA8");
        runningStroke = Color.parseColor(dark ? "#7FE0B1" : "#16845B");
        mini = new LinearLayout(context);
        mini.setOrientation(HORIZONTAL);
        mini.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(7), 0, dp(10), 0);
        pillBackground = new GradientDrawable();
        pillBackground.setColor(surface);
        pillBackground.setCornerRadius(dp(24));
        pillBackground.setStroke(dp(1), idleStroke);
        mini.setBackground(pillBackground);
        mini.setElevation(dp(8));

        mark = new ImageView(context);
        mark.setImageResource(R.drawable.ic_stra_mark);
        mini.addView(mark, new LayoutParams(dp(30), dp(30)));

        miniClock = new TextView(context);
        miniClock.setText("--:--:--");
        miniClock.setTextSize(12);
        miniClock.setTextColor(timeColor);
        miniClock.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        miniClock.setFontFeatureSettings("tnum");
        miniClock.setContentDescription("北京时间");
        LayoutParams timeLp = new LayoutParams(-2, -2);
        timeLp.leftMargin = dp(6);
        mini.addView(miniClock, timeLp);
        mini.setContentDescription("点按开始，拖动移动位置");
        addView(mini, new LayoutParams(dp(MINI_WIDTH_DP), dp(MINI_HEIGHT_DP)));
        setElevation(dp(8));
    }

    void setRunning(boolean running, boolean stopping) {
        int stroke = stopping ? 0xFFCB6A28 : running ? runningStroke : idleStroke;
        pillBackground.setStroke(dp(2), stroke);
        miniClock.setTextColor(running || stopping ? stroke : timeColor);
        mini.setContentDescription(running || stopping ? "点按立即停止，拖动移动位置" : "点按开始，拖动移动位置");
        mini.setAlpha(stopping ? .72f : 1f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
