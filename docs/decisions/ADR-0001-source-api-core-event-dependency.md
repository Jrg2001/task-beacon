# ADR-0001：Source SPI 对 Core 事件契约的单向依赖

- 状态：Accepted
- 日期：2026-09-10

## 问题

v0.2 同时规定 `TaskEvent`/`TaskKey` 位于 `core`，`TaskEventSink` 位于 `source-api`，并给出 `TaskEventSink.publish(TaskEvent)`。因此 Source SPI 若完全不依赖 Core 类型，Java 类型系统下无法保持该接口签名；把事件复制到 `source-api` 又会违反领域模型归属并产生双模型。

## 决策

`core` 保持最底层纯领域模块；`source-api` 单向依赖 `core` 的稳定模型。各 Source 依赖 `source-api`，UI 依赖 `core`，装配只发生在 `app`。

```text
core ← source-api ← source-demo/source-codex
core ← ui-desktop
all ← app
```

## 影响

- 不存在循环依赖，Core 仍不知道任何 Source 或 Codex。
- `TaskEvent` 只有一个权威定义。
- 若未来必须让 Source SDK 完全脱离 Core artifact，可把稳定模型拆成独立 `task-contract` 模块；这不改变领域语义。
