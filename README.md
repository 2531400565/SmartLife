# SmartLife · 大学生活助手

一款完全离线的 Android 大学生活管理应用：待办清单、番茄专注、课程表与考试倒计时，所有数据本地存储，无需联网、无广告、无账号。

## 功能列表

- **首页 Dashboard**：今日日期、今日待办数量、今日课程数量、今日专注时长、随机励志语（点按换一条）
- **待办 Todo**：新增 / 编辑 / 删除（长按确认）、完成勾选（删除线）、三级优先级、截止日期、实时搜索、未完成置顶
- **专注番茄钟 Focus**：25 / 45 / 60 分钟、开始 / 暂停 / 继续 / 结束、圆形倒计时动画、MM:SS 实时显示、完成后自动入库、WorkManager 本地通知提醒、今日累计统计、退出页面计时不中断
- **课程表 Timetable**：周一至周日切换（默认今天）、当天课程列表、任课老师 / 教室 / 时间、考试倒计时（今天考试 / 距考试 X 天）、课程增删改
- **我的 Profile**：数据统计（总待办 / 已完成 / 总专注时长 / 完成轮数 / 课程总数）、JSON 一键导出（系统分享）与导入恢复（事务回滚、非法 JSON 拒绝）、主题切换（跟随系统 / 浅色 / 深色）、关于

## 技术栈

| 层次 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3（深色模式自适应） |
| 架构 | MVVM（ViewModel + StateFlow + Repository） |
| 本地数据库 | Room（含 v1→v2 自动 Migration，无破坏性迁移） |
| 导航 | Navigation Compose（底部五 Tab） |
| 后台任务 | WorkManager（专注结束本地通知） |
| 偏好持久化 | DataStore Preferences（主题模式） |
| 数据备份 | JSON（Android 内置 org.json，完全离线） |

## 项目目录结构

```
SmartLife/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/smartlife/app/
│       │   ├── MainActivity.kt            # 入口，主题模式应用
│       │   ├── SmartLifeApplication.kt
│       │   ├── data/
│       │   │   ├── local/                 # Room：entity / dao / AppDatabase(+Migration)
│       │   │   └── repository/            # Task/Course/FocusSession/Quote/Settings Repository
│       │   ├── di/ServiceLocator.kt       # 轻量服务定位
│       │   ├── ui/
│       │   │   ├── navigation/            # Routes + NavGraph（五 Tab）
│       │   │   ├── screen/                # dashboard / todo / focus / timetable / profile
│       │   │   └── theme/                 # Color / Theme / Type / Shape / ThemeMode
│       │   ├── util/                      # DateUtils / JsonBackup
│       │   └── worker/                    # FocusReminderWorker（通知）
│       └── res/                           # 资源与图标
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradle/wrapper/                        # Gradle Wrapper 8.9
```

## 环境要求

- **最低 Android 版本**：Android 7.0（API 24，minSdk 24）
- 目标 / 编译 SDK：34
- JDK 17、Gradle 8.9（Wrapper 已内置）、AGP 8.7.0

## 如何打开

1. 安装 **Android Studio**（新版即可，自带 JDK）；
2. `File → Open` 选择本目录 `SmartLife/`；
3. 首次同步会自动下载依赖（需联网），等待 Gradle Sync 完成；
4. 点击 **Run ▶** 选择模拟器或真机运行。

## 如何构建 APK

命令行（项目根目录）或 Android Studio 的 Build 菜单：

```bash
# 清理并构建 Debug APK
./gradlew clean
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

> Release 构建默认使用调试签名（`isMinifyEnabled = false`），便于直接安装；如需上架，请在 `app/build.gradle.kts` 配置正式签名。

## APK 输出位置

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

## 数据备份 / 恢复

- **导出**：「我的」→「数据管理」→「导出 JSON」→ 通过系统分享保存到任意位置；
- **导入**：「导入 JSON」→ 选择备份文件；
- 备份内容：待办、课程、专注记录、励志语（version 1 格式）；
- 安全机制：导入前完整校验 JSON；校验通过后在**单个事务**内完成替换，失败自动回滚，不会产生半套数据或破坏现有数据；非法 / 版本不符的文件会被拒绝。

## License

MIT License —— 详见 [LICENSE](LICENSE)。

Copyright © 2026 SmartLife
