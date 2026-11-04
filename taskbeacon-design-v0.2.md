# TaskBeacon 通用任务状态监视器 — 工程设计文档

> 工作名：TaskBeacon  
> 文档版本：v0.2  
> 日期：2026-09-10  
> 目标读者：负责实现该项目的 Codex / Java 开发者  
> 状态：Implementation-ready / MVP 架构基线

---

## 0. 文档目的与执行要求

TaskBeacon 是一个**独立于被监控工具窗口的桌面任务状态监视器**。用户可以在 Terminal、IDE、浏览器或其他应用之间切换，TaskBeacon 仍持续显示后台任务当前状态，并在任务需要人工介入、疑似停滞、完成或失败时提供可见反馈。

第一版以 **Codex CLI** 作为第一个真实数据源，但 TaskBeacon 的 Core、UI、状态模型和 Renderer **不得与 Codex 强耦合**。后续应能够在不修改 Core 领域模型和 Desktop UI 业务逻辑的前提下增加 Claude Code、Gemini CLI、Jenkins、GitHub Actions、Maven/Gradle、自研 Agent、远程 batch job 等数据源。

实现时按以下优先级处理冲突：

1. 本文的“架构不变量”和“验收标准”。
2. 观测系统不得影响被观测任务执行。
3. Core / Source / Renderer 解耦。
4. 状态真实性与可解释性。
5. 可运行、可测试、可维护。
6. UI 美观、动画、宠物趣味性。

**不要为了未来扩展一次性实现所有能力。MVP 必须简单，但边界必须正确。**

---

## 1. v0.2 相比 v0.1 的关键变化

v0.2 是基于对现有开源实现和当前 Codex 能力的代码级调研后做出的架构修订，主要变化如下：

1. Codex Source 从单一 ingestion 改成 **Composite Source / 多信号融合**。
2. Codex MVP 的主要目标从“包装 `codex exec --json`”调整为“**能够观察已经在其他 Terminal 运行的交互式 Codex**”。
3. `TaskState` 从三维状态扩展为四维：`Lifecycle + Activity + Attention + Condition`。
4. `TaskEvent` 增加观测来源与证据质量：`ObservationMode + EvidenceQuality`。
5. 增加 `lastObservedAt` 与 `lastMeaningfulProgressAt`，明确区分“有事件”与“真的有进展”。
6. 增加父子任务模型，支持 Session / Run / Subtask / Background Job。
7. Session JSONL 读取明确采用 append-only cursor 模型，禁止每次全文件重读。
8. Hook 安装与执行必须 fail-open，TaskBeacon 故障不得阻塞 Codex。
9. 增加 `CodexRawSignal` 与 `CodexSignalFusion`，不同 Provider 不得直接操纵 Core 状态。
10. Pet 被进一步明确为 Renderer；第一版只做 Minimal / Tray。
11. 为后续第三方系统增加 Local Event API 方向，但不作为 MVP 必须项。
12. 增加状态可信度、过期状态、Stall detection、信号去重和恢复策略。

---

## 2. 产品目标

TaskBeacon 要解决的是：当 AI Agent、CLI、CI/CD、构建系统或其他长任务在后台执行时，用户不应该频繁切回原窗口确认：

- 它现在是否还在工作？
- 当前大致在做什么？
- 是否真的有进展，还是只是进程仍然存在？
- 是否正在等待我输入或批准？
- 是否疑似卡住？
- 是否已经完成、失败、取消或达到限制？

TaskBeacon 应提供：

- 与任务来源无关的统一任务模型。
- 与任务来源无关的统一状态语义。
- 一个始终可见但不打扰用户的桌面状态入口。
- 多任务同时运行时的聚合、排序和主任务选择。
- 清晰区分生命周期、当前活动、人工介入和运行条件。
- 对事件可信度和观测方式的显式建模。
- 插件化 / Adapter 化的数据源接入机制。
- 对数据源能力差异和事件缺失的容错。
- local-first、read-only-first 的安全策略。

### 2.1 典型使用场景

用户在 Terminal A 中直接执行：

```text
codex
```

让 Codex 修改项目。随后用户切到 IDE。

TaskBeacon 悬浮状态条仍显示：

```text
Codex · backend-service
Thinking…
00:48
```

随后：

```text
Codex · backend-service
Running tests
01:12
```

如果需要用户批准：

```text
Codex · backend-service
Approval required
```

如果长时间没有有效进展，但没有明确失败证据：

```text
Codex · backend-service
Possibly stalled
No meaningful progress for 5m 12s
```

任务完成后：

```text
Codex · backend-service
Completed ✓
2m 14s
```

### 2.2 产品定位

TaskBeacon 不是“Codex 桌宠”。

TaskBeacon 的核心定位是：

> **Universal Task Activity Infrastructure + Desktop Status Renderer**

宠物只是未来的一种 Renderer。

---

## 3. MVP 范围

### 3.1 MVP 必须实现

1. Java 21 桌面应用。
2. Gradle Kotlin DSL 多模块工程。
3. 通用 `source-api`，Core 不引用任何 Codex 专用类型。
4. 统一事件模型、证据模型、状态模型和 Reducer。
5. 多任务 Registry。
6. 父子任务基础模型。
7. `Condition.STALLED` 的基础检测能力。
8. `DemoTaskSource`，用于验证 UI/Core 与 Codex 解耦。
9. `source-codex` Composite Source 基础框架。
10. Codex SessionLog Provider：能观察已经运行的交互式 Codex。
11. Codex Hook Provider：能力存在且安全时增强精确信号；不可作为唯一依赖。
12. Codex `exec --json` Provider：作为 TaskBeacon 主动启动 Codex 时的高质量事件通道。
13. 独立 always-on-top 小悬浮窗。
14. 系统托盘入口。
15. 多任务状态管理和主任务自动选择。
16. 本地配置和日志。
17. Core 单测、Signal Fusion 单测、JSONL fixture 测试。
18. Windows / macOS / Linux 架构兼容；安装包可按 OS 分别构建。

### 3.2 MVP 明确不做

- 不实现云端账号系统。
- 不实现 TaskBeacon 自己的远程控制服务。
- 不在第一版从 TaskBeacon 中直接批准 Agent 操作。
- 不实现聊天历史浏览器。
- 不实现虚假的 LLM 完成百分比。
- 不要求第三方 JAR 热加载。
- 不要求 App Server 作为 MVP 必需能力。
- 不要求桌面宠物动画。
- 不要求 Live2D。
- 不持久化完整 Prompt、完整模型回复或代码内容。
- 不以 TaskBeacon 是否正常运行为 Codex 继续运行的前置条件。

---

## 4. 架构不变量

以下规则属于项目不变量，除非后续设计文档明确升级，否则实现不得违反。

### 4.1 Observability must never become a dependency of task execution

TaskBeacon 是观测工具，不是任务执行链路的一部分。

必须满足：

```text
TaskBeacon crashed
      ↓
Codex / Jenkins / Maven / Agent continues normally
```

任何 Hook、wrapper、socket client、日志 watcher 出错时必须优先 fail-open。

### 4.2 Source 与 UI 完全解耦

UI 不应该知道：

```text
turn.started
PermissionRequest
Codex rollout event
Jenkins build status
Claude hook event
```

所有外部系统必须经过 Adapter：

```text
External System
      ↓
Source-specific observation
      ↓
TaskEvent
      ↓
TaskBeacon Core
      ↓
TaskState
      ↓
Renderer
```

### 4.3 Vendor DTO 不得进入 Core

禁止：

```java
record TaskState(CodexTurn turn, ...)
```

允许：

```java
record TaskState(Activity activity, ...)
```

### 4.4 Pet 不是 Core 概念

Core、source-api、source-codex 中禁止出现以下领域含义：

```text
pet
sprite
animation row
jump
wave
```

这些只能属于 Renderer / UI。

### 4.5 状态必须可解释

TaskBeacon 不应把弱信号包装成确定事实。

例如：

```text
process alive + JSONL mtime changed
```

不足以直接断言：

```text
WORKING
```

必须保留证据质量，必要时显示：

```text
Possibly stalled
Status inferred from local activity
```

---

## 5. 推荐技术栈

第一版：

- Java：21 LTS
- Build：Gradle Kotlin DSL
- UI：JavaFX
- Tray：AWT `SystemTray`
- JSON：Jackson
- Logging：SLF4J + Logback
- Test：JUnit 5
- Assertions：AssertJ（可选）
- Packaging：`jpackage`

JavaFX 负责：

- 透明悬浮窗
- 卡片 UI
- 动画（未来）
- Pet Renderer（未来）

AWT 只负责 System Tray。

任何 JavaFX/AWT 类型不得进入 `core`、`source-api` 或 `source-codex` 领域 API。

---

## 6. Gradle 模块结构

建议：

```text
taskbeacon/
├── settings.gradle.kts
├── build.gradle.kts
├── source-api/
├── core/
├── source-demo/
├── source-codex/
├── ui-desktop/
└── app/
```

职责：

### `source-api`

定义：

- `TaskSource`
- `TaskEventSink`
- `SourceDescriptor`
- `SourceCapability`
- `SourceHealth`
- 与 Source 生命周期有关的通用 SPI

### `core`

定义：

- `TaskKey`
- `TaskRelation`
- `TaskEvent`
- `TaskState`
- `TaskStateReducer`
- `TaskRegistry`
- `StallDetector`
- `PrimaryTaskSelector`
- 状态可信度和时间语义

### `source-demo`

用于：

- 模拟多任务
- 模拟 waiting / approval / stall / success / failure
- 驱动 UI 开发
- 验证 Core/UI 不依赖 Codex

### `source-codex`

只负责：

- Codex 环境探测
- Hook Provider
- SessionLog Provider
- ExecJson Provider
- 未来 AppServer Provider
- Raw Signal
- Signal Fusion
- Session/Turn/Subagent hierarchy inference
- Codex → TaskEvent

### `ui-desktop`

负责：

- JavaFX 悬浮窗
- Mini Task Panel
- Tray
- ViewModel
- Formatter / i18n
- Renderer

### `app`

Composition Root：

- 加载配置
- 创建 Source
- 创建 Core
- 绑定 UI
- 管理应用生命周期

依赖方向：

```text
source-api  <- source-demo
source-api  <- source-codex
source-api  <- core
core        <- ui-desktop
core        <- app
source-*    <- app
ui-desktop  <- app
```

`app` 可以依赖全部模块。

禁止：

```text
core -> source-codex
ui-desktop -> source-codex
source-codex -> ui-desktop
```

---

## 7. 统一领域模型

### 7.1 TaskKey

```java
public record TaskKey(
        String sourceId,
        String taskId
) {}
```

`sourceId + taskId` 唯一标识一个 TaskBeacon task instance。

不要假设 `taskId` 永远等于外部系统 session ID。

Adapter 可以根据外部系统语义构造：

```text
codex:session:<session-id>
codex:turn:<turn-id>
codex:subagent:<agent-id>
```

### 7.2 TaskScope

```java
public enum TaskScope {
    SESSION,
    RUN,
    SUBTASK,
    BACKGROUND_JOB,
    EXTERNAL_JOB,
    UNKNOWN
}
```

语义：

- `SESSION`：长期存在的会话容器。
- `RUN`：一次用户请求 / turn / execution。
- `SUBTASK`：subagent / child task。
- `BACKGROUND_JOB`：由主任务触发但独立继续的后台任务。
- `EXTERNAL_JOB`：CI/CD、build、deployment 等自然 job。

### 7.3 TaskRelation

```java
public record TaskRelation(
        TaskScope scope,
        TaskKey rootTaskKey,
        TaskKey parentTaskKey
) {}
```

`parentTaskKey` 可以为 `null`。

规则：

- root task 的 `rootTaskKey == key`。
- 子任务完成不得自动把 root task 标记完成。
- root task 的状态可以聚合 children，但不得简单复制最后一个 child 状态。

### 7.4 Lifecycle

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

生命周期只描述“任务总体是否仍然活跃”。

**STALLED 不是 Lifecycle。**

### 7.5 Activity

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

不要试图覆盖所有厂商行为。

不能归类的事件：

```text
Activity.OTHER
```

并使用：

```text
activityLabel
```

提供可读文本。

### 7.6 Attention

```java
public enum Attention {
    NONE,
    INPUT_REQUIRED,
    APPROVAL_REQUIRED,
    WARNING,
    ERROR
}
```

Attention 表示是否需要人类注意。

### 7.7 Condition

```java
public enum Condition {
    NORMAL,
    QUIET,
    STALLED,
    RATE_LIMITED,
    DEGRADED,
    UNKNOWN
}
```

Condition 描述任务运行条件，而不是生命周期。

例：

```text
Lifecycle = RUNNING
Activity  = EXECUTING_COMMAND
Attention = NONE
Condition = STALLED
```

可以同时成立。

### 7.8 Progress

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
) {}
```

当：

```text
mode == DETERMINATE
```

必须：

```text
current >= 0
total > 0
current <= total
```

默认 Agent 任务：

```text
INDETERMINATE
```

禁止根据 token 数、tool call 数、elapsed time 猜完成百分比。

---

## 8. 证据与观测模型

### 8.1 ObservationMode

```java
public enum ObservationMode {
    NATIVE_EVENT,
    MACHINE_STREAM,
    SESSION_LOG,
    PROCESS_PROBE,
    FILE_WATCH,
    POLLING,
    WEBHOOK,
    EXTERNAL,
    UNKNOWN
}
```

示例：

```text
Codex PermissionRequest hook
→ NATIVE_EVENT

codex exec --json
→ MACHINE_STREAM

rollout JSONL
→ SESSION_LOG

process alive / cpu activity
→ PROCESS_PROBE
```

### 8.2 EvidenceQuality

```java
public enum EvidenceQuality {
    AUTHORITATIVE,
    STRONG,
    HEURISTIC,
    UNKNOWN
}
```

建议含义：

#### AUTHORITATIVE

外部系统明确告诉 TaskBeacon 某状态。

例：

```text
PermissionRequest
Turn completed
Jenkins SUCCESS
```

#### STRONG

高可信行为信号，但不是最终领域事实。

例：

```text
command execution begin
file patch applied
assistant/tool event
```

#### HEURISTIC

通过间接迹象推断。

例：

```text
process alive
CPU active
session file recently modified
```

### 8.3 Observation freshness

必须区分：

```text
lastObservedAt
```

与：

```text
lastMeaningfulProgressAt
```

`lastObservedAt`：任何有效观测事件到达。

`lastMeaningfulProgressAt`：有证据表明任务真正推进。

例如以下事件通常可刷新 meaningful progress：

```text
reasoning step
new assistant work item
command started
command output/progress
file edit
search/tool call
child task started/completed
explicit lifecycle transition
```

而以下事件默认不能单独刷新 meaningful progress：

```text
process still alive
same token count
mtime touch without semantic event
heartbeat
重复状态事件
```

---

## 9. TaskState

推荐：

```java
public record TaskState(
        TaskKey key,
        TaskRelation relation,
        String sourceDisplayName,
        String taskTitle,
        String workspace,

        Lifecycle lifecycle,
        Activity activity,
        String activityLabel,
        Attention attention,
        Condition condition,

        String message,
        TaskProgress progress,

        Instant discoveredAt,
        Instant startedAt,
        Instant lifecycleSince,
        Instant activitySince,
        Instant attentionSince,
        Instant conditionSince,
        Instant lastObservedAt,
        Instant lastMeaningfulProgressAt,
        Instant updatedAt,
        Instant finishedAt,

        EvidenceQuality currentEvidence,
        long eventCount,
        Map<String, Object> attributes
) {}
```

`TaskState` 是不可变对象。

Core 通过 Reducer 生成新 State。

### 9.1 时间要求

业务逻辑禁止直接调用：

```java
Instant.now()
```

推荐：

```java
Clock
```

由 `app` 注入。

测试使用：

```java
Clock.fixed(...)
```

Stall detection、completed display TTL、stale session 清理必须可确定性测试。

---

## 10. 统一事件模型

### 10.1 TaskEvent

```java
public record TaskEvent(
        int schemaVersion,
        UUID eventId,
        TaskKey taskKey,
        TaskRelation relation,
        EventType type,
        Instant occurredAt,

        ObservationMode observationMode,
        EvidenceQuality evidenceQuality,
        ProgressImpact progressImpact,

        String title,
        String message,
        Map<String, Object> data
) {}
```

### 10.2 ProgressImpact

```java
public enum ProgressImpact {
    MEANINGFUL,
    OBSERVATION_ONLY,
    UNKNOWN
}
```

Source Adapter 应尽量标记某事件是否代表“有效进展”。

Core 可以根据 evidence + event type 做保守降级，但不应让 UI 自己推断。

### 10.3 EventType

```java
public enum EventType {
    TASK_DISCOVERED,
    TASK_STARTED,

    ACTIVITY_CHANGED,
    ATTENTION_REQUIRED,
    ATTENTION_CLEARED,
    CONDITION_CHANGED,
    PROGRESS_UPDATED,

    TASK_SUCCEEDED,
    TASK_FAILED,
    TASK_CANCELLED,

    TASK_HEARTBEAT,
    SOURCE_DISCONNECTED,
    SOURCE_ERROR
}
```

Core 不保存厂商原始 DTO。

允许在 `data` 中保留经过裁剪的调试信息：

```text
data["vendorEventType"]
data["vendorItemType"]
data["provider"]
```

但这些字段不得成为 Core 的业务分支条件。

---

## 11. Reducer

接口：

```java
public interface TaskStateReducer {
    TaskState reduce(TaskState previous, TaskEvent event);
}
```

Reducer 必须：

- 无 UI 依赖
- 无网络依赖
- 无文件系统依赖
- 无 Codex 类型
- 可确定性测试

### 11.1 基础规则

```text
TASK_STARTED
  lifecycle = RUNNING
  lifecycleSince = occurredAt

ACTIVITY_CHANGED
  更新 activity / activityLabel
  更新 activitySince
  不改变 terminal lifecycle

ATTENTION_REQUIRED
  更新 attention
  更新 attentionSince
  lifecycle 通常仍保持 RUNNING

ATTENTION_CLEARED
  attention = NONE

CONDITION_CHANGED
  更新 condition
  不隐式改变 lifecycle

TASK_SUCCEEDED
  lifecycle = SUCCEEDED
  activity = IDLE
  attention = NONE
  condition = NORMAL
  finishedAt = occurredAt

TASK_FAILED
  lifecycle = FAILED
  activity = IDLE
  attention = ERROR
  finishedAt = occurredAt

TASK_CANCELLED
  lifecycle = CANCELLED
  activity = IDLE
  finishedAt = occurredAt
```

### 11.2 Meaningful progress

当：

```text
progressImpact == MEANINGFUL
```

更新：

```text
lastMeaningfulProgressAt
```

所有合法事件更新：

```text
lastObservedAt
```

### 11.3 Terminal state 保护

终态默认不可被普通：

```text
ACTIVITY_CHANGED
TASK_HEARTBEAT
```

覆盖。

如果 Source 外部系统复用 task ID，Adapter 必须创建新的 TaskKey 或新 task instance。

---

## 12. Stall Detector

### 12.1 目标

识别：

```text
“任务仍被认为运行中，但已经较长时间没有有效进展”
```

而不是粗暴判断：

```text
“文件没变化 = 卡住”
```

### 12.2 接口

```java
public interface StallDetector {
    Optional<TaskEvent> evaluate(TaskState state, Instant now);
}
```

建议由 Core 的 scheduler 周期性调用。

### 12.3 默认策略

只对：

```text
Lifecycle.RUNNING
```

生效。

默认阈值可配置，例如：

```text
5 minutes
```

只有：

```text
now - lastMeaningfulProgressAt >= threshold
```

且没有更强证据说明：

```text
WAITING_INPUT
APPROVAL_REQUIRED
known long-running command
RATE_LIMITED
```

时，才可进入：

```text
Condition.STALLED
EvidenceQuality.HEURISTIC
```

**STALLED 不等于 FAILED。**

新 meaningful event 到来时自动恢复：

```text
Condition.NORMAL
```

### 12.4 UI 文案

推荐：

```text
Possibly stalled
No meaningful progress for 5m 12s
```

不要写：

```text
Codex is definitely stuck
```

除非 Source 有权威错误证据。

---

## 13. Source SPI

### 13.1 TaskSource

```java
public interface TaskSource extends AutoCloseable {

    SourceDescriptor descriptor();

    void start(TaskEventSink sink) throws Exception;

    SourceHealth health();

    @Override
    void close() throws Exception;
}
```

### 13.2 TaskEventSink

```java
@FunctionalInterface
public interface TaskEventSink {
    void publish(TaskEvent event);
}
```

### 13.3 SourceDescriptor

```java
public record SourceDescriptor(
        String id,
        String displayName,
        String version,
        Set<SourceCapability> capabilities
) {}
```

### 13.4 SourceCapability

```java
public enum SourceCapability {
    REALTIME_EVENTS,
    DETERMINATE_PROGRESS,
    MULTI_TASK,
    TASK_HIERARCHY,
    ATTENTION_EVENTS,
    COMPLETION_EVENTS,
    STALL_EVIDENCE,
    READ_ONLY,
    BIDIRECTIONAL
}
```

UI 可根据 capability 调整表现，但不得针对具体 `sourceId` 写业务逻辑。

---

## 14. Codex Source 总体设计

### 14.1 目标

`source-codex` 必须同时支持两类使用方式：

#### A. Passive observation

用户已经在其他 Terminal / IDE 中运行 Codex。

TaskBeacon 不负责启动它，但仍能观察状态。

#### B. Managed execution

TaskBeacon 自己启动：

```text
codex exec --json
```

此时能获得更结构化的机器事件。

### 14.2 Composite Source

禁止把 Codex Source 写成一个超大 watcher。

推荐：

```text
                         CodexTaskSource
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
            ▼                  ▼                  ▼
     HookSignalProvider  SessionLogProvider  ExecJsonProvider
            │                  │                  │
            └──────────────┬───┴──────────────────┘
                           ▼
                     CodexRawSignal
                           │
                           ▼
                    CodexSignalFusion
                           │
                           ▼
                   CodexTaskInterpreter
                           │
                           ▼
                       TaskEvent
                           │
                           ▼
                    TaskBeacon Core
```

未来：

```text
AppServerProvider
```

也只增加到 Provider 层，不修改 Core。

---

## 15. CodexRawSignal

Provider 不应该直接产生最终 TaskEvent。

先定义 source-codex 内部模型：

```java
record CodexRawSignal(
        String providerId,
        String signalType,
        Instant observedAt,

        String sessionId,
        String turnId,
        String agentId,
        String parentAgentId,
        String workspace,

        ObservationMode observationMode,
        EvidenceQuality evidenceQuality,

        JsonNode payload
) {}
```

该类型只存在于：

```text
source-codex
```

不得进入 Core。

### 15.1 为什么需要 RawSignal

同一语义可能同时从多个 Provider 得到：

```text
Hook:
  PermissionRequest

Session log:
  approval-related event

Process probe:
  process alive
```

如果 Provider 直接发 TaskEvent，UI/Core 可能收到重复状态变化。

通过 Fusion：

```text
3 signals
   ↓
1 semantic TaskEvent
```

---

## 16. CodexSignalFusion

### 16.1 职责

Fusion 负责：

- 去重
- 时间窗口合并
- 信号优先级
- session / turn / subagent 关联
- 冲突解决
- provider fallback
- evidence 升级

Fusion 不负责 UI 文案。

### 16.2 信号优先级

默认：

```text
AUTHORITATIVE native event
        >
STRONG machine/session semantic event
        >
HEURISTIC process/file signal
```

### 16.3 去重示例

100ms 内收到：

```text
Hook PermissionRequest
SessionLog approval event
```

应只产生一次：

```text
ATTENTION_REQUIRED(APPROVAL_REQUIRED)
```

并使用较高证据：

```text
AUTHORITATIVE
```

### 16.4 冲突示例

若：

```text
Hook Stop
```

和：

```text
process still alive
```

冲突，不能仅因为进程存活继续显示 Working。

进程存在只代表 Codex 程序存在，不代表当前 task 仍在执行。

---

## 17. Codex Task Hierarchy

Codex Adapter 必须保守支持：

```text
Session
  └─ Turn / Run
       ├─ Subagent A
       ├─ Subagent B
       └─ Background job
```

### 17.1 关键规则

- Subagent Stop 不等于 root task 完成。
- Background job 结束不一定等于 turn 结束。
- 新 turn 可能在旧 detached process 尚未退出时开始。
- 多个 Codex session 必须独立跟踪。
- stale session 不得长期占据主状态。

### 17.2 Root aggregation

root task 可以根据 child signal 更新 activity：

```text
child executing command
→ root activity may show EXECUTING_COMMAND
```

但 child terminal state 不得直接使 root terminal。

---

## 18. HookSignalProvider

### 18.1 定位

Hook 用于获得精确 lifecycle / attention / tool 边界信号。

当前 Codex hook surface 中可观察到的事件可能包括：

```text
SessionStart
SessionEnd
UserPromptSubmit
PreToolUse
PermissionRequest
PostToolUse
SubagentStart
SubagentStop
Stop
Interrupt
```

实现必须在运行时探测实际 Codex 版本和可用配置，不得假设所有平台 / 所有版本行为一致。

### 18.2 Hook 不得作为唯一事实来源

原因：

- 不同版本支持范围可能变化。
- 某些事件可能存在配置级、插件级或平台级差异。
- 用户可以不允许 TaskBeacon 安装 hook。
- Hook 可能被用户配置覆盖或删除。

因此 Hook Provider：

```text
preferred precision signal
```

而不是：

```text
single point of truth
```

### 18.3 Fail-open

TaskBeacon hook helper 必须：

1. 启动极快。
2. 不做复杂业务逻辑。
3. 不阻塞 Codex。
4. IPC 失败时直接安全退出。
5. 默认 exit code 不影响任务继续。

推荐：

```text
Codex hook
   ↓
small helper
   ↓
local socket / named pipe
   ↓
TaskBeacon daemon/app
```

不推荐：

```text
hook
  ↓
启动完整 JVM
  ↓
访问数据库
  ↓
HTTP
  ↓
等待 UI
```

### 18.4 Hook 配置安装

安装器必须：

- 读取现有用户配置。
- 保留未知字段。
- 保留用户已有 hook。
- 幂等。
- 使用临时文件 + atomic replace。
- 提供 uninstall，只删除 TaskBeacon 自己写入的配置。
- 写入前备份一次最近版本。
- 配置损坏时不覆盖原文件。

### 18.5 Windows / 平台差异

Hook Provider 必须视为 capability-probed feature。

如果平台/版本不支持或行为不可靠：

```text
SourceHealth = DEGRADED
```

并回退：

```text
SessionLogProvider
```

不得导致应用无法使用。

---

## 19. SessionLogProvider

### 19.1 定位

SessionLogProvider 是 passive observation 的基础能力。

目标：

> 用户不需要通过 TaskBeacon 启动 Codex，也能看到已运行 Codex 的状态。

Codex rollout/session 文件属于实现细节，因此本 Provider 必须被视为：

```text
BEST_EFFORT OBSERVATION
```

而不是 TaskBeacon 公共稳定协议。

### 19.2 禁止全文件重复读取

禁止：

```java
Files.readAllLines(path)
```

在每次文件变化时重新读取完整历史。

必须采用：

```text
File discovery
      ↓
Watch / rescan
      ↓
DirtyPathQueue
      ↓
Coalescer
      ↓
AppendOnlyReader
      ↓
JSON line parser
      ↓
CodexRawSignal
```

### 19.3 FileCursor

建议：

```java
record FileCursor(
        Path path,
        long offset,
        Object fileIdentity,
        byte[] partialLine,
        Instant lastReadAt
) {}
```

需要处理：

- append
- partial JSON line
- truncate
- rotate/recreate
- watcher overflow
- missed watcher event
- application restart
- old session discovery

### 19.4 Bounded read

所有读取必须有上限。

建议：

- 初次打开活跃文件：只读取尾部有限窗口。
- 单次 append：限制最大字节数。
- 超出时分批处理或 resync。
- 禁止因为一个几百 MB rollout 卡死 UI。

具体大小应配置化，并通过性能测试确定。

### 19.5 Watch + rescan 双保险

仅使用 `WatchService` 不足以作为长期唯一机制。

建议：

```text
WatchService
    +
low-frequency directory rescan
```

rescan 用于恢复：

- missed event
- watcher overflow
- 新目录层级
- app sleep/wakeup

### 19.6 Stale session filtering

旧 session 不得重新显示成当前工作。

发现文件时根据：

- session metadata
- last semantic event
- file update time
- active process/supporting evidence
- configured stale threshold

判断是否恢复为 active task。

---

## 20. ExecJsonProvider

### 20.1 定位

当 TaskBeacon 主动启动 Codex 时：

```text
codex exec --json
```

是结构化程度很高的事件通道。

当前 `--json` 模式 stdout 应保持 JSONL，每行一个 JSON event，其他输出走 stderr。

### 20.2 用途

ExecJsonProvider 适合：

- TaskBeacon launcher
- 自动化任务
- fixture
- integration test
- managed task

但不能代表用户所有 Codex 使用方式。

### 20.3 Parser 要求

1. 按行解析 JSONL。
2. stdout 只进入 JSON parser。
3. stderr 进入诊断日志。
4. 未知 event type：记录并忽略/降级。
5. 单行 malformed：不得终止整个 Source。
6. 连续大量 malformed 时将 source health 降级。
7. Adapter 内可保留 `JsonNode`，不可泄露到 Core。

### 20.4 Process 生命周期

必须正确处理：

- normal exit
- non-zero exit
- user cancel
- process killed
- stdout EOF
- stderr drain
- child process cleanup

---

## 21. AppServerProvider（Phase 2）

Codex App Server 可提供更完整的 thread / turn / item 级事件与读取接口。

适合：

- 更精确状态
- 更强 session hierarchy
- 未来交互式控制

但不作为 MVP 硬依赖。

要求：

- 运行时 capability negotiation。
- 协议 DTO 只存在于 `source-codex`。
- experimental API 必须显式 opt-in。
- 协议版本变化不能要求修改 Core。
- Provider 失败后仍可退回 SessionLog / Hook。

---

## 22. ProcessProbe（可选辅助）

ProcessProbe 只用于佐证，不作为“正在工作”的唯一依据。

可以提供：

```text
process alive
process start time
cpu activity
known child/background process
```

这些默认：

```text
EvidenceQuality.HEURISTIC
```

禁止：

```text
process alive -> Activity.RUNNING
```

正确使用方式：

```text
SessionLog 长时间无 semantic progress
+
process completely idle
→ stall confidence 增强
```

或者：

```text
known detached test process busy
→ 不要过早判 stalled
```

MVP 可以先保留接口，不必跨平台实现完整 CPU probe。

---

## 23. CodexTaskInterpreter

Fusion 之后由 Interpreter 产生统一 TaskEvent。

示例语义：

```text
UserPromptSubmit / turn start
    -> TASK_STARTED
    -> Activity.THINKING or STARTING
    -> MEANINGFUL

reasoning / assistant processing
    -> ACTIVITY_CHANGED(THINKING)
    -> MEANINGFUL

command execution begin
    -> ACTIVITY_CHANGED(EXECUTING_COMMAND)
    -> MEANINGFUL

file change / patch
    -> ACTIVITY_CHANGED(EDITING_FILES)
    -> MEANINGFUL

web/search/tool call
    -> ACTIVITY_CHANGED(SEARCHING/CALLING_TOOL)
    -> MEANINGFUL

PermissionRequest
    -> ATTENTION_REQUIRED(APPROVAL_REQUIRED)
    -> AUTHORITATIVE when from native hook

user input wait
    -> ATTENTION_REQUIRED(INPUT_REQUIRED)

turn completed
    -> TASK_SUCCEEDED or run terminal

interrupt
    -> TASK_CANCELLED or appropriate lifecycle
```

### 23.1 Test command classification

可以识别常见命令：

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

转换：

```text
EXECUTING_COMMAND -> TESTING
```

但这只是 display semantic enhancement。

不能影响 lifecycle 正确性。

---

## 24. TaskRegistry

接口：

```java
public interface TaskRegistry {
    void accept(TaskEvent event);
    Optional<TaskState> get(TaskKey key);
    List<TaskState> activeTasks();
    List<TaskState> recentTasks();
    List<TaskState> childrenOf(TaskKey parent);
    Subscription subscribe(TaskStateListener listener);
}
```

建议内部：

```text
ConcurrentHashMap<TaskKey, TaskState>
```

但状态归约必须串行化。

推荐：

```text
Source threads
     ↓
Core event queue
     ↓
Single logical reducer
     ↓
Registry
     ↓
Subscribers
```

或者保证：

```text
same TaskKey -> ordered executor
```

UI 更新必须切换到 JavaFX Application Thread。

---

## 25. PrimaryTaskSelector

悬浮窗通常只显示一个主任务。

不要按“最近更新时间”简单选择。

推荐优先级：

```text
1. ATTENTION = ERROR
2. ATTENTION = APPROVAL_REQUIRED
3. ATTENTION = INPUT_REQUIRED
4. ATTENTION = WARNING
5. Condition = STALLED
6. 用户手动 pin 的 RUNNING task
7. 当前 RUNNING + 最近 meaningful progress
8. 刚失败的 task
9. 刚成功的 task
10. 其他
```

接口：

```java
public interface PrimaryTaskSelector {
    Optional<TaskState> select(
        Collection<TaskState> tasks,
        SelectionContext context
    );
}
```

终态只显示配置时间，例如：

```text
8 seconds
```

然后让位给活跃任务。

---

## 26. Desktop UI — V1

### 26.1 第一版只做工具型 UI

V1 不实现桌宠。

只实现：

```text
Minimal Floating Card
+
Task Panel
+
Tray
```

### 26.2 悬浮卡片

默认大致：

```text
width: 220~260px
height: 72~110px
```

要求：

- always on top
- undecorated
- 可拖动
- 保存屏幕位置
- 单击展开任务列表
- 可隐藏到 Tray
- 多屏幕安全恢复

示例：

```text
┌────────────────────────┐
│ 🔨 Codex                │
│ backend-service         │
│ Running tests · 01:34   │
└────────────────────────┘
```

需要人处理：

```text
┌────────────────────────┐
│ 🙋 Codex                │
│ backend-service         │
│ Approval required       │
└────────────────────────┘
```

Stall：

```text
┌────────────────────────┐
│ ⚠ Codex                 │
│ backend-service         │
│ Possibly stalled · 5m   │
└────────────────────────┘
```

### 26.3 展开任务面板

至少显示：

- active tasks
- source
- project/workspace
- current activity
- attention
- condition
- elapsed time
- source health

不需要完整历史聊天。

---

## 27. Renderer API

Pet 只是 Renderer。

建议 UI 层定义：

```java
public interface StatusRenderer {
    String id();
    Node create(RenderContext context);
    void update(TaskState state);
}
```

V1：

```text
CompactCardRenderer
```

未来：

```text
MinimalDotRenderer
FloatingBarRenderer
PetRenderer
OpenPetBridgeRenderer
```

### 27.1 Pet Renderer Phase 2+

以后 Pet 状态可以映射：

```text
THINKING          -> thinking animation
EXECUTING_COMMAND -> working animation
EDITING_FILES     -> writing animation
ATTENTION         -> wave / raise hand
SUCCEEDED         -> jump once
FAILED            -> failure animation
STALLED           -> confused animation
```

这些映射不得进入 Core。

---

## 28. Tray

Tray 至少包含：

```text
TaskBeacon
────────────
Show / Hide
Active tasks: N
────────────
Sources
Settings
Quit
```

如果当前有 attention：

```text
Approval required: 1
```

可以显示在 menu item。

平台不支持 `SystemTray` 时：

- 应用仍可运行。
- 悬浮窗成为主要入口。

---

## 29. 配置

定义统一 `AppPaths`。

禁止在业务代码散落：

```text
~/.taskbeacon
~/.codex
```

建议：

```json
{
  "ui": {
    "alwaysOnTop": true,
    "animations": true,
    "completedDisplaySeconds": 8,
    "renderer": "compact-card"
  },
  "core": {
    "stallThresholdSeconds": 300,
    "recentTaskRetentionMinutes": 30
  },
  "sources": {
    "codex": {
      "enabled": true,
      "mode": "AUTO",
      "executable": "codex",
      "enableHooks": true,
      "enableSessionLogs": true,
      "enableExecJson": true,
      "enableProcessProbe": false
    }
  },
  "privacy": {
    "showFullCommands": false,
    "persistEventPayloads": false,
    "persistPrompts": false
  }
}
```

### 29.1 Codex mode

```java
public enum CodexObservationMode {
    AUTO,
    PASSIVE,
    MANAGED_ONLY
}
```

#### AUTO

启用可用 Provider，并通过 Fusion 合并。

#### PASSIVE

只观察外部已经运行的 Codex。

#### MANAGED_ONLY

仅管理由 TaskBeacon 自己启动的 exec task。

---

## 30. Source Health

Source 故障与 Task 失败完全不同。

```java
public record SourceHealth(
        HealthLevel level,
        String message,
        Instant updatedAt,
        Map<String, ProviderHealth> providers
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

例如：

```text
SessionLog = healthy
Hook = unavailable
ExecJson = available
```

整体：

```text
DEGRADED or HEALTHY depending on mode
```

Provider 出错不得带崩整个 Source。

Source 出错不得带崩应用。

---

## 31. 隐私与安全

TaskBeacon 默认：

```text
local-first
read-only-first
least-data
```

### 31.1 默认不得持久化

- 完整 Prompt
- 完整 assistant 输出
- 文件内容
- API key
- token
- authorization
- secret
- password
- 完整敏感命令参数

### 31.2 Command display

默认只保留可安全展示的摘要。

例如：

```text
./gradlew test
```

不要长期保存：

```text
curl https://...?...token=SECRET
```

### 31.3 Hook payload

Hook 中可能包含 prompt / assistant message 等字段。

TaskBeacon helper 必须在最早边界进行裁剪。

如果业务只需要：

```text
session_id
turn_id
cwd
hook_event_name
```

就不要把 prompt 内容发给主应用。

### 31.4 Local Event API 安全（未来）

如果未来提供 HTTP：

```text
127.0.0.1 only by default
```

不默认绑定：

```text
0.0.0.0
```

---

## 32. 日志

逻辑 logger：

```text
app
core
source.codex
source.codex.hook
source.codex.sessionlog
source.codex.exec
ui
```

默认：

```text
INFO
```

原始 JSON / Hook payload：

- 默认不记录。
- 只有 DEBUG + privacy explicit opt-in 才允许。

建议滚动：

```text
10 MB / file
max 5 files
```

日志脱敏必须覆盖：

```text
authorization
api_key
token
password
secret
cookie
```

---

## 33. Local Event API（Phase 2）

为了让非 Java 系统零插件接入，未来可提供：

```text
POST 127.0.0.1:<port>/v1/events
```

提交的是 TaskBeacon TaskEvent-compatible payload，而不是 Pet 动画命令。

示例：

```json
{
  "schemaVersion": 1,
  "sourceId": "jenkins",
  "taskId": "build-1842",
  "type": "ACTIVITY_CHANGED",
  "activity": "DEPLOYING",
  "message": "Deploying staging"
}
```

以后：

```text
Jenkins
GitHub Actions
Maven
Gradle
Python
Shell
company agent
```

都能通过 Local API 接入。

Pet Renderer 仍只消费最终 `TaskState`。

---

## 34. 插件化路线

MVP：

- Source 编译进主应用。
- 稳定 `source-api`。

Phase 2：

```text
plugins/
├── taskbeacon-source-jenkins.jar
├── taskbeacon-source-github.jar
└── taskbeacon-source-company-agent.jar
```

建议：

```java
public interface TaskSourceFactory {
    SourceDescriptor descriptor();
    TaskSource create(
        SourceContext context,
        Map<String, Object> config
    );
}
```

可采用：

```text
ServiceLoader
+
separate ClassLoader
```

第一版禁止在 Core/UI 写：

```java
if (sourceId.equals("codex")) { ... }
```

---

## 35. DemoTaskSource

Demo Source 是 MVP 的必需模块。

它需要能模拟：

```text
DISCOVERED
STARTED
THINKING
EXECUTING_COMMAND
EDITING_FILES
APPROVAL_REQUIRED
ATTENTION_CLEARED
STALLED
RECOVERED
SUCCEEDED
FAILED
```

并模拟：

```text
root task
  ├─ subtask A
  └─ subtask B
```

意义：

> 如果删除整个 `source-codex` 模块，TaskBeacon UI/Core 仍然必须能完整运行。

---

## 36. 测试策略

### 36.1 Core reducer

必须覆盖：

- Lifecycle 转换。
- Activity 不覆盖 terminal lifecycle。
- Attention 与 Lifecycle 可同时存在。
- Condition 与 Lifecycle 独立。
- terminal state 保护。
- progress validation。
- `lastObservedAt` 更新。
- `lastMeaningfulProgressAt` 只被 meaningful event 更新。
- 固定 Clock 下的时间逻辑。

### 36.2 StallDetector

必须覆盖：

```text
4m59s -> NORMAL
5m01s -> STALLED
meaningful event -> NORMAL
approval wait -> not stalled
terminal task -> no stall
```

### 36.3 Task hierarchy

必须覆盖：

- child success 不导致 root success。
- child failure 可影响 root attention/condition，但不自动决定 root lifecycle。
- root terminal 后 child stale event 不复活 root。

### 36.4 Fusion tests

必须覆盖：

```text
Hook + SessionLog duplicate
→ one TaskEvent

AUTHORITATIVE vs HEURISTIC conflict
→ authoritative wins

out-of-order signal
→ does not regress terminal state

stale duplicate
→ ignored
```

### 36.5 SessionLog fixtures

路径：

```text
source-codex/src/test/resources/fixtures/session-log/
```

至少：

```text
simple-session.jsonl
multi-turn.jsonl
subagent.jsonl
command-and-edit.jsonl
partial-line.jsonl
malformed-line.jsonl
unknown-event.jsonl
truncated-and-appended.jsonl
```

### 36.6 Exec JSON fixtures

```text
source-codex/src/test/resources/fixtures/exec-json/
```

至少：

```text
simple-success.jsonl
failure.jsonl
command-and-file-edit.jsonl
unknown-event.jsonl
malformed-line.jsonl
```

### 36.7 Hook fixtures

保存脱敏输入：

```text
session-start.json
user-prompt-submit.json
permission-request.json
pre-tool-use.json
post-tool-use.json
subagent-start.json
subagent-stop.json
stop.json
interrupt.json
```

缺失某事件能力时允许 fixture 先存在但 Provider runtime capability disabled。

### 36.8 Append reader tests

必须覆盖：

- append new line
- partial line across writes
- file truncate
- file replace
- large append bounded read
- duplicate watch event
- missing watch event + rescan recovery

### 36.9 Process integration test

普通 CI 不依赖真实 Codex 登录。

实现 fake executable：

```text
fake-codex
```

按时间：

- stdout 输出 JSONL
- stderr 输出日志
- exit 0 / exit 1
- simulate hang

验证：

```text
ProcessBuilder
parser
cancel
stderr drain
source health
```

### 36.10 UI ViewModel tests

ViewModel 必须可以脱离真实 JavaFX Window 测试：

- elapsed time
- badge
- condition text
- attention priority
- completed TTL
- multiple task count

---

## 37. 性能要求

TaskBeacon 应是 ambient desktop utility。

### 37.1 Idle

没有活跃任务时：

- 不应持续高 CPU polling。
- directory rescan 频率应保守。
- UI 不应持续 60 FPS animation（V1 无宠物）。

### 37.2 Active

JSONL ingest 必须增量读取。

禁止：

```text
每 500ms 扫描全部 session 文件并重新解析
```

### 37.3 Backpressure

Core event queue 必须有明确策略。

如果 Source 突然产生大量 observation-only event：

- 可 coalesce heartbeat / duplicate activity。
- terminal / attention event 不得丢失。

---

## 38. 推荐开发顺序

### Phase A — Domain/Core

完成：

```text
source-api
core domain
TaskRelation
Evidence model
Reducer
Registry
StallDetector
PrimaryTaskSelector
source-demo
```

验收：

- 全部 Core 单测通过。
- console demo 可以模拟完整状态流。

### Phase B — Desktop Shell

完成：

```text
CompactCardRenderer
floating window
Task Panel
Tray
ViewModel
```

只使用 DemoTaskSource。

验收：

> source-codex 尚未存在也能得到完整可用 UI。

### Phase C — Codex Passive Observation

先完成：

```text
Codex environment discovery
SessionLogProvider
AppendOnlyReader
CodexRawSignal
CodexSignalFusion
CodexTaskInterpreter
```

目标：

> 用户在普通 Terminal 启动 Codex，TaskBeacon 能独立显示状态。

### Phase D — Hook Enhancement

完成：

```text
Hook capability probe
Hook helper
safe config installer
HookSignalProvider
Fusion dedup
```

Hook 不可用时 Phase C 仍必须可运行。

### Phase E — Managed Exec

完成：

```text
ExecJsonProvider
ProcessBuilder
launcher
fixtures
```

### Phase F — Packaging

完成：

```text
jpackage
Windows
macOS
Linux
```

### Phase G — App Server Evaluation

评估收益后实现：

```text
AppServerProvider
```

### Phase H — Pet Renderer

底层稳定以后再实现：

```text
PetRenderer
pet pack
sprite animation
optional OpenPet bridge
```

---

## 39. MVP 验收标准

以下全部满足才算 MVP 完成。

### 39.1 架构

- `core` 无 Codex import。
- `ui-desktop` 无 Codex import。
- `source-api` 无 Codex import。
- 所有 Codex 协议解析位于 `source-codex`。
- 所有宠物/动画概念只位于 UI/Renderer。
- 删除 `source-codex` 后 Demo Source 仍可驱动完整 UI。

### 39.2 Passive Codex 功能

用户在外部 Terminal 启动 Codex 后：

- TaskBeacon 能发现活动 session。
- 不要求 TaskBeacon 启动该 Codex。
- 能至少展示：开始/活动/等待/完成中的合理子集。
- Hook 不可用时仍能通过 SessionLog 工作。
- SessionLog 不可用时 SourceHealth 明确显示 degraded/disconnected，而不是伪造状态。

### 39.3 Managed Codex 功能

当 TaskBeacon 通过 `codex exec --json` 启动任务：

- JSONL 可正确增量解析。
- stdout/stderr 正确分流。
- unknown/malformed 单行不导致 Source 崩溃。
- process terminal 正确映射。

### 39.4 状态真实性

- `process alive` 不能单独产生 WORKING 判断。
- `lastObservedAt` 与 `lastMeaningfulProgressAt` 分开。
- 没有 total 时绝不显示百分比。
- STALLED 只能作为 Condition，不可自动转 FAILED。
- HEURISTIC inference 必须可被更强 evidence 覆盖。

### 39.5 多任务

- 多个 Codex session 独立跟踪。
- subagent 不覆盖 root task 生命周期。
- UI 显示 active task count。
- PrimaryTaskSelector 按 attention / stall / running 优先级选择。

### 39.6 UI

- 独立悬浮窗。
- 离开 Terminal 后仍持续显示。
- 可拖动并记忆位置。
- 点击可展开任务列表。
- 支持 Tray。
- Completed task 在配置时间后让位。

### 39.7 Resilience

- Hook helper 失败不阻塞 Codex。
- Source provider 崩溃不带崩 CodexTaskSource。
- CodexTaskSource 出错不带崩应用。
- Watcher 丢事件可由 rescan 恢复。
- malformed JSON 不导致 watcher 停止。

### 39.8 Privacy

- 默认不记录完整 prompt。
- 默认不记录完整 assistant 内容。
- 默认不持久化原始 rollout payload。
- 默认对敏感 command 信息裁剪。

### 39.9 Tests

- Core reducer tests 全部通过。
- StallDetector tests 通过。
- hierarchy tests 通过。
- Fusion tests 通过。
- append reader tests 通过。
- Codex fixture tests 通过。

---

## 40. UI 状态文案建议

这些文案属于 UI formatter / i18n，不得写进 Reducer。

```text
DISCOVERED
Detected

STARTING
Starting…

THINKING
Thinking…

EXECUTING_COMMAND
Running command

EDITING_FILES
Editing files

READING_FILES
Reading files

SEARCHING
Searching

TESTING
Running tests

CALLING_TOOL
Using tool

APPROVAL_REQUIRED
Approval required

INPUT_REQUIRED
Input required

STALLED
Possibly stalled

RATE_LIMITED
Rate limited

SUCCEEDED
Completed

FAILED
Failed

CANCELLED
Cancelled

SOURCE DEGRADED
Limited monitoring

SOURCE DISCONNECTED
Source disconnected
```

---

## 41. 推荐的 Codex Source package 结构

```text
source-codex/src/main/java/.../codex/
├── CodexTaskSource.java
├── CodexSourceConfig.java
├── CodexEnvironment.java
│
├── signal/
│   ├── CodexRawSignal.java
│   ├── CodexSignalFusion.java
│   ├── SignalDeduplicator.java
│   └── SignalFingerprint.java
│
├── hierarchy/
│   ├── CodexTaskGraph.java
│   ├── SessionIdentity.java
│   └── TaskHierarchyResolver.java
│
├── interpret/
│   ├── CodexTaskInterpreter.java
│   ├── CommandClassifier.java
│   └── MeaningfulProgressClassifier.java
│
├── provider/
│   ├── CodexSignalProvider.java
│   ├── hook/
│   │   ├── HookSignalProvider.java
│   │   ├── HookCapabilityProbe.java
│   │   ├── HookConfigInstaller.java
│   │   └── HookPayloadParser.java
│   │
│   ├── sessionlog/
│   │   ├── SessionLogProvider.java
│   │   ├── SessionFileDiscovery.java
│   │   ├── AppendOnlyJsonlReader.java
│   │   ├── FileCursor.java
│   │   └── SessionLogParser.java
│   │
│   ├── exec/
│   │   ├── ExecJsonProvider.java
│   │   ├── CodexExecLauncher.java
│   │   └── ExecJsonParser.java
│   │
│   └── appserver/
│       └── AppServerProvider.java   // Phase 2
│
└── health/
    ├── CodexProviderHealth.java
    └── CodexSourceHealthAggregator.java
```

Provider interface 可以只在 `source-codex` 内部定义：

```java
interface CodexSignalProvider extends AutoCloseable {
    String id();
    ProviderHealth health();
    void start(Consumer<CodexRawSignal> sink);
}
```

---

## 42. 推荐的 Core package 结构

```text
core/src/main/java/.../core/
├── model/
│   ├── TaskKey.java
│   ├── TaskRelation.java
│   ├── TaskScope.java
│   ├── TaskEvent.java
│   ├── TaskState.java
│   ├── Lifecycle.java
│   ├── Activity.java
│   ├── Attention.java
│   ├── Condition.java
│   ├── EvidenceQuality.java
│   ├── ObservationMode.java
│   ├── ProgressImpact.java
│   └── TaskProgress.java
│
├── reducer/
│   ├── TaskStateReducer.java
│   └── DefaultTaskStateReducer.java
│
├── registry/
│   ├── TaskRegistry.java
│   └── DefaultTaskRegistry.java
│
├── stall/
│   ├── StallDetector.java
│   └── DefaultStallDetector.java
│
├── selection/
│   ├── PrimaryTaskSelector.java
│   └── DefaultPrimaryTaskSelector.java
│
└── time/
    └── ClockProvider.java   // 或直接注入 java.time.Clock
```

---

## 43. 第一轮让 Codex 实现的范围

为了避免一次性范围过大，第一轮只实现：

### Milestone 1

```text
Gradle modules
source-api
core models
Reducer
Registry
StallDetector
PrimaryTaskSelector
source-demo
Core tests
```

### Milestone 2

```text
JavaFX floating card
Task panel
Tray
Demo ViewModel
UI tests
```

### Milestone 3

```text
source-codex skeleton
CodexRawSignal
AppendOnlyJsonlReader
SessionLogProvider
SignalFusion
Interpreter
fixtures
```

第一轮**不要做 Pet Renderer**。

第一轮**不要做 App Server**。

Hook 可以在 Milestone 3 之后单独实现，以免 Hook 平台差异干扰 Core 交付。

---

## 44. 给实现 Codex 的工作规则

实现者必须：

1. 每个 Milestone 完成后运行完整测试。
2. 修复测试后再进入下一个 Milestone。
3. 遇到 Codex 协议与本文示例不一致时，以实际安装版本和当前官方 schema 为准。
4. 协议差异只在 `source-codex` 内解决。
5. 不允许为了方便把 Codex JSON DTO 放进 Core。
6. 不允许用 `Thread.sleep()` 实现业务时间测试。
7. 不允许每次文件变化重读全部 rollout。
8. 不允许 process alive 直接等价为 task working。
9. 不允许 Hook 失败阻止 Codex。
10. 不允许用 token 数估算任务百分比。
11. 不允许在 V1 提前实现宠物动画而推迟状态准确性。

如果必须偏离本文架构：

- 在代码仓库新增 `docs/decisions/ADR-XXXX.md`。
- 记录问题、备选方案、最终决策和影响。
- 不得静默改变架构边界。

---

## 45. 后续演进路线

### V1 — Task Monitor

```text
Minimal card
Tray
Codex passive monitoring
Codex managed exec
```

### V1.5 — Better Observability

```text
Hooks
Process support signal
AppServer evaluation
better hierarchy
native notifications
```

### V2 — Animated Task Companion

```text
Renderer themes
animated companion
pet pack
sound/reduced motion
```

### V3 — Universal Task Platform

```text
Local Event API
Jenkins
GitHub Actions
Claude Code
Gemini
Maven/Gradle
Plugin SDK
```

### V4 — Optional integrations

```text
IDE plugin
web dashboard
mobile companion
team status bridge
```

任何 V2+ 功能都不得要求重写 Core 基础模型。

---

## 46. 参考实现与调研来源

以下项目只作为架构/行为参考，不表示复制其实现。

### OpenAI Codex

- Repository: https://github.com/openai/codex
- Codex exec JSONL implementation: `codex-rs/exec`
- Hooks implementation/schema: `codex-rs/hooks`
- App Server protocol: `codex-rs/app-server-protocol`

### AgentPet

- https://github.com/ntd4996/agentpet
- 可借鉴：Agent event normalization、hook-based monitoring、session store、multi-agent UX。

### Is Codex Working?

- https://github.com/kim-sin/is-codex-working
- 可借鉴：meaningful progress、多信号状态推断、stuck detection、session/subagent grouping。

### xiangking/agent-pet

- https://github.com/xiangking/agent-pet
- 可借鉴：Tauri floating pet、local source monitoring、WebSocket integration。
- 反面提醒：避免 Source Monitor 与 Pet StateMachine 强耦合。

### OpenPet

- https://github.com/X-T-E-R/OpenPet
- 可借鉴：Pet Runtime、CLI/HTTP/MCP 控制、Renderer 思路。
- 注意：GPL-3.0-or-later；不要在许可证不兼容的情况下复制实现代码。

---

## 47. 最终架构摘要

TaskBeacon 的目标架构：

```text
Codex                 Claude                  Jenkins
  │                      │                       │
  ▼                      ▼                       ▼
source-codex        source-claude         source-jenkins
  │                      │                       │
  └─────────────── TaskEvent ───────────────────┘
                         │
                         ▼
                ┌──────────────────┐
                │ TaskBeacon Core  │
                │                  │
                │ Reducer          │
                │ TaskRegistry     │
                │ StallDetector    │
                │ TaskHierarchy    │
                │ PrimarySelector  │
                └────────┬─────────┘
                         │
                         ▼
                     TaskState
                         │
             ┌───────────┼────────────┐
             ▼           ▼            ▼
       Compact Card     Tray       Pet Renderer
                                      (future)
```

Codex Source 内部：

```text
Hook Provider ───────────┐
                         │
SessionLog Provider ─────┼──> CodexRawSignal
                         │          │
ExecJson Provider ───────┘          ▼
                              Signal Fusion
                                    │
                                    ▼
                               Interpreter
                                    │
                                    ▼
                                TaskEvent
```

TaskBeacon 的核心价值不是“画一个宠物”，而是：

> **可靠地把不同任务系统中不一致、不完整、可信度不同的观测信号，归一化成统一、可解释、可扩展的任务状态。**

只要这个层稳定，Minimal、Tray、Pet、IDE、Web 都只是不同的展示方式。

---

## 48. Definition of Done

一个实现可以被称为“TaskBeacon v0.2 MVP”前，必须满足：

```text
[ ] Core 无 Codex 类型
[ ] UI 无 Codex 类型
[ ] Pet 不存在于 Core API
[ ] DemoTaskSource 可完整驱动 UI
[ ] Codex passive observation 可用
[ ] SessionLog 增量读取而非重复全读
[ ] Hook 不可用时仍可运行
[ ] Hook 失败不会阻塞 Codex
[ ] Exec JSONL provider 可工作
[ ] Signal Fusion 有去重和 evidence precedence
[ ] 多 session 独立
[ ] child completion 不终止 root
[ ] lastObservedAt / lastMeaningfulProgressAt 分离
[ ] Stall 是 Condition，不是 Failed
[ ] 没有虚假百分比
[ ] Source error 不带崩应用
[ ] Privacy defaults 安全
[ ] Core / Fusion / Reader fixture tests 通过
[ ] Minimal floating card + Tray 可用
```
