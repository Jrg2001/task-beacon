# Mock-Codex for TaskBeacon (无需 Token 模拟测试脚本)
# 用法: powershell -ExecutionPolicy Bypass -File .\mock-codex.ps1 [-Workspace "D:\YourProject"] [-StepDelaySeconds 3]

param(
    [string]$Workspace = "D:\TaskBeacon",
    [int]$StepDelaySeconds = 3
)

$codexHome = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE ".codex" }
$dateDir = (Get-Date).ToString("yyyy\\MM\\dd")
$targetDir = Join-Path $codexHome "sessions\$dateDir"

if (!(Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

$sessionId = [guid]::NewGuid().ToString()
$rolloutFile = Join-Path $targetDir "rollout-$((Get-Date).ToString('yyyy-MM-ddTHH-mm-ss'))-$sessionId.jsonl"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " TaskBeacon Codex Mock 模拟器启动" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "工作区目录 : $Workspace" -ForegroundColor Yellow
Write-Host "会话日志   : $rolloutFile" -ForegroundColor Gray
Write-Host "步骤间隔   : $StepDelaySeconds 秒" -ForegroundColor Gray
Write-Host "提示: 请先启动 TaskBeacon (.\TaskBeacon.bat 或 .\gradlew.bat :app:run)" -ForegroundColor Magenta
Write-Host "------------------------------------------"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Append-Event($jsonObj, $desc) {
    $nowIso = [DateTime]::UtcNow.ToString("o")
    $jsonObj.timestamp = $nowIso
    $line = $jsonObj | ConvertTo-Json -Compress -Depth 5
    [System.IO.File]::AppendAllText($rolloutFile, $line + [Environment]::NewLine, $utf8NoBom)
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] -> $desc" -ForegroundColor Yellow
    Start-Sleep -Seconds $StepDelaySeconds
}

# 1. 会话初始化 (携带 cwd)
Append-Event @{
    type = "session_meta"
    payload = @{
        id = $sessionId
        cwd = $Workspace
    }
} "1. 会话发现 (session_meta, cwd: $Workspace)"

# 2. 任务启动 (Thinking)
Append-Event @{
    type = "event_msg"
    payload = @{
        type = "task_started"
        turn_id = "turn-1"
    }
} "2. 任务开始 (task_started -> 应显示: [项目名] & Thinking…)"

# 3. 运行命令 (Running: git status)
Append-Event @{
    type = "response_item"
    payload = @{
        type = "custom_tool_call"
        name = "exec"
        command = "git status"
        turn_id = "turn-1"
    }
} "3. 执行命令 (exec git status -> 应显示: Running: git status)"

# 4. 调用工具 (Tool: read_file)
Append-Event @{
    type = "response_item"
    payload = @{
        type = "custom_tool_call"
        name = "read_file"
        turn_id = "turn-1"
    }
} "4. 调用工具 (read_file -> 应显示: Tool: read_file)"

# 5. 编辑文件 (Editing: TaskViewModelFactory.java)
Append-Event @{
    type = "event_msg"
    payload = @{
        type = "item_completed"
        turn_id = "turn-1"
        item = @{
            type = "file_change"
            file = "TaskViewModelFactory.java"
        }
    }
} "5. 编辑文件 (file_change -> 应显示: Editing: TaskViewModelFactory.java)"

# 6. 执行测试命令 (Testing: ./gradlew test)
Append-Event @{
    type = "response_item"
    payload = @{
        type = "custom_tool_call"
        name = "exec"
        command = "./gradlew test"
        turn_id = "turn-1"
    }
} "6. 运行测试 (exec ./gradlew test -> 应显示: Testing: ./gradlew test)"

# 7. 任务完成 (Completed)
Append-Event @{
    type = "event_msg"
    payload = @{
        type = "task_complete"
        turn_id = "turn-1"
    }
} "7. 任务完成 (task_complete -> 应显示: Completed)"

Write-Host "------------------------------------------"
Write-Host "Mock simulation complete. Session log retained for inspection." -ForegroundColor Green
