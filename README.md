# 听匣 (TingXia)

本地有声书播放器 · Android 原生

## 产品

- **显示名**：听匣
- **包名**：`com.tingxia.app`
- **定位**：本地文件夹导入 → 章节连播 → 后台播放 → 断点续听 → 倍速 / 睡眠定时
- **第一版不做**：账号、云同步、在线书城

完整方案见 [方案.md](./方案.md)。

## 技术栈

Kotlin · Jetpack Compose · Hilt · Room · Media3 (ExoPlayer) · DataStore · Navigation Compose

- minSdk 26 / targetSdk 35
- 导入：SAF `OpenDocumentTree`，persistable URI，不复制大文件

## 构建

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions：推送后自动 `assembleDebug` 并上传 APK Artifact。

## 模块结构

```text
app/src/main/java/com/tingxia/app/
  ui/          # Compose 页面
  player/      # PlaybackService + PlayerController
  data/        # Room / 导入 / Repository
  di/          # Hilt
```
