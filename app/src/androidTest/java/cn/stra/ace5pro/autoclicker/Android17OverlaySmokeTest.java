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

/** Compiles in CI; floating-pill runtime checks can run on a real or API 37 device. */
@RunWith(AndroidJUnit4.class)
public final class Android17OverlaySmokeTest {
    private UiDevice device;
    private Context context;

    @Before public void launch() throws Exception {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device.executeShellCommand("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        device.executeShellCommand("pm grant " + context.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launch);
        UiScrollable page = new UiScrollable(new UiSelector().scrollable(true));
        assertTrue(page.scrollTextIntoView("开启悬浮控制器"));
        assertNotNull(device.wait(Until.findObject(By.text("开启悬浮控制器")), 10000));
        assertNull(device.findObject(By.text("悬浮权限")));
    }

    @Test public void oneFloatingPillShowsClockCanMoveAndLeavesSettingsInApp() throws Exception {
        UiObject2 settings = device.wait(Until.findObject(By.text("点击设置")), 5000);
        assertNotNull(settings);
        settings.click();
        assertNotNull(device.wait(Until.findObject(By.text("点击周期（毫秒）")), 5000));
        assertNotNull(device.findObject(By.text("0.5 ms")));
        assertNotNull(device.findObject(By.text("点击点位")));
        device.pressBack();

        device.findObject(By.text("开启悬浮控制器")).click();
        UiObject2 pill = device.wait(Until.findObject(By.desc("点按开始，拖动移动位置")), 10000);
        assertNotNull("only the small time pill should float", pill);
        assertNotNull("the compact pill must keep showing Beijing time",
                pill.findObject(By.text(Pattern.compile("\\d{2}:\\d{2}:\\d{2}"))));
        assertNull(device.findObject(By.text("STRA · 点击控制")));
        assertNull(device.findObject(By.text("缩小")));
        assertNull(device.findObject(By.text("开始")));
        assertNull(device.findObject(By.text("停止")));

        Point before = pill.getVisibleCenter();
        pill.drag(new Point(before.x + 120, before.y + 100), 650);
        device.waitForIdle();
        pill = device.findObject(By.desc("点按开始，拖动移动位置"));
        assertNotNull(pill);
        assertNotEquals("the pill must remain draggable", before, pill.getVisibleCenter());

        UiScrollable page = new UiScrollable(new UiSelector().scrollable(true));
        page.scrollTextIntoView("导出诊断日志");
        UiObject2 export = device.wait(Until.findObject(By.text("导出诊断日志")), 5000);
        assertNotNull(export);
        export.click();
        assertNotNull(device.wait(Until.findObject(By.text("发送诊断日志")), 5000));

        File events = new File(context.getFilesDir(), "stra-events.txt");
        assertTrue(events.isFile());
        String log = new String(Files.readAllBytes(events.toPath()), StandardCharsets.UTF_8);
        assertTrue(log.contains("Overlay opened"));
    }


    @Test public void addPointOpensTemporaryFloatingPickerSession() throws Exception {
        UiScrollable page = new UiScrollable(new UiSelector().scrollable(true));
        page.scrollTextIntoView("点击设置");
        device.findObject(By.text("点击设置")).click();
        UiObject2 addPoint = device.wait(Until.findObject(By.text("＋ 添加点位")), 5000);
        assertNotNull(addPoint);
        addPoint.click();

        UiObject2 addInFloat = device.wait(Until.findObject(By.desc("添加一个点位")), 10000);
        assertNotNull("point setup should leave a small floating tool over the home screen", addInFloat);
        assertNotNull(device.findObject(By.desc("拖动点位工具条")));
        addInFloat.click();
        assertNotNull("the transparent picker should appear only while choosing a coordinate",
                device.wait(Until.findObject(By.text("点屏幕添加位置 · 点这里取消")), 5000));
        device.click(300, 600);
        assertNotNull(device.wait(Until.findObject(By.desc("添加一个点位")), 5000));
        UiObject2 done = device.findObject(By.desc("完成点位设置并返回设置页"));
        assertNotNull(done);
        done.click();
        assertNotNull("finishing should return to the point settings screen",
                device.wait(Until.findObject(By.text("点击设置")), 10000));
    }

    @After public void stopService() {
        if (context != null) context.stopService(new Intent(context, OverlayService.class));
    }

    private static void assertTrue(boolean value) { org.junit.Assert.assertTrue(value); }
}
