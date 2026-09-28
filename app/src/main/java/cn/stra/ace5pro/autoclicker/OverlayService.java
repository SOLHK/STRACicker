package cn.stra.ace5pro.autoclicker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class OverlayService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<PointView> points = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private WindowManager wm;
    private SharedPreferences prefs;
    private NativeTouchEngine engine;

    private LinearLayout panel;
    private LinearLayout body;
    private LinearLayout header;
    private TextView miniIcon;
    private TextView clockView;
    private WindowManager.LayoutParams panelLp;
    private View pickOverlay;

    private TextView titleText;
    private TextView beijingClock;
    private TextView statusText;
    private TextView pointText;
    private TextView collapseBtn;
    private TextView deleteBtn;
    private TextView startBtn;
    private TextView stopBtn;
    private TextView forceBtn;

    private EditText intervalInput;
    private EditText cyclesInput;

    private boolean deleteMode = false;
    private boolean runningUi = false;
    private boolean panelInputFocusMode = false;
    private int markerSizePx;

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateBeijingClock();
            main.postDelayed(this, 250L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        timeFmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        prefs = getSharedPreferences("stra_ace5pro_clicker", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        engine = new NativeTouchEngine(this);
        markerSizePx = dp(28);

        BeijingTimeManager.ensureSync(this);

        startForegroundNow();
        createPanel();
        restorePoints();
        refreshPointCount();
        probeEngine();

        main.post(clockTicker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_ALL".equals(intent.getAction())) {
            forceStopEverything(false);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startForegroundNow() {
        final String id = "stra_ace5pro_clicker";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    id,
                    "STRA 连点器",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("STRA 连点器运行控制");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Intent emergency = new Intent(this, EmergencyStopReceiver.class)
                .setAction("STRA_EMERGENCY_STOP");

        PendingIntent emergencyPi = PendingIntent.getBroadcast(
                this,
                99,
                emergency,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, id)
                : new Notification.Builder(this);

        b.setContentTitle("STRA 连点器正在运行")
                .setContentText("通知栏可随时强制停止")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_delete,
                        "强制停止",
                        emergencyPi).build());

        startForeground(2101, b.build());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), Color.argb(58, 255, 255, 255));
        return d;
    }

    private GradientDrawable glassBg() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(247, 17, 23, 34),
                        Color.argb(244, 23, 31, 46),
                        Color.argb(247, 13, 17, 25)
                });
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), Color.argb(72, 255, 255, 255));
        return d;
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView button(String value) {
        TextView v = text(value, 13.5f, Color.WHITE);
        v.setBackground(bg(Color.argb(232, 42, 53, 72), 14));
        v.setPadding(dp(8), 0, dp(8), 0);
        return v;
    }

    private EditText input(String value, String hint, boolean decimal) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setShowSoftInputOnFocus(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setTextSize(13.5f);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(113, 125, 147));
        e.setGravity(Gravity.CENTER);

        int type = android.text.InputType.TYPE_CLASS_NUMBER;
        if (decimal) type |= android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
        e.setInputType(type);

        e.setBackground(bg(Color.argb(225, 23, 29, 42), 12));
        e.setPadding(dp(7), 0, dp(7), 0);
        e.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                enablePanelInputFocus(e);
            }
            return false;
        });
        e.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hidePanelInput();
                return true;
            }
            return false;
        });
        return e;
    }

    private void enablePanelInputFocus(EditText target) {
        if (panelLp == null || panel == null) return;
        panelInputFocusMode = true;
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        panelLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
        target.requestFocus();
        target.postDelayed(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }, 120L);
    }

    private void hidePanelInput() {
        View focused = panel == null ? null : panel.findFocus();
        if (focused != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
        main.postDelayed(this::disablePanelInputFocus, 250L);
    }

    private void disablePanelInputFocus() {
        if (!panelInputFocusMode || panelLp == null || panel == null) return;
        if (isKeyboardVisible()) return;
        panelInputFocusMode = false;
        panelLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
    }

    private boolean isKeyboardVisible() {
        if (panel == null || wm == null) return false;
        Rect frame = new Rect();
        panel.getWindowVisibleDisplayFrame(frame);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        return size.y - frame.bottom > dp(160);
    }

    private void createPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(11), dp(12), dp(12));
        panel.setBackground(glassBg());

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleText = text("STRA  /  点击控制", 15.5f, Color.WHITE);
        titleText.setTypeface(null, 1);
        titleText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleText.setPadding(dp(3), 0, 0, 0);

        collapseBtn = button("—");
        TextView closeBtn = button("×");

        header.addView(titleText, new LinearLayout.LayoutParams(0, dp(42), 1f));

        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(dp(42), dp(42));
        h1.setMargins(dp(5), 0, 0, 0);
        header.addView(collapseBtn, h1);

        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(dp(42), dp(42));
        h2.setMargins(dp(5), 0, 0, 0);
        header.addView(closeBtn, h2);

        panel.addView(header);

        beijingClock = text("北京时间  --:--:--", 15, Color.rgb(139, 207, 255));
        clockView = beijingClock;
        beijingClock.setTypeface(null, 1);
        beijingClock.setGravity(Gravity.CENTER_VERTICAL);
        beijingClock.setPadding(dp(4), 0, dp(4), 0);
        beijingClock.setBackground(bg(Color.argb(115, 34, 92, 160), 12));

        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(-1, dp(42));
        clockLp.setMargins(0, dp(5), 0, 0);
        panel.addView(beijingClock, clockLp);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        statusText = text("状态  ·  正在检测触摸引擎…", 12.5f, Color.rgb(196, 210, 231));
        statusText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        statusText.setPadding(dp(4), 0, dp(4), 0);
        body.addView(statusText, new LinearLayout.LayoutParams(-1, dp(34)));

        pointText = text("0 个点位", 12.5f, Color.rgb(115, 208, 255));
        pointText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pointText.setPadding(dp(4), 0, dp(4), 0);
        body.addView(pointText, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout editRow = new LinearLayout(this);
        editRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView pickBtn = button("＋ 点位");
        deleteBtn = button("删除");
        TextView clearBtn = button("清空");

        editRow.addView(pickBtn, new LinearLayout.LayoutParams(0, dp(44), 1f));

        LinearLayout.LayoutParams er2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        er2.setMargins(dp(5), 0, 0, 0);
        editRow.addView(deleteBtn, er2);

        LinearLayout.LayoutParams er3 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        er3.setMargins(dp(5), 0, 0, 0);
        editRow.addView(clearBtn, er3);

        body.addView(editRow);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);

        intervalInput = input(
                prefs.getString("interval_ms", "0.5"),
                "周期 ms",
                true);

        cyclesInput = input(
                String.valueOf(prefs.getLong("cycles", 0L)),
                "次数 0=∞",
                false);

        settings.addView(intervalInput, new LinearLayout.LayoutParams(0, dp(46), 1f));

        LinearLayout.LayoutParams sr2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        sr2.setMargins(dp(5), 0, 0, 0);
        settings.addView(cyclesInput, sr2);

        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-1, dp(46));
        settingsLp.setMargins(0, dp(6), 0, 0);
        body.addView(settings, settingsLp);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        String[] presetValues = {"0.5 ms 极速", "1 ms", "5 ms", "10 ms"};
        String[] presetIntervals = {"0.5", "1", "5", "10"};
        for (int i = 0; i < presetValues.length; i++) {
            final String value = presetIntervals[i];
            TextView preset = button(presetValues[i]);
            preset.setTextColor(i == 0 ? Color.rgb(139, 222, 255) : Color.WHITE);
            preset.setOnClickListener(v -> intervalInput.setText(value));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) pp.setMargins(dp(6), 0, 0, 0);
            presets.addView(preset, pp);
        }
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(-1, dp(38));
        presetLp.setMargins(0, dp(6), 0, 0);
        body.addView(presets, presetLp);

        LinearLayout runRow = new LinearLayout(this);
        runRow.setOrientation(LinearLayout.HORIZONTAL);

        startBtn = button("▶ 开始");
        stopBtn = button("■ 停止");

        startBtn.setBackground(bg(Color.rgb(24, 105, 225), 13));
        stopBtn.setBackground(bg(Color.rgb(75, 82, 98), 13));

        runRow.addView(startBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));

        LinearLayout.LayoutParams rr2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        rr2.setMargins(dp(5), 0, 0, 0);
        runRow.addView(stopBtn, rr2);

        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(-1, dp(48));
        runLp.setMargins(0, dp(6), 0, 0);
        body.addView(runRow, runLp);

        forceBtn = button("强制结束");
        forceBtn.setTextSize(14.5f);
        forceBtn.setBackground(bg(Color.rgb(186, 42, 55), 14));

        LinearLayout.LayoutParams forceLp = new LinearLayout.LayoutParams(-1, dp(48));
        forceLp.setMargins(0, dp(6), 0, 0);
        body.addView(forceBtn, forceLp);

        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(2), 0, 0);
        panel.addView(body, bodyLp);

        miniIcon = text("S", 20, Color.WHITE);
        miniIcon.setTypeface(null, 1);
        miniIcon.setBackground(bg(Color.rgb(24, 105, 225), 99));
        miniIcon.setVisibility(View.GONE);
        panel.addView(miniIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelLp = new WindowManager.LayoutParams(
                dp(320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = prefs.getInt("panel_x", dp(10));
        panelLp.y = prefs.getInt("panel_y", dp(64));
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;

        wm.addView(panel, panelLp);

        panel.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (panelInputFocusMode && !isKeyboardVisible()) {
                main.postDelayed(this::disablePanelInputFocus, 300L);
            }
        });
        panel.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (!hasFocus && panelInputFocusMode) main.post(this::disablePanelInputFocus);
        });

        titleText.setOnTouchListener(new PanelDrag());

        collapseBtn.setOnClickListener(v -> minimizePanel());

        miniIcon.setOnClickListener(v -> {
            if (runningUi || engine.isRunning()) {
                stopClicking();
                main.postDelayed(this::expandPanel, 300L);
            } else {
                expandPanel();
            }
        });

        closeBtn.setOnClickListener(v -> {
            forceStopEverything(false);
            stopSelf();
        });

        pickBtn.setOnClickListener(v -> {
            if (runningUi || engine.isRunning()) {
                Toast.makeText(this, "请先停止", Toast.LENGTH_SHORT).show();
                return;
            }
            openPickOverlay();
        });

        deleteBtn.setOnClickListener(v -> {
            if (runningUi || engine.isRunning()) return;

            deleteMode = !deleteMode;
            deleteBtn.setText(deleteMode ? "完成" : "删除");
            deleteBtn.setBackground(deleteMode
                    ? bg(Color.rgb(143, 55, 66), 13)
                    : bg(Color.argb(225, 40, 47, 62), 13));

            refreshMarkers();
            refreshPointCount();
        });

        clearBtn.setOnClickListener(v -> {
            if (runningUi || engine.isRunning()) return;
            clearPoints();
        });

        startBtn.setOnClickListener(v -> startClicking());
        stopBtn.setOnClickListener(v -> stopClicking());
        forceBtn.setOnClickListener(v -> forceStopEverything(true));
    }

    private void minimizePanel() {
        if (panelInputFocusMode) hidePanelInput();
        header.setVisibility(View.GONE);
        clockView.setVisibility(View.GONE);
        body.setVisibility(View.GONE);
        miniIcon.setVisibility(View.VISIBLE);
        panel.setPadding(0, 0, 0, 0);
        panelLp.width = dp(48);
        panelLp.height = dp(48);
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
    }

    private void expandPanel() {
        miniIcon.setVisibility(View.GONE);
        panel.setPadding(dp(12), dp(11), dp(12), dp(12));
        header.setVisibility(View.VISIBLE);
        clockView.setVisibility(View.VISIBLE);
        body.setVisibility(View.VISIBLE);
        panelLp.width = dp(320);
        panelLp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
    }

    private void updateBeijingClock() {
        BeijingTimeManager.ensureSync(this);

        long now = BeijingTimeManager.nowMs(this);
        String time = timeFmt.format(new Date(now));

        if (BeijingTimeManager.isSynced(this)) {
            beijingClock.setText("北京时间  " + time + "   ✓");
            beijingClock.setTextColor(Color.rgb(131, 205, 255));
        } else if (BeijingTimeManager.isSyncing()) {
            beijingClock.setText("北京时间  " + time + "   同步中");
            beijingClock.setTextColor(Color.rgb(255, 205, 114));
        } else {
            beijingClock.setText("北京时间  " + time + "   未校时");
            beijingClock.setTextColor(Color.rgb(255, 174, 119));
        }
    }

    private void probeEngine() {
        new Thread(() -> {
            boolean ok = TouchDeviceDetector.hasRoot() && engine.probeSupport();
            main.post(() -> statusText.setText(ok ? "引擎：uinput 可用 ✓" : "引擎：uinput 不可用"));
        }, "stra-probe").start();
    }

    private void openPickOverlay() {
        if (pickOverlay != null) return;

        FrameLayout capture = new FrameLayout(this);
        capture.setBackgroundColor(Color.argb(20, 0, 0, 0));

        TextView hint = text("点屏幕添加位置 · 点这里取消", 12.8f, Color.WHITE);
        hint.setBackground(bg(Color.argb(242, 18, 23, 33), 16));
        hint.setPadding(dp(14), dp(10), dp(14), dp(10));

        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintLp.topMargin = dp(64);
        capture.addView(hint, hintLp);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        lp.gravity = Gravity.TOP | Gravity.START;

        hint.setOnClickListener(v -> removePickOverlay());

        capture.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int x = Math.round(e.getRawX()) - markerSizePx / 2;
                int y = Math.round(e.getRawY()) - markerSizePx / 2;

                removePickOverlay();
                addPoint(x, y, true);
                return true;
            }
            return true;
        });

        pickOverlay = capture;
        wm.addView(capture, lp);
    }

    private void removePickOverlay() {
        if (pickOverlay == null) return;
        try { wm.removeView(pickOverlay); } catch (Throwable ignored) {}
        pickOverlay = null;
    }

    private void addPoint(int x, int y, boolean persist) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        TextView marker = text(String.valueOf(points.size() + 1), 10.8f, Color.WHITE);
        marker.setTypeface(null, 1);
        marker.setBackground(bg(Color.argb(228, 17, 120, 239), 99));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                markerSizePx,
                markerSizePx,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(0, x);
        lp.y = Math.max(0, y);

        PointView pv = new PointView(marker, lp);
        points.add(pv);

        marker.setOnTouchListener(new MarkerTouch(pv));
        wm.addView(marker, lp);

        refreshMarkers();
        refreshPointCount();

        if (persist) savePoints();
    }

    private void removePoint(PointView p) {
        if (p == null || runningUi || engine.isRunning()) return;

        try { wm.removeView(p.view); } catch (Throwable ignored) {}
        points.remove(p);

        refreshMarkers();
        refreshPointCount();
        savePoints();
    }

    private void clearPoints() {
        for (PointView p : new ArrayList<>(points)) {
            try { wm.removeView(p.view); } catch (Throwable ignored) {}
        }

        points.clear();
        refreshMarkers();
        refreshPointCount();
        savePoints();
    }

    private void refreshMarkers() {
        for (int i = 0; i < points.size(); i++) {
            PointView p = points.get(i);

            p.view.setText(deleteMode ? "×" : String.valueOf(i + 1));
            p.view.setAlpha(runningUi ? 0.30f : 0.96f);
            p.view.setBackground(deleteMode
                    ? bg(Color.argb(235, 192, 54, 66), 99)
                    : bg(Color.argb(228, 17, 120, 239), 99));
        }
    }

    private void refreshPointCount() {
        if (pointText == null) return;

        if (deleteMode) {
            pointText.setText(points.size() + " 个点位 · 点红点删除");
        } else if (runningUi) {
            pointText.setText(points.size() + " 个点位 · 运行中");
        } else {
            pointText.setText(points.size() + " 个点位 · 可拖动调整");
        }
    }

    private List<TapPoint> collectPoints() {
        List<TapPoint> out = new ArrayList<>();

        for (PointView p : points) {
            out.add(new TapPoint(
                    p.lp.x + markerSizePx / 2,
                    p.lp.y + markerSizePx / 2));
        }

        return out;
    }

    private double parseDouble(EditText e, double def) {
        try {
            String s = e.getText().toString().trim();
            return s.isEmpty() ? def : Double.parseDouble(s);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private long parseLong(EditText e, long def) {
        try {
            String s = e.getText().toString().trim();
            return s.isEmpty() ? def : Long.parseLong(s);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private void startClicking() {
        if (runningUi || engine.isRunning()) return;

        if (points.isEmpty()) {
            Toast.makeText(this, "请先添加点位", Toast.LENGTH_SHORT).show();
            return;
        }

        final double intervalMs = Math.max(0.0, parseDouble(intervalInput, 0.5));
        final long cycles = Math.max(0L, parseLong(cyclesInput, 0L));

        prefs.edit()
                .putString("interval_ms", String.valueOf(intervalMs))
                .putLong("cycles", cycles)
                .apply();

        deleteMode = false;
        deleteBtn.setText("删除");
        deleteBtn.setBackground(bg(Color.argb(225, 40, 47, 62), 13));

        setRunningUi(true);
        statusText.setText("引擎：启动中…");

        final List<TapPoint> target = collectPoints();

        new Thread(() -> {
            if (!engine.probeSupport()) {
                main.post(() -> {
                    setRunningUi(false);
                    statusText.setText("引擎：uinput 不可用");
                    Toast.makeText(this, "uinput 不可用，未启动", Toast.LENGTH_LONG).show();
                });
                return;
            }

            boolean ok = engine.start(
                    target,
                    intervalMs,
                    cycles,
                    message -> main.post(() -> onFinished(message)));

            main.post(() -> {
                if (ok) {
                    statusText.setText("状态  ·  运行中 / 目标周期 "
                            + String.format(Locale.US, "%.3f ms",
                                    intervalMs <= 0.0 ? 0.5 : Math.max(0.5, intervalMs)));
                } else {
                    setRunningUi(false);
                    statusText.setText("引擎：启动失败");
                }
            });
        }, "stra-start").start();
    }

    private void stopClicking() {
        statusText.setText("状态  ·  正在停止…");

        try { engine.stop(); } catch (Throwable ignored) {}

        main.postDelayed(() -> {
            NativeTouchEngine.hardStop(this);
            setRunningUi(false);
            statusText.setText("状态  ·  已停止");
        }, 250L);
    }

    private void forceStopEverything(boolean toast) {
        try { engine.stop(); } catch (Throwable ignored) {}
        NativeTouchEngine.hardStop(this);

        setRunningUi(false);

        if (statusText != null) statusText.setText("引擎：已强制结束");
        if (toast) Toast.makeText(this, "已强制结束连点", Toast.LENGTH_SHORT).show();
    }

    private void onFinished(String message) {
        setRunningUi(false);
        if (statusText != null) statusText.setText("引擎：" + message);
    }

    private void setRunningUi(boolean running) {
        runningUi = running;

        titleText.setText(running ? "STRA · 运行中" : "STRA · 编辑");
        startBtn.setText(running ? "运行中" : "▶ 开始");
        startBtn.setAlpha(running ? 0.55f : 1f);

        stopBtn.setBackground(bg(
                running ? Color.rgb(187, 58, 70) : Color.rgb(75, 82, 98),
                13));

        for (PointView p : points) {
            if (running) {
                p.lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                p.lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }

            try { wm.updateViewLayout(p.view, p.lp); } catch (Throwable ignored) {}
        }

        refreshMarkers();
        refreshPointCount();
    }

    private void savePoints() {
        StringBuilder b = new StringBuilder();

        for (PointView p : points) {
            if (b.length() > 0) b.append(';');
            b.append(p.lp.x).append(',').append(p.lp.y);
        }

        prefs.edit().putString("points", b.toString()).apply();
    }

    private void restorePoints() {
        String saved = prefs.getString("points", "");
        if (saved == null || saved.isEmpty()) return;

        for (String pair : saved.split(";")) {
            String[] xy = pair.split(",");
            if (xy.length != 2) continue;

            try {
                addPoint(
                        Integer.parseInt(xy[0]),
                        Integer.parseInt(xy[1]),
                        false);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(clockTicker);
        forceStopEverything(false);
        removePickOverlay();

        try { if (panel != null) wm.removeView(panel); } catch (Throwable ignored) {}

        for (PointView p : new ArrayList<>(points)) {
            try { wm.removeView(p.view); } catch (Throwable ignored) {}
        }

        points.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class PanelDrag implements View.OnTouchListener {
        int startX;
        int startY;
        float downX;
        float downY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                startX = panelLp.x;
                startY = panelLp.y;
                downX = e.getRawX();
                downY = e.getRawY();
                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                panelLp.x = startX + Math.round(e.getRawX() - downX);
                panelLp.y = startY + Math.round(e.getRawY() - downY);

                try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_UP) {
                prefs.edit()
                        .putInt("panel_x", panelLp.x)
                        .putInt("panel_y", panelLp.y)
                        .apply();
                return true;
            }

            return false;
        }
    }

    private final class MarkerTouch implements View.OnTouchListener {
        final PointView point;

        int startX;
        int startY;
        float downX;
        float downY;
        long downAt;
        boolean moved;

        MarkerTouch(PointView point) {
            this.point = point;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (runningUi || engine.isRunning()) return true;

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                startX = point.lp.x;
                startY = point.lp.y;
                downX = e.getRawX();
                downY = e.getRawY();
                downAt = System.currentTimeMillis();
                moved = false;
                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;

                if (Math.abs(dx) > dp(2) || Math.abs(dy) > dp(2)) {
                    moved = true;
                }

                if (!deleteMode) {
                    point.lp.x = startX + Math.round(dx);
                    point.lp.y = startY + Math.round(dy);

                    try { wm.updateViewLayout(point.view, point.lp); } catch (Throwable ignored) {}
                }

                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_UP) {
                long held = System.currentTimeMillis() - downAt;

                if (deleteMode || (!moved && held >= 500)) {
                    removePoint(point);
                } else {
                    savePoints();
                }

                return true;
            }

            return false;
        }
    }

    private static final class PointView {
        final TextView view;
        final WindowManager.LayoutParams lp;

        PointView(TextView view, WindowManager.LayoutParams lp) {
            this.view = view;
            this.lp = lp;
        }
    }
}
