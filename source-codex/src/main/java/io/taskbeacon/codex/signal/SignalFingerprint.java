package io.taskbeacon.codex.signal;

public record SignalFingerprint(String sessionId, String turnId, String agentId, String semanticType) {
    public static SignalFingerprint of(CodexRawSignal signal) {
        return new SignalFingerprint(safe(signal.sessionId()), safe(signal.turnId()), safe(signal.agentId()),
                canonical(signal.signalType()));
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String canonical(String value) {
        String type = safe(value).toLowerCase().replace('-', '_').replace('.', '_');
        if (type.contains("permission") || type.contains("approval")) return "approval";
        if (type.contains("command") && (type.contains("start") || type.contains("begin"))) return "command_start";
        if (type.contains("file") || type.contains("patch")) return "file_change";
        if (type.contains("complete") || type.equals("stop")) return "completed";
        if (type.contains("fail") || type.contains("error")) return "failed";
        if (type.contains("start") || type.contains("prompt")) return "started";
        return type;
    }
}
