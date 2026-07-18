# 听匣 (TingXia)

本地有声书播放器 · Android 原生

## 产品

- **显示名**：听匣
- **包名**：`com.tingxia.app`
- **定位**：本地文件夹导入 → 章节连播 → 后台播放 → 断点续听 → 倍速 / 睡眠定时
- **第一版不做**：账号、云同步、在线书城

## 当前能力

- 递归扫描本地目录，受控并发读取章节元数据
- 增量重扫与重新授权，保留章节身份、进度、书签和自定义标题
- 后台播放、锁屏/蓝牙控制、断点续听和逐书倍速
- 进程回收后从系统媒体中心恢复最近播放队列
- 睡眠定时、本章结束暂停、渐弱停止
- 书架搜索/排序/完成筛选，章节完成状态与批量标记
- 书籍信息、章节标题和书签备注管理
- 自定义封面与后台播放错误处理策略
- 浅色、深色、跟随系统主题

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
CI 同时运行 JVM 测试、Lint、Release/R8 构建，并在 API 26/35 模拟器验证 Room 迁移与数据库约束。
推送 `v*` tag 后，Release 工作流使用仓库 Secrets 构建并发布正式签名 APK/AAB。

## 模块结构

```text
app/src/main/java/com/tingxia/app/
  ui/          # Compose 页面
  player/      # PlaybackService + PlayerController
  data/        # Room / 导入 / Repository
  di/          # Hilt
```
