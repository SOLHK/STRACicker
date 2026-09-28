# STRACicker

STRA 连点器，当前针对 OnePlus Ace 5 Pro（PKR110）优化。

## 当前版本
v2.0.5

## 功能
- Root + uinput 虚拟触摸
- 悬浮窗点位控制
- 停止 / 强制结束
- 点击速度测试
- 北京时间 NTP 校时
- Ace 5 Pro / Android 16 适配
- ARM64 触摸事件批量写入，降低每次点击的系统调用开销
- 使用单调时钟按目标点击周期调度，快速档目标周期 4 ms
- 测速使用纳秒时钟，避免用毫秒分辨率伪报微秒级平均间隔

## 构建环境
- Java 17
- Gradle 8.9
- Android SDK 35
- Android NDK 27.2.12479018

签名私钥不包含在仓库中。
