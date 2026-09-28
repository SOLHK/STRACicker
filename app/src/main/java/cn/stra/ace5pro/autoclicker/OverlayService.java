package cn.stra.ace5pro.autoclicker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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

    private final RunGate gate = new RunGate();
    private OverlayPanel panelUi;
    private android.content.Context uiContext;
    private boolean destroyed;
    private LinearLayout panel;
    private LinearLayout header;
    private View miniIcon;
    private TextView clockView;
    private WindowManager.LayoutParams panelLp;
    private View pickOverlay;

    private TextView titleText;
    private TextView beijingClock;
    private TextView statusText;
    private TextView collapseBtn;
    private TextView startBtn;
    private TextView stopBtn;


    private boolean runningUi = false;
    private int markerSizePx;

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateBeijingClock();
            main.postDelayed(this, 1000L);
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
        DiagnosticLog.record(this, "Overlay opened");
        restorePoints();
        probeEngine();

        main.post(clockTicker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PICK_POINT.equals(action)) openPickOverlay();
            else if (ACTION_RELOAD_POINTS.equals(action)) reloadPoints();
        }
        return START_NOT_STICKY;
    }

    static final String ACTION_POINT_ADDED = "cn.stra.ace5pro.autoclicker.POINT_ADDED";
    static final String ACTION_PICK_POINT = "cn.stra.ace5pro.autoclicker.PICK_POINT";
    static final String ACTION_RELOAD_POINTS = "cn.stra.ace5pro.autoclicker.RELOAD_POINTS";

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

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, id)
                : new Notification.Builder(this);

        b.setContentTitle("STRA 连点器正在运行")
                .setContentText("悬浮控制器已就绪")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true);

        startForeground(2101, b.build());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), Color.argb(26, 255, 255, 255));
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

    private void createPanel() {
        uiContext = new android.view.ContextThemeWrapper(this, R.style.Theme_STRA_Overlay);
        panelUi = new OverlayPanel(uiContext);
        panel = panelUi;
        header = panelUi.header;
        titleText = panelUi.title; beijingClock = panelUi.clock; clockView = beijingClock;
        statusText = panelUi.status;
        miniIcon = panelUi.mini; collapseBtn = panelUi.collapse;
        startBtn = panelUi.start; stopBtn = panelUi.stop;
        TextView closeBtn = panelUi.close;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelLp = new WindowManager.LayoutParams(
                Math.min(dp(350), getResources().getDisplayMetrics().widthPixels - dp(24)),
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
        panel.post(this::clampPanel);

        titleText.setOnTouchListener(new PanelDrag());

        collapseBtn.setOnClickListener(v -> minimizePanel());

        miniIcon.setOnTouchListener(new CompactIconDrag());

        closeBtn.setOnClickListener(v -> {
            requestStop(null);
            stopSelf();
        });

        startBtn.setOnClickListener(v -> startClicking());
        stopBtn.setOnClickListener(v -> stopClicking());
        panelUi.busy(false, false);
    }

    private void minimizePanel() {
        panelLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        panelUi.setCollapsed(true);
        panelLp.width = dp(OverlayPanel.MINI_WIDTH_DP); panelLp.height = dp(OverlayPanel.MINI_HEIGHT_DP);
        clampPanel();
        DiagnosticLog.record(this, "Overlay collapsed to draggable icon");
    }

    private void expandPanel() {
        if (destroyed) return;
        panelUi.setCollapsed(false);
        panelLp.width = Math.min(dp(352), getResources().getDisplayMetrics().widthPixels - dp(24));
        panelLp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        panel.measure(View.MeasureSpec.makeMeasureSpec(panelLp.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panelLp.height = panel.getMeasuredHeight();
        clampPanel();
        DiagnosticLog.record(this, "Overlay expanded");
    }

    private void clampPanel() {
        Point size = new Point(); wm.getDefaultDisplay().getRealSize(size);
        int height = panelLp.height > 0 ? panelLp.height : panel.getMeasuredHeight();
        panelLp.x = Math.max(0, Math.min(panelLp.x, size.x - panelLp.width));
        panelLp.y = Math.max(dp(24), Math.min(panelLp.y, size.y - height - dp(24)));
        if (!destroyed) try { wm.updateViewLayout(panel, panelLp); } catch (IllegalArgumentException ignored) {}
    }

    private void onCompactIconTap() {
        DiagnosticLog.record(this, "Compact icon tapped; state=" + gate.state());
        if (gate.state() != RunGate.State.IDLE || engine.isRunning()) requestStop(this::expandPanel);
        else expandPanel();
    }

    private void updateBeijingClock() {
        BeijingTimeManager.ensureSync(this);

        long now = BeijingTimeManager.nowMs(this);
        String time = timeFmt.format(new Date(now));

        beijingClock.setText(time);
        panelUi.miniClock.setText(time);
        panelUi.clockStatus.setText("北京时间\n" + (BeijingTimeManager.isSynced(this) ? "已校时" : BeijingTimeManager.isSyncing() ? "校时中" : "系统时间"));
    }

    private void probeEngine() {
        NativeTouchEngine.CONTROL.execute(() -> {
            boolean ok = engine.probeSupport();
            main.post(() -> {
                DiagnosticLog.record(this, "Root engine probe available=" + ok);
                if (!destroyed && gate.state() == RunGate.State.IDLE)
                    statusText.setText(ok ? "准备就绪 · 在应用内点击设置管理点位" : "请检查 Root 授权");
            });
        });
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
                sendBroadcast(new Intent(ACTION_POINT_ADDED).setPackage(getPackageName()));
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

        if (persist) savePoints();
    }

    private void removePoint(PointView p) {
        if (p == null || runningUi || engine.isRunning()) return;

        try { wm.removeView(p.view); } catch (Throwable ignored) {}
        points.remove(p);

        refreshMarkers();
        savePoints();
    }

    private void refreshMarkers() {
        for (int i = 0; i < points.size(); i++) {
            PointView p = points.get(i);

            p.view.setText(String.valueOf(i + 1));
            p.view.setAlpha(runningUi ? 0.30f : 0.96f);
            p.view.setBackground(bg(Color.argb(228, 17, 120, 239), 99));
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

    private void startClicking() {
        if (gate.state() != RunGate.State.IDLE) return;
        if (points.isEmpty()) { Toast.makeText(this,"请先到应用内的点击设置添加点位",Toast.LENGTH_SHORT).show(); return; }
        final double intervalMs;
        final long cycles;
        try {
            intervalMs = Math.max(10d, Double.parseDouble(prefs.getString("interval_ms", "10")));
            cycles = Math.max(0L, prefs.getLong("cycles", 0L));
            if (!Double.isFinite(intervalMs) || intervalMs > 2000) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            statusText.setText("请先在应用内点击设置中检查参数"); return;
        }
        if (!movePanelAwayFromTargets()) {
            Toast.makeText(this, "悬浮窗与点位重叠，请移动点位后再开始", Toast.LENGTH_LONG).show();
            return;
        }
        final long token = gate.begin();
        if (token < 0) return;
        DiagnosticLog.record(this, "Start requested: points=" + points.size()
                + " intervalMs=" + intervalMs + " cycles=" + cycles);

        final List<TapPoint> target=collectPoints();
        setRunningUi(true); statusText.setText("正在启动…");
        NativeTouchEngine.CONTROL.execute(() -> {
            if (!gate.current(token)) return;
            boolean ok=engine.start(target,intervalMs,cycles,message -> main.post(() -> {
                if (gate.finish(token) && !destroyed) {
                    DiagnosticLog.record(this, "Task finished: " + message);
                    setRunningUi(false); statusText.setText(message);
                }
            }));
            if (!gate.current(token)) { engine.stopBlocking(); return; }
            main.post(() -> {
                if (destroyed) return;
                if (ok && gate.started(token)) {
                    DiagnosticLog.record(this, "Task running");
                    statusText.setText("运行中 · " + intervalMs + " ms / 次");
                } else if (!ok && gate.finish(token)) {
                    DiagnosticLog.record(this, "Task failed to start");
                    setRunningUi(false); statusText.setText("启动失败，请检查 Root 权限");
                }
            });
        });
    }

    private void stopClicking() { requestStop(null); }

    private void requestStop(Runnable afterStop) {
        final long token=gate.stop();
        if (token < 0) return;
        DiagnosticLog.record(this, "Stop requested; state=" + gate.state());
        NativeTouchEngine.signalStopFile(this);
        setRunningUi(true); statusText.setText("正在停止…");
        NativeTouchEngine.CONTROL.execute(() -> {
            engine.stopBlocking();
            main.post(() -> {
                if (!destroyed && gate.stopped(token)) {
                    DiagnosticLog.record(this, "Task stopped");
                    setRunningUi(false); statusText.setText("已停止");
                    if (afterStop != null) afterStop.run();
                }
            });
        });
    }

    private void setRunningUi(boolean active) {
        runningUi=active;
        panelUi.busy(active,gate.state()==RunGate.State.STOPPING);
        for (PointView p:points) {
            if (active) p.lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else p.lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(p.view,p.lp); } catch (IllegalArgumentException ignored) {}
        }
        refreshMarkers();
    }


    private void reloadPoints() {
        if (runningUi || engine.isRunning()) return;
        for (PointView p : new ArrayList<>(points)) try { wm.removeView(p.view); } catch (Throwable ignored) {}
        points.clear();
        restorePoints();
    }

    /** Keep every configured touch target outside the expanded controller's hit area. */
    private boolean movePanelAwayFromTargets() {
        if (points.isEmpty()) return true;
        expandPanel();
        Point display = new Point(); wm.getDefaultDisplay().getRealSize(display);
        int width = panelLp.width;
        int height = Math.max(panel.getMeasuredHeight(), dp(160));
        int edge = dp(24);
        int[] xs = {edge, Math.max(edge, (display.x-width)/2), Math.max(edge, display.x-width-edge)};
        int[] ys = {dp(32), Math.max(dp(32), (display.y-height)/2), Math.max(dp(32), display.y-height-dp(32))};
        int margin = dp(24);
        for (int y : ys) for (int x : xs) {
            Rect candidate = new Rect(x-margin, y-margin, x+width+margin, y+height+margin);
            boolean collision = false;
            for (PointView point : points) {
                int px = point.lp.x + markerSizePx/2, py = point.lp.y + markerSizePx/2;
                if (candidate.contains(px, py)) { collision = true; break; }
            }
            if (!collision) {
                panelLp.x=x; panelLp.y=y; panelLp.height=WindowManager.LayoutParams.WRAP_CONTENT;
                clampPanel(); prefs.edit().putInt("panel_x",x).putInt("panel_y",y).apply(); return true;
            }
        }
        return false;
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
        DiagnosticLog.record(this, "Overlay closed; state=" + gate.state());
        destroyed = true;
        gate.close();
        NativeTouchEngine.signalStopFile(this);
        main.removeCallbacksAndMessages(null);
        NativeTouchEngine.CONTROL.execute(() -> engine.stopBlocking());
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

                clampPanel();
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

    private final class CompactIconDrag implements View.OnTouchListener {
        int startX, startY;
        float downX, downY;
        boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = panelLp.x;
                    startY = panelLp.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    moved = false;
                    view.setPressed(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!moved && (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5))) moved = true;
                    if (moved) {
                        Point display = new Point();
                        wm.getDefaultDisplay().getRealSize(display);
                        int maxX = Math.max(0, display.x - panelLp.width);
                        int maxY = Math.max(0, display.y - panelLp.height);
                        panelLp.x = Math.max(0, Math.min(maxX, startX + Math.round(dx)));
                        panelLp.y = Math.max(0, Math.min(maxY, startY + Math.round(dy)));
                        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    if (moved) {
                        prefs.edit().putInt("panel_x", panelLp.x).putInt("panel_y", panelLp.y).apply();
                    } else {
                        onCompactIconTap();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    return true;
                default:
                    return true;
            }
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

                point.lp.x = startX + Math.round(dx);
                point.lp.y = startY + Math.round(dy);
                try { wm.updateViewLayout(point.view, point.lp); } catch (Throwable ignored) {}

                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_UP) {
                long held = System.currentTimeMillis() - downAt;

                if (!moved && held >= 700) {
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
