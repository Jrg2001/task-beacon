# TaskBeacon

TaskBeacon 是一个 local-first 的桌面任务状态监视器。它把来源不同、可信度不同的任务观测信号归一化为统一状态，并通过始终置顶的小卡片、任务面板和系统托盘展示。当前实现支持 Demo 场景，以及对外部 Terminal 中 Codex rollout/session JSONL 和 Claude Code transcript JSONL 的被动增量观察。

## 效果展示

Codex 和 Claude Code 各有一只桌面宠物，宠物的动作实时反映对应工具的任务状态。内置 8 种狗和 6 种猫，右键宠物即可切换：

<p align="center"><img src="docs/images/pets-lineup-v3.gif" alt="内置宠物：8 种狗和 6 种猫" width="100%"></p>

以默认的秋田犬为例，不同任务状态下的动作：

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/images/akita-thinking.gif" alt="思考中" width="136"><br><b>思考中</b><br><sub>模型正在推理或生成回复</sub></td>
    <td align="center" width="25%"><img src="docs/images/akita-working.gif" alt="使用工具" width="136"><br><b>使用工具</b><br><sub>读写文件、搜索、调用工具</sub></td>
    <td align="center" width="25%"><img src="docs/images/akita-running.gif" alt="执行命令" width="136"><br><b>执行命令</b><br><sub>运行命令、测试或构建</sub></td>
    <td align="center" width="25%"><img src="docs/images/akita-completed.gif" alt="完成" width="136"><br><b>完成</b><br><sub>一轮任务成功结束</sub></td>
  </tr>
  <tr>
    <td align="center" width="25%"><img src="docs/images/akita-attention.gif" alt="需要注意" width="136"><br><b>需要注意</b><br><sub>失败、等待审批或等待输入</sub></td>
    <td align="center" width="25%"><img src="docs/images/akita-stalled.gif" alt="可能卡住" width="136"><br><b>可能卡住</b><br><sub>长时间没有任何进展</sub></td>
    <td align="center" width="25%"><img src="docs/images/akita-idle.gif" alt="空闲" width="136"><br><b>空闲</b><br><sub>空闲 30 秒后趴下，随后入睡</sub></td>
    <td></td>
  </tr>
</table>

## 环境与构建

- JDK 21
- Gradle 8.14+（可先用已安装 Gradle 执行 `gradle wrapper`）

```powershell
./gradlew.bat test
./gradlew.bat :app:run --args="--demo"
```

Linux/macOS：

```bash
./gradlew test
./gradlew :app:run --args="--demo"
```

## 运行模式

- 默认：同时被动观察 Codex 的 `$CODEX_HOME/sessions` 和 Claude Code 的 `$CLAUDE_CONFIG_DIR/projects`；未设置环境变量时分别使用用户目录下的 `.codex/sessions` 与 `.claude/projects`。
- `--demo`：只运行内置演示源；演示多任务、子任务、审批、stall 恢复和完成。
- `--with-demo`：被动 Codex、Claude Code 观察与 Demo 同时运行。

直接启动（默认同时观察 Codex 与 Claude Code）：

```powershell
./gradlew.bat :app:run
```

随后可在任意其他 Terminal 启动 `codex`。TaskBeacon 不处于 Codex 执行链路中；关闭或崩溃不会阻塞 Codex。

也可以在其他 Terminal 启动 `claude`。TaskBeacon 会只读增量观察
`${CLAUDE_CONFIG_DIR:-~/.claude}/projects/**/*.jsonl`，无需修改 Claude Code 配置；只保留任务状态、时间、工作目录和工具名称，不保留 prompt、回复、工具输入或工具输出。

## 配置与日志

首次运行会创建安全默认配置：

- Windows：`%LOCALAPPDATA%\TaskBeacon\config.json`
- macOS：`~/Library/Application Support/TaskBeacon/config.json`
- Linux：`${XDG_CONFIG_HOME:-~/.config}/taskbeacon/config.json`

日志位于同目录的 `logs/`。默认不记录或持久化完整 prompt、assistant 回复、文件内容、原始 rollout payload 或完整敏感命令。

## 内置宠物素材

Codex 和 Claude Code 的宠物可以分别设置：右键宠物，在“内置宠物”里选择 8 种狗或 6 种猫，也可以选择自定义图片。默认 Codex 是边境牧羊犬，Claude Code 是秋田犬。

宠物素材来自 Pet Dogs Pack 和 Pet Cats Pack，均为 CC0 许可，可用于商业和非商业项目，原始许可证随素材放在 `ui-desktop/src/main/resources/pets/`。部分宠物在原素材基础上调整了配色或细节，或由同包中的其他品种改绘而来，生成脚本在 `tools/pets/`；README 中的展示动图由 `tools/readme/make_readme_media.py` 生成。

## 当前限制

- 首轮按照设计文档 v0.2 的 Milestone 1–3 实现；Claude/Codex Hook、App Server、Pet Renderer 和 Local Event API 尚未实现。
- SessionLog/transcript 是 best-effort 被动观测。上游协议变化应分别隔离在 `source-codex` 或 `source-claude` 中修复。
- 当前被动 Provider 使用低频增量 rescan；cursor 可恢复漏事件、partial line 和 truncate，原生 WatchService 可在后续加入以降低活跃时延。
- `codex exec --json` 的 fixture 与解释边界已预留，但完整 managed-process Provider 属于下一轮。
- 系统不支持 AWT SystemTray 时，悬浮卡片仍可运行。

## 打包

三种产物都内置精简后的 Java 运行时（只包含用到的模块，约 60 MB），用户无需另装 Java；程序、快捷方式和托盘都使用秋田犬图标（由 `tools/icons/make_icons.py` 生成）。

| 命令 | 产物 | 说明 |
|---|---|---|
| `./gradlew.bat :app:portableExe` | `release/TaskBeacon/` | 绿色版目录，入口为 `TaskBeacon.exe`；整个目录才是完整应用，不能只复制 `.exe`。项目根目录的 `TaskBeacon.bat` 可直接双击启动它。 |
| `./gradlew.bat :app:portableZip` | `release/TaskBeacon-<版本>-windows-portable.zip` | 上述目录的压缩包，解压到任意位置后双击 `TaskBeacon.exe` 即可。 |
| `./gradlew.bat :app:windowsInstaller` | `release/TaskBeacon-<版本>.exe` | 安装程序：为当前用户安装到 `%LOCALAPPDATA%\TaskBeacon App`（无需管理员权限），创建开始菜单和桌面快捷方式，可在“应用和功能”中卸载；新版本安装时会直接升级旧版本。卸载只删除程序本身，配置、日志和宠物数据（`%LOCALAPPDATA%\TaskBeacon`）会保留。 |

生成安装程序需要 WiX Toolset 3（例如在管理员终端执行 `winget install --id WiXToolset.WiXToolset -e`），并确保其 `bin` 目录在 `PATH` 中。修改源码后需要重新执行对应命令生成新版本；`jpackage` 产物按宿主 OS 构建，不做跨平台交叉编译。

新增依赖时，如果它需要其他 Java 模块，需要同步更新 `app/build.gradle.kts` 中的 `runtimeModules`（可用 `jdeps --print-module-deps` 检查）。
