# Lyrics Plus Android

一个基于 Android 的歌词同步显示原型应用。本项目受 PC 端 Spicetify 的 **lyrics-plus** 自定义应用启发，通过监听 Spotify 的本地媒体广播，在安卓设备上实现高颜值、丝滑过渡的歌词同步滚动与特效显示。

---

## ✨ 核心特性

- 🎵 **多源歌词获取**：
  - 优先尝试从 **网易云音乐 (Netease)** 获取带翻译的双语同步歌词。
  - 自动回退到 **LRCLIB** 获取高质量的同步歌词。
- 🇯🇵 **日语假名标注 (Furigana)**：
  - 内置 **Kuromoji IPADIC** 日语分析器，自动为日语歌词行上方标注振假名（平假名读音），方便日语学习与跟唱。
- 🎨 **极佳的视觉特效 (WebView 驱动)**：
  - 歌词渲染通过内置 WebView 运行的本地前端微应用（位于 `app/src/main/assets/lyrics-web`）驱动。
  - 支持类似原生 Apple Music 的歌词模糊、动态缩放、平滑滚动、平移淡入淡出及翻译和读音标注特效。
- ⏱️ **高精度本地同步**：
  - 本地解析 LRC 时间戳，通过与 Spotify 播放进度对齐，实现超低延迟的平滑滚动。

---

## ⚙️ 手机端配置指南

为了让 App 能够完美接收 Spotify 的播放状态与歌词进度，请在手机上进行以下设置：

### 1. 开启 Spotify 设备广播
在 Spotify Android 客户端中，依次点击：
`设置与隐私 -> 应用程序和设备 -> 开启“设备广播状态” (Device Broadcast Status)`。
*(注：这是 Spotify 系统的安全机制，必须手动开启后，其他应用才能监听到当前的歌曲切换)*

### 2. 授予通知栏监听权限（强烈推荐）
在手机系统设置中，依次进入：
`系统设置 -> 应用与通知 -> 特殊应用权限 -> 设备和应用通知 -> 允许“Lyrics Plus”读取通知`。
* **为什么需要这个权限？**：
  * Spotify 本身的本地广播只在歌曲切换或播放状态改变时触发，不会频繁广播微秒级的当前进度。
  * 授权后，App 可以读取 Spotify 活跃媒体会话（Media Session）的播放位置。**即使您刚打开 App、刷新应用或手机息屏锁屏后返回，歌词进度也能瞬间对齐，无需等待下一首歌。**

---

## 🛠️ 编译与安装

本项目的发行包已为您编译完成。

1. **直接安装（推荐）**：
   * 将编译好的 [app-release.apk](app/build/outputs/apk/release/app-release.apk) 复制到您的 Android 手机上直接安装。
2. **自行编译**：
   * 用 **Android Studio** 打开 `android` 目录。
   * 直接运行 `app` 配置，或者在终端运行以下命令：
     ```bash
     # 编译 Debug 包
     ./gradlew.bat assembleDebug
     
     # 编译 Release 发行包
     ./gradlew.bat assembleRelease
     ```

---

## ⚖️ 开源协议

本项目采用 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。

### 协议选择依据：
由于本项目在设计理念与功能逻辑上深度参考了 PC 端著名的 **Spicetify lyrics-plus** 自定义应用（其所属的 Spicetify 整个生态采用 **GPL-3.0** 协议），根据开源协议的传染性与合规性要求，本项目同样采用 **GPL-3.0** 开源，以促进开源社区的健康发展与二次创作。
