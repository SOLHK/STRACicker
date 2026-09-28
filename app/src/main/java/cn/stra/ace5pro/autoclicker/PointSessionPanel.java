package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

/** Temporary point picker controls shown over other apps. */
final class PointSessionPanel extends LinearLayout {
    final TextView dragHandle, count;
    final MaterialButton add, finish;

    PointSessionPanel(Context context) {
        super(context);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int surface = Color.parseColor(dark ? "#202735" : "#FFFFFF");
        int ink = Color.parseColor(dark ? "#E7EAF0" : "#19202C");
        int muted = Color.parseColor(dark ? "#ACB6C8" : "#596579");
        int accent = Color.parseColor(dark ? "#ADC6FF" : "#315DA8");
        int container = Color.parseColor(dark ? "#313D50" : "#E5EBF7");
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(6), 0, dp(7), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), Color.argb(55, accent >>> 16 & 255, accent >>> 8 & 255, accent & 255));
        setBackground(background);
        setElevation(dp(10));

        dragHandle = new TextView(context);
        dragHandle.setText("⠿");
        dragHandle.setTextSize(18);
        dragHandle.setTextColor(muted);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("拖动点位工具条");
        addView(dragHandle, new LayoutParams(dp(28), dp(42)));

        add = button(context, "＋ 添加", accent, Color.WHITE);
        add.setContentDescription("添加一个点位");
        addView(add, new LayoutParams(dp(88), dp(40)));

        count = new TextView(context);
        count.setText("0");
        count.setTextSize(12);
        count.setTextColor(ink);
        count.setGravity(Gravity.CENTER);
        count.setContentDescription("点位数量");
        LayoutParams countLp = new LayoutParams(dp(30), dp(40));
        countLp.leftMargin = dp(2);
        addView(count, countLp);

        finish = button(context, "完成", container, accent);
        finish.setContentDescription("完成点位设置并返回设置页");
        LayoutParams finishLp = new LayoutParams(dp(60), dp(40));
        finishLp.leftMargin = dp(2);
        addView(finish, finishLp);
        setContentDescription("点位选择悬浮工具条");
    }

    void setPointCount(int value) { count.setText(String.valueOf(value)); }

    private MaterialButton button(Context c, String label, int fill, int foreground) {
        MaterialButton button = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setInsetTop(0); button.setInsetBottom(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setCornerRadius(dp(22));
        button.setStrokeWidth(0);
        button.setBackgroundTintList(ColorStateList.valueOf(fill));
        button.setTextColor(foreground);
        return button;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
}
