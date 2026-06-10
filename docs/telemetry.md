# 匿名统计说明

Lyrics Plus Android 可以在正式构建中启用匿名统计，用来了解用户量、版本分布、功能使用情况和歌词源成功率。

## 收集内容

- 随机生成的匿名安装 ID。
- 随机生成的本次启动会话 ID。
- App 版本、versionCode、Android SDK、系统语言。
- Cloudflare 提供的粗粒度国家/地区代码。
- 事件名，例如 `app_open`、`feature_toggle`、`lyrics_fetch_result`。
- 少量事件属性，例如功能名、歌词源、成功或失败。

## 不收集内容

- 不收集歌曲名、歌手名、专辑名、歌词正文。
- 不收集 Spotify 账号、用户名或播放列表。
- 不收集 IMEI、Android ID、广告 ID、MAC 地址等设备唯一标识。
- 服务端 D1 数据库不保存 IP 地址。

## 关闭方式

如果构建中配置了统计端点，App 的悬浮设置菜单会显示“匿名统计”开关。关闭后客户端不会继续发送统计事件。

## 开发者构建

默认构建不会发送统计事件，因为 `STATS_ENDPOINT` 为空。

正式构建时可以通过 Gradle 参数注入公开端点：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\assemble-official-release.ps1
```

官方构建脚本读取 `scripts/official-build.env`，当前公开端点是：

```text
https://lyrics.artria.dpdns.org/v1/events
```

GitHub Actions Release workflow 也调用同一套脚本；如需临时覆盖端点，可以在本地或 GitHub repository variables 中设置 `LYRICS_PLUS_STATS_ENDPOINT`。
