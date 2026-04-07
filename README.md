# Block Short Videos

> 一款基于 TensorFlow Lite 实现的 Android 短视频自动拦截工具，伪装为系统组件以避免被发现。

## ✨ 功能特性

### 🛡️ 短视频实时检测与拦截
- 利用 **Android 无障碍服务（Accessibility Service）** 周期性截取屏幕
- 内置 TFLite 二分类模型（`Bili_Detector_V2`），对截图进行实时推理，判断当前界面是否为短视频
- 检测到短视频时自动触发系统返回（`GLOBAL_ACTION_BACK`），阻止用户继续观看

### ⏱️ 智能检测频率
| 模式 | 截图间隔 | 触发条件 |
|------|----------|----------|
| 常规检测 | 10 秒 | 解锁屏幕后自动开始 |
| 激活检测 | 2 秒 | 检测到短视频后进入，持续 30 分钟 |

### 🙈 高度隐蔽设计
- **伪装身份**：包名为 `com.android.webview`，桌面名称显示为 **Android System Webview**，图标采用 Android System WebView 官方图标
- **隐藏桌面图标**：支持定时隐藏（自定义小时/分钟/秒）和永久隐藏
- **隐藏后台卡片**：可从最近任务列表中隐藏，防止被发现
- **秘密唤起方式**：图标隐藏后，在拨号盘输入 `*#*#7266#*#*` 即可重新打开设置界面

## 🏗️ 项目架构

```
app/src/main/
├── AndroidManifest.xml              # 应用清单（含 Activity Alias、无障碍服务、广播接收器）
├── assets/
│   ├── Android system webview.png   # 伪装图标原图
│   ├── bili_binary_model_v2.tflite  # TFLite 二分类模型
│   └── model_protocol.json          # 模型协议（输入输出规格、归一化参数）
├── java/com/android/webview/
│   ├── MainActivity.kt             # 主界面：服务状态、图标管理、后台隐藏控制
│   ├── BlockAccessibilityService.kt # 核心：无障碍服务，截图→推理→返回
│   ├── ModelInferenceHelper.kt      # TFLite 模型加载、预处理（PadWhite→Resize→Normalize）、推理
│   ├── IconHideManager.kt           # 桌面图标显示/隐藏管理（通过 Activity Alias 启停）
│   ├── AppPreferences.kt           # SharedPreferences 封装
│   ├── ScreenUnlockReceiver.kt     # 监听屏幕解锁，触发检测循环
│   └── SecretCodeReceiver.kt       # 监听秘密拨号码，重新打开 MainActivity
└── res/
    ├── layout/activity_main.xml    # Material Design 主界面布局
    ├── xml/accessibility_service_config.xml  # 无障碍服务配置
    └── values/strings.xml          # 字符串资源
```

## 🔧 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 9.0.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 35 (Android 15) |
| TensorFlow Lite | 2.17.0 |
| Material Components | 1.13.0 |
| AndroidX AppCompat | 1.7.1 |

## 🚀 快速开始

### 环境要求
- Android Studio Meerkat 或更新版本
- JDK 11+
- Android 15（API 35）或更高版本的设备/模拟器

### 构建与安装

```bash
# 克隆仓库
git clone https://github.com/<your-username>/blockshortvideos.git
cd blockshortvideos

# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 使用步骤

1. **安装并打开应用** — 桌面将显示为 "Android System Webview"
2. **开启无障碍服务** — 点击按钮跳转至系统无障碍设置，找到本应用并启用
3. **（可选）隐藏桌面图标** — 可选择定时隐藏或永久隐藏
4. **（可选）隐藏后台卡片** — 开启后应用不会出现在最近任务列表中
5. 应用将在后台自动运行，检测并拦截短视频

### 恢复图标

当桌面图标被隐藏后，在系统拨号盘输入：

```
*#*#7266#*#*
```

即可重新打开设置界面。

## 🧠 模型说明

| 属性 | 值 |
|------|------|
| 模型名称 | Bili_Detector_V2_AutoCalibrated |
| 输入尺寸 | 1 × 603 × 295 × 3 (NHWC, float32) |
| 输出 | 1 × 2 (float32) |
| 分类标签 | `0` = notrelated, `1` = shortvideo |
| 训练样本数 | 8,928 |
| 归一化 | mean=[0.5679, 0.5574, 0.5495], std=[0.399, 0.3966, 0.4008] |

**预处理流程**：原始截图 → 等比缩放适配 295×603 → 白色画布居中填充 → 像素归一化

## ⚠️ 注意事项

- 本应用需要 **无障碍服务权限** 和 **截屏权限** 才能正常工作
- 仅支持 **Android 15（API 35）** 及以上版本
- 目前模型针对 **哔哩哔哩** 短视频界面进行训练，对其他平台的识别效果可能有限
- 本项目仅供学习和个人使用，请合理使用

## 📄 许可证

本项目采用 [The Unlicense](LICENSE) 发布至公共领域，可自由使用。

