package pt.flawless.freports.model;

import java.util.UUID;

/** Linha de freports_reports; campos opcionais a {@code null}, instantes em epoch millis. */
public record Report(long id, UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                     Category category, String reason, ReportStatus status, UUID claimedByUuid, String claimedByName,
                     Verdict verdict, boolean notified, long createdAt, long updatedAt, Long resolvedAt) {}
