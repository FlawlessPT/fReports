package pt.flawless.freports.data;

import pt.flawless.freports.model.BugReport;
import pt.flawless.freports.model.BugStatus;
import pt.flawless.freports.model.Category;
import pt.flawless.freports.model.HistoryEntry;
import pt.flawless.freports.model.Report;
import pt.flawless.freports.model.ReportNote;
import pt.flawless.freports.model.ReportStatus;
import pt.flawless.freports.model.StaffRating;
import pt.flawless.freports.model.Verdict;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fachada fina sobre o {@link ReportsRepository}. Os futuros completam fora da thread principal. */
public class ReportsService {

    private final ReportsRepository repository;

    public ReportsService(ReportsRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Long> createReport(UUID reporterUuid, String reporterName, UUID targetUuid,
                                                String targetName, Category category, String reason) {
        return repository.createReport(reporterUuid, reporterName, targetUuid, targetName, category, reason);
    }

    public CompletableFuture<List<Report>> listReports(ReportStatus status, Category category, int offset, int limit) {
        return repository.listReports(status, category, offset, limit);
    }

    public CompletableFuture<Integer> countReports(ReportStatus status, Category category) {
        return repository.countReports(status, category);
    }

    /** Reports pendentes (OPEN). */
    public CompletableFuture<Integer> countPending() {
        return repository.countReports(ReportStatus.OPEN, null);
    }

    /** Todos os reports contra cada alvo (lore da lista de staff). */
    public CompletableFuture<Map<UUID, Integer>> countReportsAgainst(Collection<UUID> targets) {
        return repository.countAgainst(targets, false);
    }

    /** Só os OPEN e IN_REVIEW contra cada alvo. */
    public CompletableFuture<Map<UUID, Integer>> countOpenAgainst(Collection<UUID> targets) {
        return repository.countAgainst(targets, true);
    }

    public CompletableFuture<List<Report>> listByReporter(UUID reporterUuid) {
        return repository.listByReporter(reporterUuid);
    }

    public CompletableFuture<Optional<Report>> getReport(long id) {
        return repository.getReport(id);
    }

    /** @return {@code false} se o report já não estava OPEN */
    public CompletableFuture<Boolean> claim(long id, UUID staffUuid, String staffName) {
        return repository.claim(id, staffUuid, staffName);
    }

    /** @return {@code false} se o report já estava fechado */
    public CompletableFuture<Boolean> resolve(long id, Verdict verdict, UUID staffUuid, String staffName) {
        return repository.resolve(id, verdict, staffUuid, staffName);
    }

    /** @return {@code false} se o report já estava fechado */
    public CompletableFuture<Boolean> reject(long id, UUID staffUuid, String staffName) {
        return repository.reject(id, staffUuid, staffName);
    }

    public CompletableFuture<Void> addNote(long reportId, UUID authorUuid, String authorName, String text) {
        return repository.addNote(reportId, authorUuid, authorName, text);
    }

    public CompletableFuture<List<ReportNote>> listNotes(long reportId) {
        return repository.listNotes(reportId);
    }

    public CompletableFuture<Long> createBug(UUID reporterUuid, String reporterName, String description) {
        return repository.createBug(reporterUuid, reporterName, description);
    }

    public CompletableFuture<List<BugReport>> listBugs(BugStatus status, int offset, int limit) {
        return repository.listBugs(status, offset, limit);
    }

    public CompletableFuture<Integer> countBugs(BugStatus status) {
        return repository.countBugs(status);
    }

    public CompletableFuture<Void> handleBug(long id, BugStatus status, UUID staffUuid, String staffName) {
        return repository.handleBug(id, status, staffUuid, staffName);
    }

    public CompletableFuture<List<HistoryEntry>> history(UUID reporterUuid) {
        return repository.history(reporterUuid);
    }

    public CompletableFuture<List<StaffRating>> listStaffRatings() {
        return repository.listStaffRatings();
    }

    public CompletableFuture<Boolean> rateStaff(UUID staffUuid, String staffName, UUID raterUuid, String raterName, int score) {
        return repository.rateStaff(staffUuid, staffName, raterUuid, raterName, score);
    }

    public CompletableFuture<Void> markNotified(long id) {
        return repository.markNotified(id);
    }

    public CompletableFuture<List<Report>> listUnnotifiedFinished(UUID reporterUuid) {
        return repository.listUnnotifiedFinished(reporterUuid);
    }
}
