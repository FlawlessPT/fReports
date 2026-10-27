package pt.flawless.freports;

import java.util.UUID;

public record ReportNote(long id, long reportId, UUID authorUuid, String authorName, String text, long createdAt) {}
