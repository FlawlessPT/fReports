package pt.flawless.freports.model;

import java.util.UUID;

public record BugReport(long id, UUID reporterUuid, String reporterName, String description, BugStatus status,
                        long createdAt, UUID handledByUuid, String handledByName, Long handledAt) {}
