package cn.stra.ace5pro.autoclicker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;

/** Presentation only. Root operations and task lifecycle live in OverlayService. */
final class OverlayPanel extends LinearLayout {
    final LinearLayout expanded, header, body;
    final TextView title, clock, clockStatus, status, pointCount, mini;
    final MaterialButton collapse, close, add, delete, clear, start, stop, emergency;
    final int surface, ink, muted, accent, onAccent, container;
    private final List<MaterialButton> presets = new ArrayList<>();
    private final EditText interval, cycles;

    OverlayPanel(Context c, EditText interval, EditText cycles) {
        super(c);
        this.interval = interval; this.cycles = cycles;
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        surface = Color.parseColor(dark ? "#202735" : "#FFFFFF");
        container = Color.parseColor(dark ? "#313D50" : "#E5EBF7");
        ink = Color.parseColor(dark ? "#E7EAF0" : "#19202C");
        muted = Color.parseColor(dark ? "#ACB6C8" : "#596579");
        accent = Color.parseColor(dark ? "#ADC6FF" : "#315DA8");
        onAccent = Color.parseColor(dark ? "#122D56" : "#FFFFFF");
        setOrientation(VERTICAL);
        expanded = column(); addView(expanded, new LayoutParams(-1, -2));
        header = new LinearLayout(c); header.setGravity(Gravity.CENTER_VERTICAL);
        title = text("点击控制", 21, ink); title.setTypeface(null, 1);
        header.addView(title, new LayoutParams(0, dp(48), 1));
        collapse = button("收起", false); close = button("关闭", true);
        collapse.setContentDescription("收起为可拖动的小图标");
        close.setContentDescription("关闭悬浮窗");
        collapse.setTextColor(accent); close.setTextColor(ink);
        header.addView(collapse, new LayoutParams(dp(66), dp(48)));
        header.addView(close, new LayoutParams(dp(58), dp(48))); expanded.addView(header);

        LinearLayout clockRow = new LinearLayout(c); clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clock = text("--:--:--", 25, ink); clock.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        clock.setFontFeatureSettings("tnum"); clockRow.addView(clock, new LayoutParams(0, dp(48), 1));
        clockStatus = text("北京时间\n正在校时", 11, muted); clockStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clockRow.addView(clockStatus); expanded.addView(clockRow);

        body = column();
        LinearLayout pointCard = column(); pointCard.setPadding(dp(14), dp(12), dp(14), dp(12)); pointCard.setBackground(shape(container, 24));
        pointCount = text("尚未添加点位", 14, ink); pointCount.setTypeface(null, 1); pointCard.addView(pointCount);
        LinearLayout edits = new LinearLayout(c); edits.setGravity(Gravity.CENTER_VERTICAL);
        add = button("添加点位", false); add.setIcon(new Symbol("plus"));
        add.setBackgroundTintList(ColorStateList.valueOf(accent));
        add.setTextColor(onAccent); add.setIconTint(ColorStateList.valueOf(onAccent));
        delete = button("编辑", true); clear = button("清空", true);
        edits.addView(add, new LayoutParams(0, dp(48), 1.5f));
        edits.addView(delete, new LayoutParams(0, dp(48), 1));
        edits.addView(clear, new LayoutParams(0, dp(48), 1));
        LayoutParams editLp = new LayoutParams(-1, -2); editLp.topMargin = dp(8); pointCard.addView(edits, editLp);
        body.addView(pointCard);

        TextView settingsLabel = text("点击参数", 12, muted); settingsLabel.setPadding(dp(4), dp(16), 0, dp(8)); body.addView(settingsLabel);
        LinearLayout fields = new LinearLayout(c);
        fields.addView(field("周期 · 毫秒", interval), new LayoutParams(0, -2, 1));
        LayoutParams countLp = new LayoutParams(0, -2, 1); countLp.leftMargin = dp(8);
        fields.addView(field("轮数 · 0 为无限", cycles), countLp); body.addView(fields);
        LinearLayout quick = new LinearLayout(c);
        String[] values = {"0.5", "1", "5", "10"};
        for (String value : values) {
            MaterialButton chip = button(value + " ms", false); chip.setTextSize(11);
            chip.setOnClickListener(v -> interval.setText(value)); presets.add(chip);
            LayoutParams lp = new LayoutParams(0, dp(44), 1); if (presets.size() > 1) lp.leftMargin = dp(4); quick.addView(chip, lp);
        }
        LayoutParams quickLp = new LayoutParams(-1, -2); quickLp.topMargin = dp(8); body.addView(quick, quickLp);
        interval.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int count, int after) {}
            public void onTextChanged(CharSequence s, int a, int before, int count) { selectPreset(); }
            public void afterTextChanged(Editable e) {}
        }); selectPreset();
        status = text("准备就绪", 12, muted); status.setPadding(dp(4), dp(10), dp(4), dp(8)); body.addView(status);
        ScrollView scroll = new ScrollView(c) {
            @Override protected void onMeasure(int w, int h) {
                int available = getResources().getDisplayMetrics().heightPixels - dp(280);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.max(dp(90), Math.min(dp(350), available)), MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false); scroll.addView(body); expanded.addView(scroll);

        LinearLayout actions = new LinearLayout(c);
        start = button("开始点击", false); start.setTextSize(16); start.setIcon(new Symbol("play"));
        start.setBackgroundTintList(ColorStateList.valueOf(accent)); start.setTextColor(onAccent); start.setIconTint(ColorStateList.valueOf(onAccent));
        stop = button("停止", false); stop.setIcon(new Symbol("stop"));
        stop.setTextColor(ink); stop.setIconTint(ColorStateList.valueOf(ink));
        actions.addView(start, new LayoutParams(0, dp(56), 1.6f)); LayoutParams stopLp = new LayoutParams(0, dp(56), 1); stopLp.leftMargin=dp(8); actions.addView(stop, stopLp);
        LayoutParams actionsLp=new LayoutParams(-1,-2); actionsLp.topMargin=dp(8); expanded.addView(actions,actionsLp);
        emergency=button("紧急结束",true); emergency.setTextColor(Color.parseColor(dark ? "#FFB4AB" : "#BA1A1A")); expanded.addView(emergency,new LayoutParams(-1,dp(44)));
        mini=text("S",21,onAccent); mini.setTypeface(null,1); mini.setGravity(Gravity.CENTER); mini.setBackground(shape(accent,24)); mini.setContentDescription("轻点停止并展开，拖动移动位置"); mini.setVisibility(GONE); addView(mini,new LayoutParams(dp(48),dp(48)));
        setCollapsed(false);
    }
    void setCollapsed(boolean collapsed) {
        expanded.setVisibility(collapsed ? GONE : VISIBLE); mini.setVisibility(collapsed ? VISIBLE : GONE);
        setPadding(collapsed ? 0 : dp(16), collapsed ? 0 : dp(10), collapsed ? 0 : dp(16), collapsed ? 0 : dp(6));
        GradientDrawable background = shape(surface,32);
        if (!collapsed) background.setStroke(dp(2), accent);
        setBackground(collapsed ? null : background);
        setElevation(collapsed ? dp(8) : dp(14));
    }
    void busy(boolean active, boolean stopping) {
        start.setEnabled(!active); start.setAlpha(active ? .55f : 1f);
        start.setText(stopping ? "停止中…" : active ? "正在运行" : "开始点击");
        stop.setEnabled(active && !stopping); stop.setAlpha(active && !stopping ? 1f : .45f);
        interval.setEnabled(!active); cycles.setEnabled(!active); add.setEnabled(!active); delete.setEnabled(!active); clear.setEnabled(!active);
        for(MaterialButton b:presets) b.setEnabled(!active);
    }
    private void selectPreset() {
        double selected; try { selected=Double.parseDouble(interval.getText().toString()); } catch(Exception e) { selected=-1; }
        double[] values={.5,1,5,10};
        for(int i=0;i<presets.size();i++) {
            MaterialButton b=presets.get(i); boolean match=selected==values[i];
            b.setBackgroundTintList(ColorStateList.valueOf(match ? accent : container)); b.setTextColor(match ? onAccent : muted);
        }
    }
    private TextInputLayout field(String hint, EditText input) {
        TextInputLayout f=new TextInputLayout(getContext()); f.setHint(hint); f.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
        f.setBoxBackgroundColor(container); f.setBoxCornerRadii(dp(16),dp(16),dp(16),dp(16));
        input.setBackground(null); input.setTextColor(ink); input.setTextSize(17); input.setPadding(dp(12),dp(20),dp(8),dp(8));
        f.addView(input,new LayoutParams(-1,dp(62))); return f;
    }
    private MaterialButton iconButton(String description,String symbol) {
        MaterialButton b=button("",true); b.setIcon(new Symbol(symbol)); b.setIconPadding(0); b.setContentDescription(description); return b;
    }
    private MaterialButton button(String value, boolean textOnly) {
        MaterialButton b=new MaterialButton(getContext(),null,com.google.android.material.R.attr.materialButtonStyle);
        b.setText(value); b.setAllCaps(false); b.setTextSize(12); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(8),0,dp(8),0); b.setInsetTop(0); b.setInsetBottom(0); b.setCornerRadius(dp(28)); b.setStrokeWidth(0);
        b.setTextColor(accent); b.setIconTint(ColorStateList.valueOf(accent)); b.setIconSize(dp(18)); b.setIconPadding(dp(5));
        b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        b.setBackgroundTintList(ColorStateList.valueOf(textOnly ? Color.TRANSPARENT : container));
        b.setRippleColor(ColorStateList.valueOf(Color.argb(40,128,150,210))); return b;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(VERTICAL);return l;}
    private TextView text(String s,float sp,int color){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private static final class Symbol extends Drawable {
        final String symbol; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Symbol(String s){symbol=s;p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);}
        @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
            if(symbol.equals("close")){canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);}
            else if(symbol.equals("collapse")){canvas.drawLine(6,10,12,16,p);canvas.drawLine(12,16,18,10,p);}
            else if(symbol.equals("plus")){canvas.drawLine(5,12,19,12,p);canvas.drawLine(12,5,12,19,p);}
            else if(symbol.equals("stop"))canvas.drawRoundRect(6,6,18,18,2,2,p);
            else {android.graphics.Path path=new android.graphics.Path();path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();canvas.drawPath(path,p);} canvas.restore();}
        @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }
}
