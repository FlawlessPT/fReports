package pt.flawless.freports;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Contrato de acesso às tabelas do fReports. Os futuros completam fora da thread principal. */
public interface ReportsRepository {

    CompletableFuture<Long> createReport(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                                         Category category, String reason);

    /** Mais recentes primeiro; filtros {@code null} = sem filtro. */
    CompletableFuture<List<Report>> listReports(ReportStatus status, Category category, int offset, int limit);

    CompletableFuture<Integer> countReports(ReportStatus status, Category category);

    /** Reports contra cada alvo; {@code openOnly} conta só OPEN e IN_REVIEW. Alvos sem reports ficam de fora. */
    CompletableFuture<Map<UUID, Integer>> countAgainst(Collection<UUID> targets, boolean openOnly);

    CompletableFuture<List<Report>> listByReporter(UUID reporterUuid);

    CompletableFuture<Optional<Report>> getReport(long id);

    /** OPEN para IN_REVIEW; {@code false} se já não estava OPEN. */
    CompletableFuture<Boolean> claim(long id, UUID staffUuid, String staffName);

    /** OPEN/IN_REVIEW para RESOLVED; {@code false} se já estava fechado. */
    CompletableFuture<Boolean> resolve(long id, Verdict verdict, UUID staffUuid, String staffName);

    /** OPEN/IN_REVIEW para REJECTED; {@code false} se já estava fechado. */
    CompletableFuture<Boolean> reject(long id, UUID staffUuid, String staffName);

    CompletableFuture<Void> addNote(long reportId, UUID authorUuid, String authorName, String text);

    CompletableFuture<List<ReportNote>> listNotes(long reportId);

    CompletableFuture<Long> createBug(UUID reporterUuid, String reporterName, String description);

    CompletableFuture<List<BugReport>> listBugs(BugStatus status, int offset, int limit);

    CompletableFuture<Integer> countBugs(BugStatus status);

    CompletableFuture<Void> handleBug(long id, BugStatus status, UUID staffUuid, String staffName);

    CompletableFuture<List<HistoryEntry>> history(UUID reporterUuid);

    CompletableFuture<Void> markNotified(long id);

    /** Reports RESOLVED/REJECTED do jogador ainda não notificados. */
    CompletableFuture<List<Report>> listUnnotifiedFinished(UUID reporterUuid);
}
