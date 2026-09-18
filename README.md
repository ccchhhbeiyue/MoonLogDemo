# MoonLog

生理期预测日历 + 待办 + 计划 + 番茄钟，四合一的纯本地 Android 应用。

**无网络权限、无账号、无第三方统计/推送 SDK**——所有数据只存在设备本地的 SQLite 里，卸载即清除。

- 语言 / UI：Kotlin 2.2 + Jetpack Compose（Material 3）
- 持久化：Room 2.8（SQLite），显式版本迁移
- 计时：前台服务（Foreground Service），锁屏 / 切后台不中断
- 规模：61 个 Kotlin 源文件、约 6500 行，含预测算法的 JVM 单元测试

---

## 功能一览

### 日历（首页）

| 能力 | 说明 |
|---|---|
| 经期记录 | 点日期记录 / 编辑 / 删除，含流量、症状、备注 |
| 周期预测 | 历史周期中位数（抗异常值）+ 黄体期恒定倒推排卵日 + 置信度分级 |
| 一键确认 | 点「预测经期」的日子可确认转为真实记录，自动参与下一轮预测 |
| 月格渲染 | 真实经期深粉带 / 预测经期浅粉带 / 易孕窗浅紫带 / 排卵日浅黄圆 |
| 易孕窗开关 | 易孕窗与排卵日**默认隐藏**（生育力信息隐私敏感），设置页可开启 |
| 月格小点 | 蓝点 = 当天有待办截止；棕点 = 当天有每日计划生效；两者都有则并排 |
| 当日面板 | 经期详情、当天待办（勾选 / 新增）、当天每日计划（含已专注分钟） |
| 二级页 | 历史记录列表；统计页（周期折线趋势、平均周期、规律性评分、番茄 / 待办完成率） |

### 待办

优先级、分类、截止日（新建时默认当天）、手动排序、完成勾选。
删除待办时同事务解除番茄记录对它的引用（多态外键置空，保留专注流水）。

### 计划

- 三种计时模式：**番茄钟 / 倒计时 / 正计时**，启动后跳转专注页复用其控制界面
- **每日计划**：开启后从创建日起每天出现在日历当日面板，日期带棕点
- **详情统计**：累计次数、累计时长、坚持天数、日均时长、最近一次、中途放弃次数

### 专注（番茄钟）

- 前台服务计时，通知栏常驻显示剩余 / 已走时长
- 三种结束语义：**完成**（提前结算，按已走分钟落库）、**停止**（正计时）、**放弃**（不计完成）
- 可关联某个待办或计划，专注分钟自动聚合到对应对象上
- 今日番茄计数与最近历史列表

### 设置

周期默认值（冷启动预测用）、易孕窗口显示开关、番茄钟四参数、通知开关。
无「保存」按钮：单行配置表每次改动即一次完整提交，改完即时生效。

---

## 技术栈

| 项 | 版本 / 选型 | 备注 |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | 内置 Kotlin，注解处理走 KSP |
| Kotlin | 2.2.10 | KSP 2.2.10-2.0.2 前缀必须一致 |
| Compose BOM | 2026.02.01 | 统一管理 Compose 各制品版本 |
| Room | 2.8.5 | `exportSchema = true`，schema 快照入库 |
| Lifecycle | 2.11.0 | `collectAsStateWithLifecycle` |
| Navigation Compose | 2.10.1 | 底部四 Tab + 二级页面 |
| 月历组件 | kizitonwose calendar-compose 2.9.1 | **不可升 2.10.x**（Kotlin 2.3 构建，metadata 不兼容） |
| 协程 | kotlinx-coroutines 1.9.0 | 显式声明，不靠传递依赖 |
| minSdk / targetSdk | 26 / 37 | API 26 原生支持 `java.time`，无需 desugaring |
| 依赖注入 | 手写 `AppContainer` | 不用 Hilt：`by lazy` 链式单例，零反射零学习成本 |
| 图标 | 全部自绘 `ImageVector` | material-icons 制品已停止维护，Material3 1.4+ 不再传递 |

---

## 架构

```
ui/            Screen(Composable) + ViewModel      视图与展示状态
domain/        predictor/ 纯算法 + model/ 字典枚举  零 Android 依赖，可 JVM 单测
data/
  repository/    对外唯一入口，封装跨表事务与不变量
  local/dao/     Room 接口，SQL 写在注解里（编译期校验）
  local/entity/  表结构映射
service/       PomodoroService 前台服务             计时真相所在
di/            AppContainer + ViewModelFactory      手写 IoC
```

依赖方向单向向下：UI 从不直接摸 DAO。

### 贯穿全项目的数据流范式

```
SQLite 表
  ↓  Room @Query 返回 Flow<List<T>>        表变更自动推送
Repository                                包一层，需要时 combine 多表 / 守不变量
  ↓
ViewModel  .stateIn(WhileSubscribed(5000))  收敛成 StateFlow；5 秒宽限扛住旋转/切 Tab
  ↓
Screen  collectAsStateWithLifecycle()       生命周期安全订阅，退后台自动断
  ↓  Compose 重组
UI 自动更新
```

因此全项目几乎没有「操作完手动刷新列表」的代码：删一条待办 → DAO 写库 →
Flow 推新列表 → UI 自动重绘。

### 几个关键设计

- **计划不存进度**：今日分钟由「当日 link 到该计划的 session 聚合」现算，
  避免模板表里冗余一个会被写坏的计数器。
- **多态外键**：`pomodoro_session` 用 `(link_type, link_id)` 指向 todo / plan，
  不建 FK 约束；引用完整性由 Repository 的删除事务显式维护。
- **日期存 INTEGER**：`LocalDate` → epochDay、时刻 → epochMillis，
  由 TypeConverter 双向转换，范围查询可走索引比较。
- **统计口径收口在 SQL**：条件聚合（`SUM(CASE WHEN …)`）一次扫表出多个指标，
  UI 层不做第二套计算。
- **按需订阅**：详情类数据走 `MutableStateFlow<id> + flatMapLatest`，
  弹窗打开才查库、关闭即取消订阅，列表接口不被拖垮。

---

## 数据库

Room + SQLite，库文件 `moonlog.db`，当前 **version = 4**。

| 表 | 用途 | 关键字段 |
|---|---|---|
| `period_record` | 经期记录 | `start_date` / `end_date`(epochDay)、`flow`、`symptoms`、`note` |
| `todo` | 待办 | `due_date`(可空=无期限)、`priority`、`is_done`、`category`、`sort_order` |
| `plan` | 计划模板 | `mode`(0 番茄/1 倒计时/2 正计时)、`target_minutes`、`is_daily` |
| `pomodoro_session` | 专注流水 | `planned_minutes`、`actual_minutes`、`is_completed`、`link_type`+`link_id`、`mode` |
| `user_setting` | 单行配置（id 恒为 1） | 周期默认值、番茄四参数、`notification_enabled`、`show_fertile_window` |
| `work_log` | 旧工作日志 | UI 已下线，**表与历史数据保留**（接口下线但不 drop 表） |

### 迁移链

| 迁移 | 内容 |
|---|---|
| v1 → v2 | 新增 `plan` 表；`pomodoro_session` 加 `mode` 列（默认 0 免回填） |
| v2 → v3 | `plan` 加 `is_daily` 列（默认 0 免回填） |
| v3 → v4 | `user_setting` 加 `show_fertile_window` 列（默认 0 = 隐藏，存量用户升级即收起） |

每列都用 `NOT NULL DEFAULT` 兜底，老行免回填即归入正确语义（等价于在线 DDL 带默认值加列）。

`app/schemas/<DB 全名>/1~4.json` 是各版本表结构快照，作用等同 Flyway 的版本化基线，
**必须随代码提交**，否则 Room 无法生成 / 校验后续迁移。

> ⚠️ `AppDatabase` 里的 `fallbackToDestructiveMigration()` 是 demo 阶段安全网
> （未覆盖的版本跨度才删库重建）。**正式发布前必须移除**，改为手写 Migration。

---

## 项目结构

```
app/src/main/java/com/example/demo/
├── DemoApplication.kt          持有 AppContainer
├── MainActivity.kt             单 Activity + Compose 入口
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt      库定义 + 迁移链 + 双重检查锁单例
│   │   ├── converter/          日期 TypeConverter
│   │   ├── dao/                6 个 DAO（含 GROUP BY / 条件聚合投影）
│   │   └── entity/             6 个 Entity
│   └── repository/             6 个 Repository
├── domain/
│   ├── model/                  纯业务字典（TimerMode / PomodoroLinkType / Symptom …）
│   └── predictor/              CyclePredictor 预测算法（纯函数）
├── di/                         AppContainer + DemoViewModelFactory
├── navigation/                 底部导航与路由
├── service/                    PomodoroService 前台服务 + 全局计时状态
└── ui/
    ├── calendar/               日历页、历史页、记录对话框、经期区间展开
    ├── todo/  plan/  focus/    三个 Tab
    ├── stats/  settings/       二级页
    ├── shared/                 跨 Tab 共享的「选中日」ViewModel
    ├── theme/                  主题、色板、自绘图标
    └── common/                 脚手架期遗留的占位组件（已无引用）
app/src/test/…/CyclePredictorTest.kt   预测算法 10 个 JUnit 用例
```

---

## 构建与运行

**环境**：Android Studio（Quail 或更新）、JDK 11+、Android SDK 37。
`local.properties` 不入版本库，clone 后由 IDE 按本机 SDK 路径自动生成。

```bash
# 编译 debug 包
./gradlew :app:assembleDebug

# 装到已连接的设备 / 模拟器
./gradlew :app:installDebug

# 跑预测算法单测
./gradlew :app:testDebugUnitTest
```

**装机要求**：Android 8.0（API 26）及以上；纯 Kotlin 无 native 库，全 ABI 兼容。

**release 包**：仓库**不含任何签名配置与 keystore**（刻意如此）。
`assembleRelease` 产出的是未签名包，无法直接安装；自行发布需先用 `keytool`
生成 keystore 并在 `build.gradle.kts` 配置 `signingConfig`，
密码走 `local.properties` 或环境变量，**不要硬编码进构建脚本、不要把 keystore 提交进 Git**。

---

## 隐私说明

- `AndroidManifest.xml` **未声明 `INTERNET` 权限**，应用不具备联网能力；
  仅申请 `POST_NOTIFICATIONS` 与前台服务所需权限。
- 无账号体系、无云端同步、无埋点。
- 易孕窗口与排卵日标记默认隐藏，由用户在设置页显式开启。
- `allowBackup="true"` 仅参与系统级备份（如 Google One），不涉及本应用主动上传。
- 预测结果仅供参考，不构成医疗建议。

---

## 已知取舍 / 技术债

- `ui/common/` 下两个占位组件为脚手架期遗留，已无引用，可删。
- `work_log` 表保留但 UI 下线；历史番茄记录仍可能 link 到它。
- `applicationId = com.example.demo` 含保留字 `example`，**无法上架 Google Play**；
  正式发布需改为自有域名反写包名。
- 设置项的「读-改-写」非原子；当前调用方仅单线程 UI，无并发问题。
