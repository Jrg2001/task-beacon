# TaskBeacon Architecture

实现基线：[`docs/design/taskbeacon-design-v0.2.md`](docs/design/taskbeacon-design-v0.2.md)。

## 数据流

```text
Demo / Codex / Claude Code providers
        ↓
source-specific observations
        ↓
TaskEvent
        ↓
Reducer → Registry → StallDetector / PrimaryTaskSelector
        ↓
TaskState
        ↓
Compact Card / Task Panel / Tray
```

Claude Code 被动观察使用独立适配器，避免把 transcript 格式泄漏到 Core：

```text
TranscriptFileDiscovery (~/.claude/projects/**/*.jsonl)
        → bounded incremental reader + partial-line recovery
        → ClaudeTranscriptParser (discard prompt/response/tool payload content)
        → ClaudeTaskInterpreter → TaskEvent
```

Codex 被动观察的内部数据流：

```text
SessionFileDiscovery
        ↓ low-frequency rescan
AppendOnlyJsonlReader (bounded cursor + partial line)
        ↓
SessionLogParser → CodexRawSignal
        ↓
CodexSignalFusion (semantic dedup + evidence precedence)
        ↓
CodexTaskInterpreter → TaskEvent
```

## 模块

- `core`：纯领域模型、Reducer、串行化 Registry、层级传播、stall 判断、主任务选择。无 Source/UI/Codex 依赖。
- `source-api`：Source 生命周期 SPI。它只依赖 Core 的稳定事件契约。
- `source-demo`：独立演示源，用于验证删除 `source-codex` 后系统仍可运行。
- `source-codex`：Codex 私有 DTO、rollout 增量读取、信号融合与解释。
- `source-claude`：Claude Code transcript 增量读取、隐私裁剪与任务状态解释。
- `ui-desktop`：JavaFX 卡片/面板/ViewModel 与可选 AWT Tray。无 Codex 依赖。
- `app`：配置、路径、日志、Source 和 UI 的唯一装配点。

## 状态与时间

任务状态由 `Lifecycle + Activity + Attention + Condition` 四个正交维度组成。`STALLED` 只能是 Condition。所有事件刷新 `lastObservedAt`；只有 `ProgressImpact.MEANINGFUL` 刷新 `lastMeaningfulProgressAt`。业务时间由 `Clock` 注入。

父子任务独立归约。子任务终态不会结束 root；子任务失败只把仍活跃的父任务标记为 warning/degraded。

## 可靠性与隐私

Source 异常被隔离，listener 异常不会中断 Registry。Session 文件只增量读取，不重复载入历史；单行 malformed JSON 被跳过。Adapter 只向 Core 传输裁剪后的通用字段，默认不持久化原始 payload。

架构澄清见 [ADR-0001](docs/decisions/ADR-0001-source-api-core-event-dependency.md)。
