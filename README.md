<div align="center">

# 🎧 listenDTV

**为"听"而生的开源安卓影音客户端**

电影 · 电视剧 · 直播 —— 默认听播模式，只出声音不解码画面，省电省内存，边听边做别的事

[![Release](https://img.shields.io/github/v/release/cookie-kangd/listenDTV?style=flat-square&label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/cookie-kangd/listenDTV/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/cookie-kangd/listenDTV/build.yml?style=flat-square&label=CI)](https://github.com/cookie-kangd/listenDTV/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-brightgreen?style=flat-square)]()
[![Engine](https://img.shields.io/badge/Player-media3%20%7C%20ExoPlayer%20%7C%20mpv-4285F4?style=flat-square)]()
[![License](https://img.shields.io/badge/License-%E5%AD%A6%E4%B9%A0%E4%BA%A4%E6%B5%81-lightgrey?style=flat-square)]()

[下载安装](#-下载安装) · [核心亮点](#-核心亮点) · [功能一览](#-功能一览) · [常见问题](#-常见问题) · [自行构建](#-自行构建)

基于 [FongMi/TV](https://github.com/FongMi/TV) 深度定制的开源 Android 影音应用：点播聚合、直播播放、实时弹幕、画中画小窗，以及独一无二的**默认听播模式**——像听播客一样"听"影视剧，手机、平板、电视盒子全支持。

</div>

> 上游项目：[FongMi/TV](https://github.com/FongMi/TV) · 相关项目：[dtv_mx 直播聚合客户端](https://github.com/cookie-kangd/dtv_mx)

---

## 📥 下载安装

前往 [**Releases（最新版）**](https://github.com/cookie-kangd/listenDTV/releases/latest) 下载对应 APK 安装即可：

| 设备 | 架构 | 文件 |
|---|---|---|
| 手机 / 平板（触屏） | arm64 | `mobile-arm64_v8a.apk` |
| 手机 / 平板（触屏） | armv7 | `mobile-armeabi_v7a.apk` |
| 电视 / 盒子（遥控器） | arm64 | `leanback-arm64_v8a.apk` |
| 电视 / 盒子（遥控器） | armv7 | `leanback-armeabi_v7a.apk` |

国内直连 GitHub 下载慢？把 APK 链接前面拼上加速镜像前缀即可：

| 来源 | 链接前缀 |
|---|---|
| GitHub 直连 | 无 |
| **Cloudflare (v4 推荐)** ⭐ | `https://v4.gh-proxy.org/` |
| Cloudflare (v4/v6) | `https://v6.gh-proxy.org/` |
| Fastly (v4) | `https://cdn.gh-proxy.org/` |

示例：`https://v4.gh-proxy.org/https://github.com/cookie-kangd/listenDTV/releases/download/v0.1.3/mobile-arm64_v8a.apk`

- **系统要求**：Android 7.0（API 24）及以上
- **应用内更新**：设置 → 关于 → 检查更新，发现新版本后直接下载安装（已内置加速镜像 + 直链兜底）
- **架构支持**：`arm64-v8a` / `armeabi-v7a`

---

## ✨ 核心亮点

### 🎧 听播模式（本项目的灵魂）

- **默认开启**：打开电影、电视剧自动进入听播，**彻底禁用视频轨**——不只不渲染画面，连视频解码都不发生，CPU / 内存 / 电量占用降到接近音乐播放器水平
- **一键切换**：播放时点击屏幕，耳机按钮亮起表示听播中，点一下即可恢复画面
- **完整控制**：快退 15 秒 / 播放暂停 / 快进 30 秒 / 画中画，横竖屏同一套按钮
- **后台不断**：听播状态退到桌面或锁屏，声音继续播
- **双引擎生效**：ExoPlayer 与 mpv 引擎统一支持

### 📺 电视 + 手机双端

- **leanback 版**：为遥控器和大屏优化，焦点导航、频道式直播
- **mobile 版**：触屏交互、手势快进快退、竖屏听播

---

## 🧭 功能一览

### 🎬 点播
- **多源聚合**：VOD 配置接口接入站点、爬虫（Spider Jar / Python / quickjs）
- **详情展示**：海报墙、简介、演员、选集、选源
- **播放记忆**：断点续播、收藏追剧、历史记录

### 📡 直播
- **多格式直播源**：Live 配置接口，频道 / 节目单
- **换源秒切**：频道即时切换，加载失败自动重试

### 🎞 播放体验
- **双播放引擎**：media3 ExoPlayer（硬解）/ mpv（软解），按需切换
- **画中画（PiP）**：系统级小窗悬浮，边看边做别的事
- **倍速播放**：0.5x–4.0x
- **字幕支持**：内封字幕 + 外挂 SRT/ASS，样式可调
- **音轨切换**：多音轨内容自由选择
- **跳过片头片尾**：手动设置跳过点

### 💬 弹幕
- **实时弹幕**：直播弹幕展示
- **显示定制**：字号、透明度、显示区域可调，一键开关

### 🧰 其它
- **内置 OTA 更新**：设置 → 关于 → 检查更新，自动匹配设备架构的 APK，加速镜像 + 直链双通道
- **退出自动清缓存**（默认开启）：退出 App 自动清理缓存与临时文件，保持干净
- **无痕模式**、**DoH 加密 DNS**、**主题色自定义**
- **DLNA 投放**、**Android Auto**、**遥控器 / 扫码绑定**

---

## ❓ 常见问题

<details>
<summary><b>听播模式怎么开关？</b></summary>

播放视频时点击屏幕呼出控制栏，点击最左边的耳机图标即可切换：图标亮起 = 听播（只听不看），图标变暗 = 正常播放。默认进入视频时自动开启听播。
</details>

<details>
<summary><b>听播模式下费电吗？</b></summary>

听播模式直接禁用了视频解码器，处理器不再做视频解码和渲染，耗电接近纯音频播放，长时间"听剧"非常省电。
</details>

<details>
<summary><b>应用内「检查更新」没反应？</b></summary>

v0.1.3 已修复：更新检查改走加速镜像优先 + 直链兜底，无论有无新版本都会给出提示（在 设置 → 关于 里）。
</details>

<details>
<summary><b>支持电视 / 盒子吗？</b></summary>

支持。下载 `leanback-*.apk` 即为电视版，专为遥控器交互优化。
</details>

<details>
<summary><b>怎么换播放源？</b></summary>

设置 → 点播，填入配置接口地址；直播同理（设置 → 直播）。
</details>

---

## 🛠 自行构建

**环境要求**

- JDK 21
- Android SDK（compileSdk 36）
- 构建链：Gradle 9.x + AGP 9.x

**构建命令**

```bash
# 手机版 Debug
./gradlew :app:assembleMobileDebug

# 电视版 Release（需签名：app/release.keystore，PKCS12，alias listenDTV）
./gradlew :app:assembleLeanbackRelease
```

> 签名：仓库内置固定 keystore（`app/release.keystore`，storeType PKCS12，密码 `android`），所有版本签名一致，可直接覆盖升级。

---

## 🧱 技术栈

| 类别 | 选型 |
|---|---|
| 语言 | Java（主）、Python（爬虫，chaquopy 运行时） |
| 播放器 | androidx.media3（ExoPlayer + mpv 扩展，composite build 源码编译） |
| 网络 | OkHttp、DoH |
| 数据库 | Room |
| 图片 | Glide 5 |
| 弹幕 | acfundanmaku |
| JS 引擎 | QuickJS、Rhino |
| CI/CD | GitHub Actions，自动构建 + Release 发布 |

---

## 📄 说明

- 本项目基于 [FongMi/TV](https://github.com/FongMi/TV) 二次开发，仅用于**学习与技术交流**，非任何平台官方产品
- 播放内容来自第三方公开接口，**版权归属第三方**，请遵守各平台用户协议，**勿作商业用途**

---

## 🔍 关键词

Android 影音客户端 · 听剧 App · 听播模式 · 省电播放器 · 点播聚合 · 直播聚合 · 开源 TV 应用 · 电视盒子播放器 · ExoPlayer · mpv · FongMi TV · 画中画 · 弹幕播放器 · OTA 自更新 · gh-proxy 加速下载

---

## 📚 开发者文档

| 文件 | 说明 |
|---|---|
| [CONFIG.md](docs/CONFIG.md) | Vod / Live 完整配置欄位說明 |
| [SPIDER.md](docs/SPIDER.md) | Spider 所有方法規格與回傳格式 |
| [LOCAL.md](docs/LOCAL.md) | 本地 HTTP API 所有端點完整說明 |
| [LIVE.md](docs/LIVE.md) | 直播來源格式完整說明 |
