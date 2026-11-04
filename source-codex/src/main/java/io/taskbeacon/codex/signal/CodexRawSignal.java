package io.taskbeacon.codex.signal;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;

import java.time.Instant;

public record CodexRawSignal(String providerId, String signalType, Instant observedAt,
                             String sessionId, String turnId, String agentId, String parentAgentId,
                             String workspace, ObservationMode observationMode,
                             EvidenceQuality evidenceQuality, JsonNode payload) {
    public CodexRawSignal withWorkspace(String newWorkspace) {
        return new CodexRawSignal(providerId, signalType, observedAt, sessionId, turnId, agentId,
                parentAgentId, newWorkspace, observationMode, evidenceQuality, payload);
    }
}
