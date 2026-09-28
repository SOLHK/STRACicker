package cn.stra.ace5pro.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Lets the user stop the root click helper with a deliberate volume-down hold. */
public final class EmergencyKeyService extends AccessibilityService {
    private boolean stopTriggered;

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) return false;

        if (event.getAction() == KeyEvent.ACTION_UP) {
            stopTriggered = false;
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || stopTriggered) return false;

        if (event.getEventTime() - event.getDownTime() >= 800L) {
            stopTriggered = true;
            new Thread(() -> {
                NativeTouchEngine.hardStopBlocking(this);
                stopService(new Intent(this, OverlayService.class));
                android.app.NotificationManager nm =
                        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(2101);
            }, "stra-volume-emergency-stop").start();
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Key filtering is the only feature this service needs.
    }

    @Override
    public void onInterrupt() {
    }
}
