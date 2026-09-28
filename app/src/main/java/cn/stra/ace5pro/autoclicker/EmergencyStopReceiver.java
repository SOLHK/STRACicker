package cn.stra.ace5pro.autoclicker;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class EmergencyStopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();

        new Thread(() -> {
            try {
                NativeTouchEngine.hardStop(context);
                context.stopService(new Intent(context, OverlayService.class));

                NotificationManager nm =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(2101);
            } finally {
                pending.finish();
            }
        }, "stra-emergency-stop").start();
    }
}
