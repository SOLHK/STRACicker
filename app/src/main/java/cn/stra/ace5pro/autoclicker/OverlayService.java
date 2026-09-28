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
    private View miniIcon;
    private WindowManager.LayoutParams panelLp;
    private View pickOverlay;
    private PointSessionPanel sessionPanel;
    private WindowManager.LayoutParams sessionLp;

    private boolean pointSessionActive;
    private boolean runningUi = false;
    private boolean showPointMarkers;
    private boolean pendingReload;
    private static volatile boolean serviceActive;
    private static volatile boolean taskActive;
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

        serviceActive = true;
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
            if (ACTION_BEGIN_POINT_SESSION.equals(action) || ACTION_PICK_POINT.equals(action)) beginPointSession();
            else if (ACTION_RELOAD_POINTS.equals(action)) reloadPoints();
            else if (ACTION_HIDE_POINTS.equals(action) && !pointSessionActive) setPointMarkersVisible(false);
        }
        return START_NOT_STICKY;
    }

    static final String ACTION_POINT_ADDED = "cn.stra.ace5pro.autoclicker.POINT_ADDED";
    static final String ACTION_PICK_POINT = "cn.stra.ace5pro.autoclicker.PICK_POINT";
    static final String ACTION_BEGIN_POINT_SESSION = "cn.stra.ace5pro.autoclicker.BEGIN_POINT_SESSION";
    static final String ACTION_RELOAD_POINTS = "cn.stra.ace5pro.autoclicker.RELOAD_POINTS";
    static final String ACTION_HIDE_POINTS = "cn.stra.ace5pro.autoclicker.HIDE_POINTS";
    static boolean isServiceActive() { return serviceActive; }
    static boolean isTaskRunning() { return taskActive; }

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
        miniIcon = panelUi.mini;
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        panelLp = new WindowManager.LayoutParams(
                dp(OverlayPanel.MINI_WIDTH_DP), dp(OverlayPanel.MINI_HEIGHT_DP), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = prefs.getInt("panel_x", dp(10));
        panelLp.y = prefs.getInt("panel_y", dp(64));
        wm.addView(panel, panelLp);
        panel.post(this::clampPanel);
        miniIcon.setOnTouchListener(new CompactIconDrag());
        panelUi.setRunning(false, false);
    }

    private void toggleClicking() {
        DiagnosticLog.record(this, "Floating pill tapped; state=" + gate.state());
        if (gate.state() != RunGate.State.IDLE || engine.isRunning()) requestStop(null);
        else startClicking();
    }

    private void clampPanel() {
        Point size = new Point(); wm.getDefaultDisplay().getRealSize(size);
        int height = panelLp.height > 0 ? panelLp.height : panel.getMeasuredHeight();
        panelLp.x = Math.max(0, Math.min(panelLp.x, size.x - panelLp.width));
        panelLp.y = Math.max(dp(24), Math.min(panelLp.y, size.y - height - dp(24)));
        if (!destroyed) try { wm.updateViewLayout(panel, panelLp); } catch (IllegalArgumentException ignored) {}
    }

    private void updateBeijingClock() {
        BeijingTimeManager.ensureSync(this);

        long now = BeijingTimeManager.nowMs(this);
        String time = timeFmt.format(new Date(now));

        panelUi.miniClock.setText(time);
    }

    private void probeEngine() {
        NativeTouchEngine.CONTROL.execute(() -> {
            boolean ok = engine.probeSupport();
            main.post(() -> {
                DiagnosticLog.record(this, "Root engine probe available=" + ok);
                if (!destroyed) DiagnosticLog.record(this, ok ? "Click engine ready" : "Root click engine unavailable");
            });
        });
    }

    private void beginPointSession() {
        if (pointSessionActive) return;
        if (runningUi || engine.isRunning()) {
            Toast.makeText(this, "请先停止任务再编辑点位", Toast.LENGTH_SHORT).show();
            return;
        }
        pointSessionActive = true;
        setPointMarkersVisible(true);
        panel.setVisibility(View.GONE);
        sessionPanel = new PointSessionPanel(uiContext);
        sessionPanel.setPointCount(points.size());
        sessionPanel.add.setOnClickListener(v -> openPickOverlay());
        sessionPanel.finish.setOnClickListener(v -> endPointSession(true));
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        sessionLp = new WindowManager.LayoutParams(
                dp(224), dp(52), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        sessionLp.gravity = Gravity.TOP | Gravity.START;
        sessionLp.x = prefs.getInt("point_session_x", dp(12));
        sessionLp.y = prefs.getInt("point_session_y", dp(76));
        wm.addView(sessionPanel, sessionLp);
        sessionPanel.dragHandle.setOnTouchListener(new SessionPanelDrag());
        DiagnosticLog.record(this, "Point selection session started");
    }

    private void endPointSession(boolean returnToSettings) {
        removePickOverlay();
        pointSessionActive = false;
        setPointMarkersVisible(false);
        try { if (sessionPanel != null) wm.removeView(sessionPanel); } catch (Throwable ignored) {}
        sessionPanel = null;
        sessionLp = null;
        if (panel != null) panel.setVisibility(View.VISIBLE);
        DiagnosticLog.record(this, "Point selection session finished");
        if (returnToSettings && !destroyed) {
            Intent settings = new Intent(this, ClickSettingsActivity.class);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { startActivity(settings); } catch (Throwable ignored) {}
        }
    }

    private void openPickOverlay() {
        if (!pointSessionActive || pickOverlay != null) return;

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
                if (sessionPanel != null) sessionPanel.setPointCount(points.size());
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
        marker.setVisibility(showPointMarkers ? View.VISIBLE : View.GONE);
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
        if (sessionPanel != null) sessionPanel.setPointCount(points.size());
        sendBroadcast(new Intent(ACTION_POINT_ADDED).setPackage(getPackageName()));
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
            intervalMs = Math.max(0.5d, Double.parseDouble(prefs.getString("interval_ms", "0.5")));
            cycles = Math.max(0L, prefs.getLong("cycles", 0L));
            if (!Double.isFinite(intervalMs) || intervalMs > 2000) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            Toast.makeText(this, "请先在应用内点击设置中检查参数", Toast.LENGTH_SHORT).show(); return;
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
        setRunningUi(true);
        NativeTouchEngine.CONTROL.execute(() -> {
            if (!gate.current(token)) return;
            boolean ok=engine.start(target,intervalMs,cycles,message -> main.post(() -> {
                if (gate.finish(token) && !destroyed) {
                    DiagnosticLog.record(this, "Task finished: " + message);
                    setRunningUi(false);
                    if (!"已停止".equals(message)) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            }));
            if (!gate.current(token)) { engine.stopBlocking(); return; }
            main.post(() -> {
                if (destroyed) return;
                if (ok && gate.started(token)) {
                    DiagnosticLog.record(this, "Task running");
                    DiagnosticLog.record(this, "Task running at " + intervalMs + " ms per tap");
                } else if (!ok && gate.finish(token)) {
                    DiagnosticLog.record(this, "Task failed to start");
                    setRunningUi(false); Toast.makeText(this, "启动失败，请检查 Root 权限", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void requestStop(Runnable afterStop) {
        final long token=gate.stop();
        if (token < 0) return;
        DiagnosticLog.record(this, "Stop requested; state=" + gate.state());
        NativeTouchEngine.signalStopFile(this);
        setRunningUi(true);
        NativeTouchEngine.CONTROL.execute(() -> {
            engine.stopBlocking();
            main.post(() -> {
                if (!destroyed && gate.stopped(token)) {
                    DiagnosticLog.record(this, "Task stopped");
                    setRunningUi(false);
                    if (pendingReload) { pendingReload = false; reloadPoints(); }
                    if (afterStop != null) afterStop.run();
                }
            });
        });
    }

    private void setRunningUi(boolean active) {
        runningUi=active;
        taskActive=active;
        panelUi.setRunning(active, gate.state() == RunGate.State.STOPPING);
        for (PointView p:points) {
            if (active) p.lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else p.lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(p.view,p.lp); } catch (IllegalArgumentException ignored) {}
        }
        refreshMarkers();
    }



    private void setPointMarkersVisible(boolean visible) {
        showPointMarkers = visible;
        for (PointView point : points) point.view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void reloadPoints() {
        if (runningUi || engine.isRunning()) { pendingReload = true; return; }
        for (PointView p : new ArrayList<>(points)) try { wm.removeView(p.view); } catch (Throwable ignored) {}
        points.clear();
        restorePoints();
        if (sessionPanel != null) sessionPanel.setPointCount(points.size());
    }

    /** Keep the pill outside configured touch targets before starting rapid input. */
    private boolean movePanelAwayFromTargets() {
        if (points.isEmpty()) return true;
        Point display = new Point(); wm.getDefaultDisplay().getRealSize(display);
        int width = dp(OverlayPanel.MINI_WIDTH_DP);
        int height = dp(OverlayPanel.MINI_HEIGHT_DP);
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
                panelLp.x=x; panelLp.y=y;
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
        serviceActive = false;
        taskActive = false;
        gate.close();
        NativeTouchEngine.signalStopFile(this);
        main.removeCallbacksAndMessages(null);
        NativeTouchEngine.CONTROL.execute(() -> engine.stopBlocking());
        removePickOverlay();

        try { if (sessionPanel != null) wm.removeView(sessionPanel); } catch (Throwable ignored) {}
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

    private final class SessionPanelDrag implements View.OnTouchListener {
        int originX, originY;
        float downX, downY;
        boolean moved;
        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    originX = sessionLp.x; originY = sessionLp.y;
                    downX = event.getRawX(); downY = event.getRawY(); moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    if (moved) {
                        Point size = new Point(); wm.getDefaultDisplay().getRealSize(size);
                        sessionLp.x = Math.max(0, Math.min(size.x-sessionLp.width, originX+Math.round(dx)));
                        sessionLp.y = Math.max(dp(24), Math.min(size.y-sessionLp.height-dp(24), originY+Math.round(dy)));
                        try { wm.updateViewLayout(sessionPanel, sessionLp); } catch (Throwable ignored) {}
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) prefs.edit().putInt("point_session_x", sessionLp.x).putInt("point_session_y", sessionLp.y).apply();
                    return true;
                default: return true;
            }
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
                        toggleClicking();
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
