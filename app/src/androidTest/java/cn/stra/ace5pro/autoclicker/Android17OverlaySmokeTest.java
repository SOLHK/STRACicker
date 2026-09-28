package cn.stra.ace5pro.autoclicker;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/** Runs the real app UI on API 37; native root tapping itself needs an Ace 5 Pro. */
@RunWith(AndroidJUnit4.class)
public final class Android17OverlaySmokeTest {
    private UiDevice device;
    private Context context;

    @Before public void launch() throws Exception {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device.executeShellCommand("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launch);
        UiScrollable page = new UiScrollable(new UiSelector().scrollable(true));
        assertTrue(page.scrollTextIntoView("开启悬浮控制器"));
        assertNotNull(device.wait(Until.findObject(By.text("开启悬浮控制器")), 10000));
        assertNull("duplicate overlay permission tile should be removed",
                device.findObject(By.text("悬浮权限")));
    }

    @Test public void overlayCanCollapseMoveExpandAndExportLogs() throws Exception {
        device.findObject(By.text("开启悬浮控制器")).click();

        UiObject2 collapse = device.wait(Until.findObject(By.text("收起")), 10000);
        assertNotNull("expanded overlay must show the collapse control", collapse);
        collapse.click();

        UiObject2 mini = device.wait(
                Until.findObject(By.desc("轻点暂停并展开，拖动移动位置")), 5000);
        assertNotNull("collapsed overlay must remain visible as a draggable clock pill", mini);
        assertNotNull("collapsed overlay must keep the Beijing time visible",
                mini.findObject(By.text(Pattern.compile("\\d{2}:\\d{2}:\\d{2}"))));
        Point before = mini.getVisibleCenter();
        mini.drag(new Point(before.x + 160, before.y + 180), 700);
        device.waitForIdle();
        mini = device.findObject(By.desc("轻点暂停并展开，拖动移动位置"));
        Point after = mini.getVisibleCenter();
        assertNotEquals("compact overlay must move when dragged", before, after);
        mini.click();
        assertNotNull("tapping the compact icon must expand the overlay",
                device.wait(Until.findObject(By.text("收起")), 5000));

        device.findObject(By.text("收起")).click();
        UiScrollable page = new UiScrollable(new UiSelector().scrollable(true));
        page.scrollTextIntoView("导出诊断日志");
        UiObject2 export = device.wait(Until.findObject(By.text("导出诊断日志")), 5000);
        assertNotNull("main screen must expose log export", export);
        export.click();
        assertNotNull("log export must open the Android share sheet",
                device.wait(Until.findObject(By.text("发送诊断日志")), 5000));

        File events = new File(context.getFilesDir(), "stra-events.txt");
        assertTrue(events.isFile());
        String log = new String(Files.readAllBytes(events.toPath()), StandardCharsets.UTF_8);
        assertTrue(log.contains("Overlay collapsed"));
        assertTrue(log.contains("Overlay expanded"));
    }

    @After public void stopService() {
        if (context != null) context.stopService(new Intent(context, OverlayService.class));
    }

    private static void assertTrue(boolean value) { org.junit.Assert.assertTrue(value); }
}
