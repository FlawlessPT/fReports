package pt.flawless.freports;

import java.util.UUID;

/** Reports de um jogador agrupados por alvo. */
public record HistoryEntry(UUID targetUuid, String targetName, int count, Category lastCategory, long lastCreatedAt) {}
