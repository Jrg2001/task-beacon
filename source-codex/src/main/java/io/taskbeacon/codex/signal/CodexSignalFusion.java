package io.taskbeacon.codex.signal;

import java.time.Duration;
import java.util.*;

public final class CodexSignalFusion {
    private final Duration window;

    public CodexSignalFusion(Duration window) {
        this.window = Objects.requireNonNull(window);
    }

    public List<CodexRawSignal> fuse(Collection<CodexRawSignal> signals) {
        List<CodexRawSignal> ordered = signals.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(CodexRawSignal::observedAt)).toList();
        List<CodexRawSignal> selected = new ArrayList<>();
        for (CodexRawSignal signal : ordered) {
            SignalFingerprint key = SignalFingerprint.of(signal);
            int matching = -1;
            for (int index = selected.size() - 1; index >= 0; index--) {
                CodexRawSignal candidate = selected.get(index);
                if (SignalFingerprint.of(candidate).equals(key)
                        && Duration.between(candidate.observedAt(), signal.observedAt()).abs().compareTo(window) <= 0) {
                    matching = index;
                    break;
                }
            }
            if (matching < 0) {
                selected.add(signal);
            } else {
                CodexRawSignal prior = selected.get(matching);
                if (signal.evidenceQuality().rank() > prior.evidenceQuality().rank()
                        || signal.evidenceQuality() == prior.evidenceQuality()
                        && signal.observedAt().isAfter(prior.observedAt())) {
                    selected.set(matching, signal);
                }
            }
        }
        return List.copyOf(selected);
    }
}
