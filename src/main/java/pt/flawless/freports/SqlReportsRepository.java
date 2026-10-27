package pt.flawless.freports;

import pt.flawless.fapi.database.DatabaseService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Acesso às tabelas do fReports. Todos os métodos correm no pool do {@link DatabaseService} e os futuros
 * falham com a {@link SQLException} original. Instantes em epoch millis.
 */
public class SqlReportsRepository implements ReportsRepository {

    private static final String REPORT_COLS = "id, reporter_uuid, reporter_name, target_uuid, target_name, category, reason, "
            + "status, claimed_by_uuid, claimed_by_name, verdict, notified, created_at, updated_at, resolved_at";
    private static final String BUG_COLS = "id, reporter_uuid, reporter_name, description, status, created_at, "
            + "handled_by_uuid, handled_by_name, handled_at";
    private static final String CLOSE = "UPDATE freports_reports SET status = ?, verdict = ?, claimed_by_uuid = ?, "
            + "claimed_by_name = ?, updated_at = ?, resolved_at = ? WHERE id = ? AND status IN ('OPEN', 'IN_REVIEW')";

    private final DatabaseService database;

    public SqlReportsRepository(DatabaseService database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Long> createReport(UUID reporterUuid, String reporterName, UUID targetUuid,
                                                String targetName, Category category, String reason) {
        long now = System.currentTimeMillis();
        return insert("INSERT INTO freports_reports (reporter_uuid, reporter_name, target_uuid, target_name, category, "
                        + "reason, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                reporterUuid.toString(), reporterName, targetUuid.toString(), targetName, category.name(), reason, now, now);
    }

    @Override
    public CompletableFuture<List<Report>> listReports(ReportStatus status, Category category, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + REPORT_COLS + " FROM freports_reports" + where(status, category, args)
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        return query(sql, args, SqlReportsRepository::report);
    }

    @Override
    public CompletableFuture<Integer> countReports(ReportStatus status, Category category) {
        List<Object> args = new ArrayList<>();
        return query("SELECT COUNT(*) FROM freports_reports" + where(status, category, args), args,
                rs -> rs.getInt(1)).thenApply(list -> list.get(0));
    }

    @Override
    public CompletableFuture<Map<UUID, Integer>> countAgainst(Collection<UUID> targets, boolean openOnly) {
        if (targets.isEmpty()) return CompletableFuture.completedFuture(Map.of());
        List<Object> args = new ArrayList<>();
        targets.forEach(t -> args.add(t.toString()));
        String sql = "SELECT target_uuid, COUNT(*) FROM freports_reports WHERE target_uuid IN ("
                + "?,".repeat(targets.size() - 1) + "?)" + (openOnly ? " AND status IN ('OPEN', 'IN_REVIEW')" : "")
                + " GROUP BY target_uuid";
        return query(sql, args, rs -> Map.entry(UUID.fromString(rs.getString(1)), rs.getInt(2)))
                .thenApply(list -> {
                    Map<UUID, Integer> map = new HashMap<>();
                    list.forEach(e -> map.put(e.getKey(), e.getValue()));
                    return map;
                });
    }

    @Override
    public CompletableFuture<List<Report>> listByReporter(UUID reporterUuid) {
        return query("SELECT " + REPORT_COLS + " FROM freports_reports WHERE reporter_uuid = ? "
                + "ORDER BY created_at DESC, id DESC", List.of(reporterUuid.toString()), SqlReportsRepository::report);
    }

    @Override
    public CompletableFuture<Optional<Report>> getReport(long id) {
        return query("SELECT " + REPORT_COLS + " FROM freports_reports WHERE id = ?", List.of(id),
                SqlReportsRepository::report).thenApply(list -> list.stream().findFirst());
    }

    @Override
    public CompletableFuture<Boolean> claim(long id, UUID staffUuid, String staffName) {
        return update("UPDATE freports_reports SET status = 'IN_REVIEW', claimed_by_uuid = ?, claimed_by_name = ?, "
                        + "updated_at = ? WHERE id = ? AND status = 'OPEN'",
                staffUuid.toString(), staffName, System.currentTimeMillis(), id).thenApply(n -> n > 0);
    }

    @Override
    public CompletableFuture<Boolean> resolve(long id, Verdict verdict, UUID staffUuid, String staffName) {
        long now = System.currentTimeMillis();
        return update(CLOSE, ReportStatus.RESOLVED.name(), verdict.name(), staffUuid.toString(), staffName, now, now, id)
                .thenApply(n -> n > 0);
    }

    @Override
    public CompletableFuture<Boolean> reject(long id, UUID staffUuid, String staffName) {
        long now = System.currentTimeMillis();
        return update(CLOSE, ReportStatus.REJECTED.name(), null, staffUuid.toString(), staffName, now, now, id)
                .thenApply(n -> n > 0);
    }

    @Override
    public CompletableFuture<Void> addNote(long reportId, UUID authorUuid, String authorName, String text) {
        return update("INSERT INTO freports_notes (report_id, author_uuid, author_name, text, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)", reportId, authorUuid.toString(), authorName, text,
                System.currentTimeMillis()).thenApply(n -> null);
    }

    @Override
    public CompletableFuture<List<ReportNote>> listNotes(long reportId) {
        return query("SELECT id, report_id, author_uuid, author_name, text, created_at FROM freports_notes "
                        + "WHERE report_id = ? ORDER BY created_at, id", List.of(reportId),
                rs -> new ReportNote(rs.getLong(1), rs.getLong(2), UUID.fromString(rs.getString(3)), rs.getString(4),
                        rs.getString(5), rs.getLong(6)));
    }

    @Override
    public CompletableFuture<Long> createBug(UUID reporterUuid, String reporterName, String description) {
        return insert("INSERT INTO freports_bugs (reporter_uuid, reporter_name, description, created_at) "
                + "VALUES (?, ?, ?, ?)", reporterUuid.toString(), reporterName, description, System.currentTimeMillis());
    }

    @Override
    public CompletableFuture<List<BugReport>> listBugs(BugStatus status, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        if (status != null) args.add(status.name());
        args.add(limit);
        args.add(offset);
        return query("SELECT " + BUG_COLS + " FROM freports_bugs" + (status == null ? "" : " WHERE status = ?")
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", args, rs -> new BugReport(rs.getLong(1),
                UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), BugStatus.valueOf(rs.getString(5)),
                rs.getLong(6), uuid(rs.getString(7)), rs.getString(8), nullableLong(rs, 9)));
    }

    @Override
    public CompletableFuture<Integer> countBugs(BugStatus status) {
        return query("SELECT COUNT(*) FROM freports_bugs" + (status == null ? "" : " WHERE status = ?"),
                status == null ? List.of() : List.of(status.name()), rs -> rs.getInt(1)).thenApply(l -> l.get(0));
    }

    @Override
    public CompletableFuture<Void> handleBug(long id, BugStatus status, UUID staffUuid, String staffName) {
        return update("UPDATE freports_bugs SET status = ?, handled_by_uuid = ?, handled_by_name = ?, handled_at = ? "
                        + "WHERE id = ?", status.name(), staffUuid.toString(), staffName, System.currentTimeMillis(), id)
                .thenApply(n -> null);
    }

    @Override
    public CompletableFuture<List<HistoryEntry>> history(UUID reporterUuid) {
        // o último report de cada alvo dá o nome, a categoria e a data; o resto agrega-se por alvo
        String sql = "SELECT r.target_uuid, r.target_name, g.cnt, r.category, r.created_at FROM freports_reports r "
                + "JOIN (SELECT target_uuid, COUNT(*) cnt, MAX(id) last_id FROM freports_reports "
                + "WHERE reporter_uuid = ? GROUP BY target_uuid) g ON r.id = g.last_id ORDER BY r.id DESC";
        return query(sql, List.of(reporterUuid.toString()), rs -> new HistoryEntry(UUID.fromString(rs.getString(1)),
                rs.getString(2), rs.getInt(3), Category.valueOf(rs.getString(4)), rs.getLong(5)));
    }

    @Override
    public CompletableFuture<Void> markNotified(long id) {
        return update("UPDATE freports_reports SET notified = TRUE WHERE id = ?", id).thenApply(n -> null);
    }

    @Override
    public CompletableFuture<List<Report>> listUnnotifiedFinished(UUID reporterUuid) {
        return query("SELECT " + REPORT_COLS + " FROM freports_reports WHERE reporter_uuid = ? AND notified = FALSE "
                + "AND status IN ('RESOLVED', 'REJECTED') ORDER BY id", List.of(reporterUuid.toString()),
                SqlReportsRepository::report);
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static String where(ReportStatus status, Category category, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if (status != null) {
            parts.add("status = ?");
            args.add(status.name());
        }
        if (category != null) {
            parts.add("category = ?");
            args.add(category.name());
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    private CompletableFuture<Long> insert(String sql, Object... args) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, Arrays.asList(args));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    private CompletableFuture<Integer> update(String sql, Object... args) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, Arrays.asList(args));
                return ps.executeUpdate();
            }
        });
    }

    private <T> CompletableFuture<List<T>> query(String sql, List<Object> args, RowMapper<T> mapper) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> rows = new ArrayList<>();
                    while (rs.next()) rows.add(mapper.map(rs));
                    return rows;
                }
            }
        });
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
    }

    private static Report report(ResultSet rs) throws SQLException {
        String verdict = rs.getString(11);
        return new Report(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                UUID.fromString(rs.getString(4)), rs.getString(5), Category.valueOf(rs.getString(6)), rs.getString(7),
                ReportStatus.valueOf(rs.getString(8)), uuid(rs.getString(9)), rs.getString(10),
                verdict == null ? null : Verdict.valueOf(verdict), rs.getBoolean(12), rs.getLong(13), rs.getLong(14),
                nullableLong(rs, 15));
    }

    private static UUID uuid(String s) {
        return s == null ? null : UUID.fromString(s);
    }

    private static Long nullableLong(ResultSet rs, int col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
