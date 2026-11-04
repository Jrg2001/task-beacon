package io.taskbeacon.core.model;

public enum EvidenceQuality {
    AUTHORITATIVE(3), STRONG(2), HEURISTIC(1), UNKNOWN(0);
    private final int rank;
    EvidenceQuality(int rank) { this.rank = rank; }
    public int rank() { return rank; }
}
