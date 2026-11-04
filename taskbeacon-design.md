# TaskBeacon 通用任务状态监视器 — 工程设计文档

> 工作名：TaskBeacon  
> 文档版本：v0.1  
> 日期：2026-09-10  
> 目标读者：负责实现该项目的 Codex / Java 开发者

---

## 0. 给实现者的执行要求

本项目要实现一个**独立于被监控工具窗口的桌面任务状态监视器**。用户可以继续在 Terminal、IDE 或其他应用中工作，TaskBeacon 以悬浮窗 / 桌面宠物 / 系统托盘的形式持续显示后台任务当前状态。

第一版以 **Codex CLI** 作为第一个数据源，但架构不得与 Codex 强耦合。后续应该能够在不修改 Core 和 Desktop UI 的前提下增加 Claude Code、Jenkins、GitHub Actions、自研 Agent、构建任务等数据源。

实现时按以下优先级处理冲突：

1. 本文的“验收标准”。
2. Core 与 Source API 的解耦要求。
3. 可运行、可测试、可维护。
4. UI 美观和动画效果。

不要为了未来扩展而一次性实现所有功能。**MVP 必须简单，但边界必须正确。**

---

## 1. 产品目标

TaskBeacon 解决的问题是：当 AI Agent、CLI、CI/CD 或其他长任务在后台执行时，用户不应该反复切回原窗口确认“它现在在做什么”“是不是卡住了”“是否正在等我批准”“是否已经完成”。

产品应提供：

- 一个与任务来源无关的统一任务模型。
- 一个始终可见但不打扰用户的桌面状态入口。
- 多任务同时运行时的聚合与优先级展示。
- 清晰区分“运行中”“当前活动”“需要用户操作”“终态”。
- 插件化的数据源接入机制。
- 对数据源能力差异的容错，而不是要求所有数据源都能提供相同粒度的信息。

### 1.1 典型使用场景

用户在终端运行 Codex，让其修改项目。用户切到浏览器或 IDE 后，TaskBeacon 仍显示：

```text
Codex · backend-service
Thinking…
00:48
```

随后状态可以变为：

```text
Codex · backend-service
Running tests
./mvnw test
01:12
```

如果需要用户批准：

```text
Codex · backend-service
Needs approval
Click to inspect
```

任务完成后：

```text
Codex · backend-service
Completed ✓
2m 14s
```

---

## 2. MVP 范围

### 2.1 MVP 必须实现

1. Java 21 桌面应用。
2. 通用 `source-api`，Core 不引用任何 Codex 专用类型。
3. 统一事件模型与状态归约器（Reducer）。
4. 一个 Codex 数据源实现。
5. 至少支持 Codex `exec --json` JSONL 事件流。
6. 独立 always-on-top 小悬浮窗。
7. 系统托盘入口。
8. 多任务状态管理。
9. 当前最需要注意的任务自动成为主展示任务。
10. 本地配置和日志。
11. 单元测试 + Codex JSONL fixture 测试。
12. Windows / macOS / Linux 的架构兼容；构建包可以按 OS 分别产出。

### 2.2 MVP 明确不做

- 不实现云端账号系统。
- 不实现任务远程控制。
- 不在第一版支持从 TaskBeacon 中直接批准 Agent 操作。
- 不实现聊天历史浏览器。
- 不实现虚假的 LLM 百分比进度。
- 不要求第一版支持第三方 JAR 热加载；但 Source API 必须为后续插件化保留边界。
- 不解析或持久化完整 Prompt、模型回复和代码内容，除非后续用户明确开启。

---

## 3. 核心设计原则

### 3.1 Source 与 UI 完全解耦

UI 不应该知道 `turn.started`、Codex JSON-RPC、Jenkins build status 等外部协议。

所有外部系统都先经过 Adapter 转换成 TaskBeacon 的统一事件：

```text
Codex / Claude / Jenkins / GitHub Actions / Custom System
                         │
                         ▼
                 TaskSource Adapter
                         │
                         ▼
                    TaskEvent
                         │
                         ▼
                   State Reducer
                         │
                         ▼
                    TaskState
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
          Pet UI       Tray      Mini Panel
```

### 3.2 生命周期、当前活动、人工介入必须分开

不要使用一个超大枚举：

```text
THINKING
RUNNING_COMMAND
WAITING_APPROVAL
EDITING
...
```

否则随着数据源增加会出现状态爆炸。

应把状态拆成三个维度：

- `Lifecycle`：这个任务总体处于什么生命周期。
- `Activity`：任务当前正在做什么。
- `Attention`：是否需要用户介入。

例如：

```text
Lifecycle = RUNNING
Activity  = EXECUTING_COMMAND
Attention = APPROVAL_REQUIRED
```

三个信息可以同时成立。

### 3.3 不伪造进度百分比

LLM/Agent 通常无法可靠知道整个任务完成百分比。

因此默认使用：

```text
ProgressMode.INDETERMINATE
```

UI 展示当前阶段、动作、已耗时、步骤数等。

只有数据源明确提供 `current + total` 时，才使用：

```text
ProgressMode.DETERMINATE
```

---

## 4. 推荐技术栈

- Java：21 LTS
- Build：Gradle Kotlin DSL
- UI：JavaFX
- Tray：AWT `SystemTray`
- JSON：Jackson
- Logging：SLF4J + Logback
- Test：JUnit 5
- Packaging：`jpackage`

JavaFX 负责透明悬浮窗、动画和后续桌面宠物表现；AWT 只负责系统托盘。

不要让 UI Toolkit 类型进入 `core` 或 `source-api`。

---

## 5. Gradle 模块结构

建议第一版使用：

```text
taskbeacon/
├── settings.gradle.kts
├── build.gradle.kts
├── source-api/
├── core/
├── source-codex/
├── ui-desktop/
└── app/
```

职责：

```text
source-api
  定义 TaskSource、TaskEventSink、SourceDescriptor 等公共 SPI。

core
  定义 TaskEvent / TaskState / Reducer / TaskRegistry / PrioritySelector。

source-codex
  只负责 Codex 事件获取、解析和归一化。

ui-desktop
  JavaFX 悬浮窗、任务列表、Tray、Renderer。

app
  Composition Root：装配 Core、Sources、UI、配置、生命周期。
```

依赖方向必须保持：

```text
source-api  <- source-codex
source-api  <- core
core        <- ui-desktop
core        <- app
source-*    <- app
ui-desktop  <- app
```

更准确地说，`app` 可以依赖所有模块，但 `core` 和 `ui-desktop` 不能依赖 `source-codex`。

---

## 6. 领域模型

### 6.1 TaskKey

```java
public record TaskKey(
        String sourceId,
        String taskId
) {}
```

`sourceId + taskId` 必须唯一标识一个任务。

示例：

```text
sourceId = "codex"
taskId   = "thread-or-run-id"
```

### 6.2 Lifecycle

```java
public enum Lifecycle {
    DISCOVERED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
```

### 6.3 Activity

```java
public enum Activity {
    IDLE,
    STARTING,
    THINKING,
    EXECUTING_COMMAND,
    EDITING_FILES,
    READING_FILES,
    SEARCHING,
    CALLING_TOOL,
    TESTING,
    BUILDING,
    DEPLOYING,
    WAITING,
    OTHER,
    UNKNOWN
}
```

不要试图让枚举覆盖所有厂商行为。不能归类的事件用 `OTHER`，并通过 `activityLabel` 提供人类可读文本。

### 6.4 Attention

```java
public enum Attention {
    NONE,
    INPUT_REQUIRED,
    APPROVAL_REQUIRED,
    WARNING,
    ERROR
}
```

### 6.5 Progress

```java
public enum ProgressMode {
    NONE,
    INDETERMINATE,
    DETERMINATE
}
```

```java
public record TaskProgress(
        ProgressMode mode,
        Long current,
        Long total,
        String unit
) {
    public static TaskProgress indeterminate() { ... }
}
```

当 `mode == DETERMINATE` 时要求：

```text
current >= 0
total > 0
current <= total
```

否则不得计算百分比。

### 6.6 TaskState

```java
public record TaskState(
        TaskKey key,
        String sourceDisplayName,
        String taskTitle,
        String workspace,
        Lifecycle lifecycle,
        Activity activity,
        String activityLabel,
        Attention attention,
        String message,
        TaskProgress progress,
        Instant startedAt,
        Instant updatedAt,
        Instant finishedAt,
        long eventCount,
        Map<String, Object> attributes
) {}
```

`TaskState` 应视为不可变对象，通过 Reducer 生成新状态。

---

## 7. 统一事件模型

建议采用稳定的 Event Envelope：

```java
public record TaskEvent(
        int schemaVersion,
        UUID eventId,
        TaskKey taskKey,
        EventType type,
        Instant occurredAt,
        String title,
        String message,
        Map<String, Object> data
) {}
```

### 7.1 EventType

```java
public enum EventType {
    TASK_DISCOVERED,
    TASK_STARTED,
    ACTIVITY_CHANGED,
    ATTENTION_REQUIRED,
    ATTENTION_CLEARED,
    PROGRESS_UPDATED,
    TASK_SUCCEEDED,
    TASK_FAILED,
    TASK_CANCELLED,
    TASK_HEARTBEAT,
    SOURCE_DISCONNECTED,
    SOURCE_ERROR
}
```

Source Adapter 负责把厂商事件转换成这些事件。

Core 不保存厂商原始对象。

必要时可以把经过裁剪的原始字段放在：

```text
data["vendorEventType"]
data["vendorItemType"]
```

但这些字段不得参与核心状态机的强依赖。

---

## 8. Reducer

定义：

```java
public interface TaskStateReducer {
    TaskState reduce(TaskState previous, TaskEvent event);
}
```

Reducer 必须是纯业务逻辑，不能访问 UI、网络和文件系统。

关键规则：

```text
TASK_STARTED
  lifecycle = RUNNING

ACTIVITY_CHANGED
  更新 activity / activityLabel
  不改变终态

ATTENTION_REQUIRED
  更新 attention
  lifecycle 通常仍保持 RUNNING

ATTENTION_CLEARED
  attention = NONE

TASK_SUCCEEDED
  lifecycle = SUCCEEDED
  activity = IDLE
  attention = NONE
  finishedAt = event time

TASK_FAILED
  lifecycle = FAILED
  activity = IDLE
  attention = ERROR
  finishedAt = event time

TASK_CANCELLED
  lifecycle = CANCELLED
  activity = IDLE
  finishedAt = event time
```

终态默认不可被普通 `ACTIVITY_CHANGED` 覆盖。如果一个数据源复用同一个 taskId 启动新任务，Adapter 必须生成新的 TaskKey 或显式发出新的任务实例。

---

## 9. Source SPI

### 9.1 TaskSource

```java
public interface TaskSource extends AutoCloseable {

    SourceDescriptor descriptor();

    void start(TaskEventSink sink) throws Exception;

    SourceHealth health();

    @Override
    void close() throws Exception;
}
```

### 9.2 TaskEventSink

```java
@FunctionalInterface
public interface TaskEventSink {
    void publish(TaskEvent event);
}
```

### 9.3 SourceDescriptor

```java
public record SourceDescriptor(
        String id,
        String displayName,
        String version,
        Set<SourceCapability> capabilities
) {}
```

### 9.4 SourceCapability

```java
public enum SourceCapability {
    REALTIME_EVENTS,
    DETERMINATE_PROGRESS,
    MULTI_TASK,
    ATTENTION_EVENTS,
    COMPLETION_EVENTS,
    READ_ONLY,
    BIDIRECTIONAL
}
```

UI 可以根据 capability 调整表现，但不能针对具体 `sourceId` 写业务分支。

---

## 10. Codex 数据源设计

Codex Source 内部应该允许多种 ingestion strategy，共享同一套归一化逻辑：

```text
CodexTaskSource
    │
    ├── ExecJsonIngestion
    ├── SessionLogIngestion      (后续增强)
    ├── AppServerIngestion       (后续增强)
    └── NotifyHookIngestion      (后续增强)
             │
             ▼
       CodexEventNormalizer
             │
             ▼
          TaskEvent
```

### 10.1 MVP：`codex exec --json`

当前 Codex `exec --json` 的 stdout 设计为 JSONL：每行一个完整 JSON 事件。其他非 JSON 输出应走 stderr。

MVP 应优先实现这一模式，因为：

- 接口天然机器可读。
- 不需要解析终端 ANSI/TUI。
- 易于制作 fixture 和回归测试。
- Adapter 可以完全控制进程生命周期。

建议提供：

```text
taskbeacon codex exec "<prompt>"
```

或者内部 API：

```java
CodexRunHandle startExec(CodexExecRequest request)
```

底层通过 `ProcessBuilder` 启动：

```text
codex exec --json ...
```

stdout：逐行读取 JSONL。  
stderr：写入 TaskBeacon 自身日志，不作为 JSON 解析。

### 10.2 JSON 解析必须宽容

不要把整个 Adapter 建立在“当前版本所有 JSON 字段永远不变”的假设上。

解析策略：

1. 首先读取顶层 `type`。
2. 已知类型映射到语义事件。
3. 未知类型记录 debug 日志并忽略或降级成 `OTHER`。
4. 缺失非关键字段时不得让整个 Source 崩溃。
5. 单行 JSON 解析失败只影响该事件，除非连续错误达到阈值。

可以保留：

```java
JsonNode raw
```

仅在 Codex Adapter 内部使用，不可泄露到 Core API。

### 10.3 推荐 Codex 映射

映射应根据实际 `codex --version` 与 JSON fixture 校验，不要仅依赖本文示例字符串。

语义上至少实现：

```text
turn/thread/run start
    -> TASK_STARTED
    -> Activity.THINKING 或 STARTING

reasoning/agent processing
    -> ACTIVITY_CHANGED(THINKING)

command execution started
    -> ACTIVITY_CHANGED(EXECUTING_COMMAND)

file change / patch
    -> ACTIVITY_CHANGED(EDITING_FILES)

tool call
    -> ACTIVITY_CHANGED(CALLING_TOOL)

turn completed successfully
    -> TASK_SUCCEEDED

turn failed / thread error
    -> TASK_FAILED
```

如果可以识别测试命令：

```text
mvn test
mvnw test
gradle test
gradlew test
npm test
pnpm test
pytest
cargo test
go test
```

允许把 `EXECUTING_COMMAND` 细化成 `TESTING`，但这只能是展示层增强，不得影响任务正确性。

### 10.4 Codex App Server：Phase 2

Codex App Server 使用 JSON-RPC 风格协议，可提供 thread / turn / item 级实时通知，适合更完整的交互式客户端。

但实现必须假设协议会随 Codex 版本发展。

Phase 2 要求：

- 启动时探测 `codex --version`。
- 尽可能使用当前 Codex 自带的 schema generation 命令生成与本机版本匹配的协议 schema。
- Adapter 内部做 protocol compatibility layer。
- Core 不感知协议版本。
- experimental 字段只能在 capability negotiation 后使用。

不要把 App Server 的 JSON-RPC DTO 直接作为项目领域模型。

### 10.5 普通 Codex TUI 附加观察：Phase 1.1 / 实验功能

普通 Codex CLI/TUI 会在 `$CODEX_HOME/sessions`（默认通常位于用户 `.codex` 目录）保存 rollout JSONL session 文件。

可以实现只读 `SessionLogIngestion`：

- 递归发现新增/活跃 rollout 文件。
- tail 追加内容。
- 根据 `session_meta` / cwd / thread 信息建立任务。
- 尝试从追加事件推断 activity。
- File Watch 失效时使用低频 rescan 回退。

此模式必须标记为：

```text
BEST_EFFORT / EXPERIMENTAL
```

原因是 rollout 属于 Codex 内部 session representation，不应作为 TaskBeacon Core 的稳定契约。

该模式的失败不得影响 `ExecJsonIngestion`。

### 10.6 Codex notify hook：辅助能力

Codex 当前支持配置一个外部 notifier，在一个 turn 完成后调用外部程序并附带 JSON 参数。

可以在后续版本用它增强普通 TUI 的“完成”信号，但不能把它当作实时活动流，因为它本质上是 turn-complete hook。

---

## 11. Core：TaskRegistry

```java
public interface TaskRegistry {
    void accept(TaskEvent event);
    Optional<TaskState> get(TaskKey key);
    List<TaskState> activeTasks();
    List<TaskState> recentTasks();
    Subscription subscribe(TaskStateListener listener);
}
```

实现建议：

```text
ConcurrentHashMap<TaskKey, TaskState>
```

事件处理顺序：

```text
Source Thread
    ↓
Event Dispatcher
    ↓
Single logical reducer queue
    ↓
TaskRegistry state update
    ↓
listeners
```

为了避免并发 source 对同一任务产生竞态，推荐 Core 内部使用单消费者事件队列，或者保证每个 TaskKey 串行归约。

UI 更新必须切换到 JavaFX Application Thread。

---

## 12. 主展示任务选择算法

多任务同时存在时，悬浮宠物只能重点显示一个任务。

不要简单按“最近更新时间”选择。

推荐优先级：

```text
1. ATTENTION = ERROR
2. ATTENTION = APPROVAL_REQUIRED
3. ATTENTION = INPUT_REQUIRED
4. ATTENTION = WARNING
5. RUNNING 且用户手动 pin
6. RUNNING 且最近发生事件
7. 刚完成的 FAILED
8. 刚完成的 SUCCEEDED
9. 其他
```

可以实现：

```java
public interface PrimaryTaskSelector {
    Optional<TaskState> select(Collection<TaskState> tasks, SelectionContext context);
}
```

终态任务只在主视图停留一段可配置时间，例如 8 秒，然后让位给仍在运行的任务。

---

## 13. Desktop UI

### 13.1 MVP 视图

悬浮窗建议默认约：

```text
宽：220px 左右
高：80~110px
```

要求：

- `alwaysOnTop = true`
- 无系统标题栏
- 支持拖动
- 记住上次屏幕位置
- 单击打开任务列表
- 可最小化到 Tray
- 可设置“鼠标穿透”作为未来功能，不要求 MVP

### 13.2 展示内容

主卡至少包含：

```text
[状态图形]  Codex · project-name
            Running tests
            01:12
```

如果 attention 非 NONE：

```text
[!] Codex · project-name
    Approval required
```

### 13.3 Pet Renderer

不要把“宠物”写死到 Core。

定义 UI 侧接口：

```java
public interface StatusRenderer {
    Node render(TaskState state);
    void update(TaskState state);
}
```

MVP 可以先实现：

```text
CompactCardRenderer
```

之后再加：

```text
PetRenderer
MinimalDotRenderer
FloatingBarRenderer
```

### 13.4 动画

动画只能作为状态反馈增强：

```text
THINKING          -> 轻微呼吸/思考
EXECUTING_COMMAND -> 工作动画
EDITING_FILES     -> 写字动画
ATTENTION         -> 明显但不过度闪烁
SUCCEEDED         -> 一次短完成动画
FAILED            -> 一次错误提示
```

必须支持系统 reduced-motion 或 TaskBeacon 自身的“关闭动画”设置。

---

## 14. Tray

Tray 菜单至少包含：

```text
TaskBeacon
────────────
Show / Hide
Active tasks: N
────────────
Settings
Quit
```

如果平台不支持 AWT `SystemTray`，应用仍应能通过悬浮窗运行，不允许启动失败。

---

## 15. 配置

定义统一 `AppPaths`，不要在业务代码到处拼接 `~/.taskbeacon`。

建议配置：

```json
{
  "ui": {
    "alwaysOnTop": true,
    "animations": true,
    "completedDisplaySeconds": 8
  },
  "sources": {
    "codex": {
      "enabled": true,
      "executable": "codex",
      "ingestionMode": "EXEC_JSON"
    }
  },
  "privacy": {
    "showFullCommands": false,
    "persistEventPayloads": false
  }
}
```

配置读取失败时：

- 记录错误。
- 使用安全默认值启动。
- 不因单个未知字段拒绝整个配置。

---

## 16. 隐私与安全

TaskBeacon 默认应是 local-first。

MVP 不需要任何 TaskBeacon 自己的网络服务。

默认不得持久化：

- 完整用户 prompt。
- 完整 assistant 输出。
- 文件内容。
- API Key / Token。
- 完整敏感命令参数。

命令展示默认做裁剪，例如：

```text
./mvnw test
```

而不是把可能含密码、token、URL query 参数的完整命令直接长期保存。

日志中对环境变量、authorization、token、password、secret 等字段做脱敏。

---

## 17. 错误与健康状态

Source 自身的异常与 Task 失败是不同概念。

例如：

```text
Codex task FAILED
```

表示任务本身失败。

而：

```text
Codex source DISCONNECTED
```

表示 TaskBeacon 无法继续获取状态。

定义：

```java
public record SourceHealth(
        HealthLevel level,
        String message,
        Instant updatedAt
) {}
```

```java
public enum HealthLevel {
    HEALTHY,
    DEGRADED,
    DISCONNECTED,
    ERROR
}
```

Source 崩溃不能带崩整个桌面应用。

---

## 18. 日志

至少拆分逻辑 logger：

```text
app
core
source.codex
ui
```

默认 INFO。

Codex 原始 JSONL 只能在 DEBUG 且通过 privacy 设置允许时记录。

建议滚动日志：

```text
10 MB / file
最多 5 个文件
```

---

## 19. 测试策略

### 19.1 Core 单元测试

必须覆盖：

- 生命周期转换。
- attention 与 lifecycle 同时存在。
- terminal state 不被普通 activity 覆盖。
- progress 参数校验。
- 多任务主任务选择优先级。

### 19.2 Codex Adapter fixture 测试

在：

```text
source-codex/src/test/resources/fixtures/
```

保存经过脱敏的真实/模拟 JSONL：

```text
simple-success.jsonl
command-and-file-edit.jsonl
failure.jsonl
unknown-event.jsonl
malformed-line.jsonl
```

每次 Codex 升级后，可以补充新的 fixture，而不用修改 Core 测试。

### 19.3 Process 集成测试

不要在普通 CI 中依赖真实 Codex 登录。

实现一个 fake executable，按时间输出 JSONL，用于验证：

```text
ProcessBuilder
stdout JSONL
stderr
exit code
cancellation
parser resilience
```

### 19.4 UI 测试

MVP 至少把 UI ViewModel 与 JavaFX Node 分开，确保状态文案、elapsed time、attention badge 等可以脱离真实桌面做测试。

---

## 20. 外部插件化路线

MVP 先把 `source-api` 稳定下来，Codex source 可以编译进主程序。

Phase 2 再实现：

```text
plugins/
├── taskbeacon-source-jenkins.jar
├── taskbeacon-source-github.jar
└── taskbeacon-source-company-agent.jar
```

插件可通过 Java `ServiceLoader` + 独立 ClassLoader 加载。

建议定义 factory：

```java
public interface TaskSourceFactory {
    SourceDescriptor descriptor();
    TaskSource create(SourceContext context, Map<String, Object> config);
}
```

动态插件加载不是 MVP 验收项，但第一版禁止把：

```java
if (sourceId.equals("codex"))
```

写进 Core 或 UI 的业务逻辑。

---

## 21. 第二个 Source 的验证意义

在 MVP 完成后，建议立即实现一个极简单的第二数据源，例如：

```text
DemoTaskSource
```

它每秒模拟：

```text
STARTED
THINKING
EXECUTING_COMMAND
ATTENTION_REQUIRED
SUCCEEDED
```

这个 Source 的价值不是功能，而是验证“UI/Core 没有偷偷依赖 Codex”。

后续真正的第二个外部 Source 可以再选择 Claude Code、Jenkins 或 GitHub Actions。

---

## 22. 推荐开发顺序

### Phase A — Core 可运行

完成：

```text
source-api
core domain model
reducer
registry
selector
DemoTaskSource
```

先用 console 输出验证状态流。

### Phase B — Desktop Shell

完成：

```text
JavaFX floating card
Tray
Task list
primary task selection
```

用 DemoTaskSource 驱动 UI。

### Phase C — Codex `exec --json`

完成：

```text
ProcessBuilder
JSONL parser
Codex normalizer
fixtures
source health
```

### Phase D — Packaging

完成：

```text
jpackage
macOS package
Windows package
Linux package
```

### Phase E — Codex CLI 普通 TUI 增强

优先评估：

```text
SessionLogIngestion
notify hook
```

### Phase F — App Server

在协议稳定性、维护成本和收益合理时实现更完整的 App Server Adapter。

---

## 23. MVP 验收标准

以下全部满足才算 MVP 完成。

### 架构

- `core` 无 Codex import。
- `ui-desktop` 无 Codex import。
- Codex 代码全部位于 `source-codex`。
- 添加 `DemoTaskSource` 不需要修改 Core 业务代码。

### 功能

- TaskBeacon 启动后存在独立悬浮窗。
- Codex 任务运行时，离开 Terminal 后悬浮窗仍持续工作。
- 可以至少识别：开始、思考/运行、命令执行、文件修改、成功、失败。
- 多任务同时存在时可以展示活动任务数，并自动选出主任务。
- attention 事件模型已实现，即使第一版 Codex ingestion 暂时无法产生所有 attention 类型。
- 完成后展示完成状态，并在配置时间后自动让位给活跃任务。
- Source 出错不会导致应用退出。

### 进度真实性

- 没有可靠 total 时，绝不显示伪百分比。
- UI 可以显示 elapsed time。

### 测试

- Core reducer 单测通过。
- Codex fixture 测试通过。
- malformed / unknown JSON event 不会导致整个 Source 崩溃。

### 隐私

- 默认日志不包含完整 prompt 和 assistant 输出。
- 默认不持久化原始 Codex JSON payload。

---

## 24. 建议的第一版 UI 文案

```text
DISCOVERED / STARTING
Starting…

THINKING
Thinking…

EXECUTING_COMMAND
Running command

EDITING_FILES
Editing files

READING_FILES
Reading files

TESTING
Running tests

CALLING_TOOL
Using tool

APPROVAL_REQUIRED
Approval required

INPUT_REQUIRED
Input required

SUCCEEDED
Completed

FAILED
Failed

CANCELLED
Cancelled

SOURCE_DISCONNECTED
Source disconnected
```

UI 文案必须来源于 UI 层的 formatter / i18n resource，不要散落在 Reducer 中。

---

## 25. 可选增强

MVP 之后可以考虑：

- 桌面宠物动画包。
- Renderer 主题系统。
- 用户 pin 某个任务。
- 每个 workspace 独立图标。
- 声音提示。
- Native notifications。
- Global hotkey。
- 多显示器位置记忆。
- 任务历史。
- 统计平均运行时间。
- 手机/网页 Companion。
- WebSocket / SSE / webhook source。
- Jenkins / GitHub Actions / Claude Code adapter。
- 第三方 Source SDK。

这些都不能反向污染第一版 Core API。

---

## 26. 关键工程判断

### 判断 1：TaskBeacon 不是 Codex Pet

Codex 是第一个 Provider，不是产品边界。

正确关系：

```text
TaskBeacon
  └── source-codex
```

不是：

```text
CodexPet
  └── later add Jenkins hacks
```

### 判断 2：TaskState 是 TaskBeacon 的领域对象

Vendor event 只能进入 Adapter，不能穿透 Core。

### 判断 3：事件比轮询 UI 更重要

UI 只订阅 Core 的状态变化，不主动去问 Codex“你现在在干什么”。

### 判断 4：兼容性问题隔离在 Adapter

Codex 升级导致 JSON 字段变化时，只修 `source-codex`。

### 判断 5：第一版要能够被替换 UI

以后宠物、状态条、菜单栏、Web Dashboard 都应可以复用同一个 Core。

---

## 27. Codex 当前技术事实与兼容性备注（2026-09-10）

实现前应在本机再次通过 `codex --version` 和实际输出确认。

目前可依赖的工程方向包括：

1. `codex exec --json` 的 stdout 是 JSONL 机器可读事件流，每行一个事件；这是 MVP 推荐入口。
2. Codex 代码中存在 thread、turn、item、command execution、file change、turn completed/failed 等事件类型。
3. `codex app-server` 提供 JSON-RPC 风格的 richer interface，并支持根据本机 Codex 版本生成 TypeScript/JSON Schema；因此适合做版本兼容型高级 Adapter。
4. Codex TUI / exec 的内部实现正在向 app-server 语义收敛，但外部 app-server 文档与接口仍可能变化，不能让 Core 与其 DTO 耦合。
5. Codex CLI session rollout 文件位于 `$CODEX_HOME/sessions` 路径体系，可作为普通 TUI session 的只读观察来源，但应视为 best-effort internal representation。
6. Codex `notify` 是 turn-complete 外部通知机制，适合补足完成信号，不适合作为完整实时 activity stream。

---

## 28. 最终交付物

Codex 实现本设计后，仓库至少应包含：

```text
README.md
ARCHITECTURE.md
settings.gradle.kts
build.gradle.kts
source-api/
core/
source-codex/
ui-desktop/
app/
```

README 至少说明：

```text
项目是什么
如何构建
如何运行 Demo Source
如何运行 Codex Source
配置文件位置
隐私默认行为
已知限制
```

`ARCHITECTURE.md` 应从本文提炼实际实现后的模块和接口；若实现与本文发生合理偏差，应记录 ADR（Architecture Decision Record）说明原因，而不是静默改变边界。

---

# Implementation Definition of Done

当以下场景可以演示时，第一版可以结束：

```text
1. 启动 TaskBeacon。
2. 悬浮窗出现，显示 Idle。
3. 启动一个 Demo 或 Codex task。
4. 悬浮窗显示 Running/Thinking。
5. task 执行命令时显示 Running command。
6. task 修改文件时显示 Editing files。
7. 用户切换到其他应用，TaskBeacon 仍然可见。
8. task 完成，显示 Completed + elapsed time。
9. 同时启动第二个 task，TaskBeacon 能管理两个 TaskState。
10. 模拟 Approval Required，主展示自动切换到该任务。
11. 关闭 Codex Source，TaskBeacon 主程序仍然运行。
12. 用 Demo Source 替换 Codex Source，UI 无需修改。
```

如果第 12 条做不到，说明架构仍然与 Codex 耦合，应在继续增加功能前先修正。
