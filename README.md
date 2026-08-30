# SmartLife · 大学生活助手

![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)
![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-orange.svg)

> 基于 Kotlin + Jetpack Compose 开发的 Android 大学生效率工具，集成待办、番茄专注、单双周课表、学期设置、数据导入导出等功能。

完全离线运行，无广告、无账号，所有数据本地存储，保护隐私。

---

## 功能展示

### 🏠 Dashboard 首页

今日日期、随机励志语（点按换一条），以及「今日待办 / 今日课程 / 今日专注」三张统计卡片——卡片可点击直达对应模块。

![首页](./screenshots/home.png)

### ✅ Todo 待办

新增 / 编辑 / 删除（长按确认）、完成勾选、三级优先级、**截止日期 + 时间**（精确到分钟）、实时搜索、逾期提示（已逾期 X 小时）。

![待办](./screenshots/todo.png)

### 🍅 Focus 番茄钟

预设 15 / 25 / 45 / 60 分钟 + 自定义（5~180 分钟）；开始 / 暂停 / 继续 / 结束；圆形倒计时动画；退出页面计时不中断；结束经 WorkManager 本地通知提醒。

![专注](./screenshots/focus.png)

### 📚 Timetable 课表

周一至周日切换、**多星期课程**、**单双周（每周 / 单周 / 双周）**、**学期设置**（自定义开学日期，自动计算当前周数与单双周）、考试倒计时、任课老师 / 教室 / 时间。

![课表](./screenshots/timetable.png)

### 👤 Profile 我的

数据统计（总待办 / 已完成 / 总专注时长 / 完成轮数）、**当前学期课程**入口、主题切换（跟随系统 / 浅色 / 深色）、JSON 一键导入导出、学期设置、关于。

> 截图由真实设备 / 模拟器补充后替换 `screenshots/` 下同名文件即可。

---

## 技术栈

| 层次 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3（深色模式自适应） |
| 架构 | MVVM（ViewModel + StateFlow + Repository） |
| 本地数据库 | Room（自动 Migration，无破坏性迁移） |
| 导航 | Navigation Compose（底部五 Tab + 二级页面） |
| 偏好持久化 | DataStore Preferences（主题模式、学期设置） |
| 后台任务 | WorkManager（专注结束本地通知） |
| 数据备份 | JSON（Android 内置 org.json，完全离线） |

---

## 项目结构

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
│       │   │   ├── local/                 # Room：entity / dao / AppDatabase(+Migration) / Converters
│       │   │   │   ├── entity/            # Task / Course / FocusSession / Quote
│       │   │   │   └── dao/               # TaskDao / CourseDao / FocusSessionDao / QuoteDao / BackupDao
│       │   │   └── repository/            # Task / Course / FocusSession / Quote / Settings
│       │   ├── di/ServiceLocator.kt       # 轻量服务定位
│       │   ├── ui/
│       │   │   ├── components/            # DateField / TimeField（统一日期时间组件）
│       │   │   ├── navigation/            # Routes + NavGraph
│       │   │   ├── screen/                # dashboard / todo / focus / timetable / profile / semester
│       │   │   └── theme/                 # Color / Theme / Type / Shape / ThemeMode
│       │   ├── util/                      # DateUtils / WeekUtils / JsonBackup
│       │   └── worker/                    # FocusReminderWorker（通知）
│       └── res/                           # 资源与图标
├── screenshots/                           # 界面截图（占位）
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradle/wrapper/                        # Gradle Wrapper 8.9
```

---

## 架构图

```
              ┌─────────────────────┐
              │   Compose UI        │   (Screen)
              └──────────┬──────────┘
                         ↓ 状态订阅 / 事件
              ┌──────────┴──────────┐
              │   ViewModel         │   (StateFlow + viewModelScope)
              └──────────┬──────────┘
                         ↓ 数据流 / 写操作
              ┌──────────┴──────────┐
              │   Repository        │
              └──────────┬──────────┘
                         ↓
        ┌────────────────┴────────────────┐
        │                                 │
   ┌────┴─────┐                     ┌─────┴──────┐
   │   Room   │                     │  DataStore │
   │ (SQLite) │                     │(Preferences)│
   └──────────┘                     └────────────┘
```

- **UI 层**：Compose 声明式界面，订阅 ViewModel 暴露的 StateFlow。
- **ViewModel 层**：持有业务状态，响应 UI 事件，调用 Repository。
- **Repository 层**：封装数据来源，Room 返回 Flow 实现响应式更新。
- **数据层**：Room 存业务数据（待办/课程/专注/励志语），DataStore 存全局偏好（主题、学期）。

---

## 环境要求

- **Android Studio**（新版即可，自带 JDK）
- **最低 Android 版本**：Android 7.0（API 24，minSdk 24）
- **目标 / 编译 SDK**：34
- **JDK**：17
- **Gradle**：8.9（Wrapper 已内置）；AGP 8.7.0；Kotlin 2.0.21

---

## 如何运行

1. 安装 **Android Studio**；
2. `File → Open` 选择本目录 `SmartLife/`；
3. 等待 Gradle Sync 完成（首次需联网下载依赖）；
4. 点击 **Run ▶** 选择模拟器或真机运行。

## 如何构建 APK

```bash
# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（默认调试签名，便于直接安装）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 数据备份 / 恢复

- **导出**：「我的」→「数据管理」→「导出 JSON」→ 系统分享保存；
- **导入**：「导入 JSON」→ 选择备份文件；
- 备份内容：待办、课程、专注记录、励志语；
- 安全机制：导入前完整校验 JSON，校验通过后在**单个事务**内替换，失败自动回滚，非法 / 版本不符的文件被拒绝。

---

## License

[MIT License](./LICENSE) · Copyright © 2026 SmartLife
