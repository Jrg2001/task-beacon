package io.taskbeacon.codex.signal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodexSignalFusionTest {
    private final Instant now = Instant.parse("2026-09-10T00:00:00Z");

    @Test void deduplicatesEquivalentProvidersAndKeepsStrongestEvidence() {
        CodexRawSignal session = signal("session-log", now, EvidenceQuality.STRONG, ObservationMode.SESSION_LOG);
        CodexRawSignal hook = signal("hook", now.plusMillis(100), EvidenceQuality.AUTHORITATIVE, ObservationMode.NATIVE_EVENT);
        List<CodexRawSignal> result = new CodexSignalFusion(Duration.ofMillis(200)).fuse(List.of(session, hook));
        assertEquals(1, result.size());
        assertEquals("hook", result.getFirst().providerId());
        assertEquals(EvidenceQuality.AUTHORITATIVE, result.getFirst().evidenceQuality());
    }

    @Test void keepsSemanticallyEqualSignalsOutsideWindow() {
        CodexRawSignal first = signal("session-log", now, EvidenceQuality.STRONG, ObservationMode.SESSION_LOG);
        CodexRawSignal later = signal("session-log", now.plusSeconds(2), EvidenceQuality.STRONG, ObservationMode.SESSION_LOG);
        assertEquals(2, new CodexSignalFusion(Duration.ofMillis(200)).fuse(List.of(first, later)).size());
    }

    private CodexRawSignal signal(String provider, Instant at, EvidenceQuality quality, ObservationMode mode) {
        return new CodexRawSignal(provider, "approval_required", at, "s", "t", null, null,
                "/work", mode, quality, JsonNodeFactory.instance.objectNode());
    }
}
