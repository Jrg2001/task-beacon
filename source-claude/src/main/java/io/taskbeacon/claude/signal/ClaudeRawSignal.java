package io.taskbeacon.claude.signal;

import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;

import java.time.Instant;

public record ClaudeRawSignal(String signalType, Instant observedAt, String sessionId,
                              String agentId, String workspace, String toolName,
                              ObservationMode observationMode, EvidenceQuality evidenceQuality) { }
