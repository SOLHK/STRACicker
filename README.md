# STRACicker

STRA 连点器，当前针对 OnePlus Ace 5 Pro（PKR110）优化。

## 当前版本
v2.1.0

## 功能
- Material 3 Expressive 风格界面，控制台、速度测试、北京时间与悬浮窗统一视觉
- 支持系统浅色 / 深色外观
- Root + uinput 虚拟触摸
- 悬浮窗点位控制
- 停止 / 强制结束
- 点击速度测试
- 北京时间 NTP 校时
- Ace 5 Pro / Android 16 适配
- ARM64 触摸事件批量写入，降低每次点击的系统调用开销
- 单调时钟按完整点击周期调度，极速档目标周期 0.5 ms（实际速度受设备触控与调度限制）
- 测速使用纳秒时钟，避免用毫秒分辨率伪报微秒级平均间隔
- 急停 Root 清理在后台执行，独立校验 PID 后结束触摸进程
- 速度测试按 1 秒窗口实时统计；悬浮控制器提供 0.5/1/5/10 ms 快捷周期
- 悬浮窗默认不抢其它应用的输入焦点；编辑周期输入框时临时切换到键盘输入模式
- 固定签名由 GitHub Actions Secrets 中的 `STRA_SIGNING_KEYSTORE_BASE64` 与 `STRA_SIGNING_KEYSTORE_PASSWORD` 提供；未配置时只生成临时签名 debug 包

## 构建环境
- Java 17
- Gradle 8.9
- Android SDK 35
- Android NDK 27.2.12479018

签名私钥不包含在仓库中。

