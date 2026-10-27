package pt.flawless.freports;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import pt.flawless.fapi.enums.FInventorySize;
import pt.flawless.fapi.inventory.ConfirmMenu;
import pt.flawless.fapi.inventory.FInventory;
import pt.flawless.fapi.items.FItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Menus de staff: fila de reports, detalhe, bug reports e histórico de denúncias de um jogador. */
public final class StaffMenus implements Listener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final int PER_PAGE = 28;
    private static final int PREV_SLOT = 48, BACK_SLOT = 49, NEXT_SLOT = 50;
    private static final int FILTER_A = 46, FILTER_B = 52;
    // detalhe
    private static final int D_INFO = 13, D_CLAIM = 20, D_TP = 22, D_NOTES = 24, D_REJECT = 40;
    private static final int[] D_VERDICT_SLOTS = {29, 31, 33};
    private static final Verdict[] VERDICTS = Verdict.values();
    private static final Material[] VERDICT_ICONS = {Material.EMERALD, Material.GRAY_DYE, Material.RED_DYE};
    private static final List<ReportStatus> STATUS_CYCLE = cycle(ReportStatus.values());
    private static final List<Category> CATEGORY_CYCLE = cycle(Category.values());
    private static final List<BugStatus> BUG_CYCLE = cycle(BugStatus.values());

    private static StaffMenus instance;

    private enum Kind { LIST, DETAIL, BUGS, HISTORY }

    /** Estado da lista de reports, para voltar a ela a partir do detalhe. */
    private record Ctx(ReportStatus status, Category category, int page) {
    }

    private record NotePending(long reportId, Ctx ctx, long expires) {
    }

    private static final class Holder implements InventoryHolder {
        final Kind kind;
        final Ctx ctx;
        final int page;
        final BugStatus bugFilter;
        List<Report> reports = List.of();
        List<BugReport> bugs = List.of();
        long reportId;
        Report report;
        HistoryHolderData historyOwner;
        Inventory inventory;

        Holder(Kind kind, Ctx ctx, int page, BugStatus bugFilter) {
            this.kind = kind;
            this.ctx = ctx;
            this.page = page;
            this.bugFilter = bugFilter;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final FReports plugin;
    private final ReportsService service;
    private final ReportsConfig config;
    private final Map<UUID, NotePending> notes = new ConcurrentHashMap<>();

    private StaffMenus(FReports plugin, ReportsService service, ReportsConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    static void install(FReports plugin, ReportsService service, ReportsConfig config) {
        instance = new StaffMenus(plugin, service, config);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** Fila de reports para analisar. Thread principal. */
    public static void openReports(Player p) {
        instance.openList(p, new Ctx(ReportStatus.OPEN, null, 0));
    }

    /** Detalhe de um report (voltar leva à fila). Thread principal. */
    static void openReport(Player p, long id) {
        instance.openDetail(p, id, new Ctx(ReportStatus.OPEN, null, 0));
    }

    /** Lista de bug reports. Thread principal. */
    public static void openBugs(Player p) {
        instance.openBugList(p, BugStatus.OPEN, 0);
    }

    /** Histórico dos jogadores que {@code reporterUuid} denunciou. Thread principal. */
    public static void openHistory(Player p, UUID reporterUuid, String reporterName) {
        instance.openHistoryMenu(p, reporterUuid, reporterName, 0);
    }

    // ---------- lista de reports ----------

    private record ListData(int page, int total, List<Report> list, Map<UUID, Integer> counts) {
    }

    private void openList(Player p, Ctx ctx) {
        service.countReports(ctx.status(), ctx.category()).thenCompose(total -> {
            int pg = clampPage(ctx.page(), total);
            return service.listReports(ctx.status(), ctx.category(), pg * PER_PAGE, PER_PAGE).thenCompose(list ->
                    service.countReportsAgainst(list.stream().map(Report::targetUuid).distinct().toList())
                            .thenApply(counts -> new ListData(pg, total, list, counts)));
        }).whenComplete((d, e) -> runSync(() -> {
            if (!fail(p, e) || !p.isOnline()) return;
            Ctx c = new Ctx(ctx.status(), ctx.category(), d.page());
            Holder h = new Holder(Kind.LIST, c, d.page(), null);
            h.reports = d.list();
            Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("menu-staff-list-title"));
            frame(inv, d.page(), d.total());
            inv.setItem(FILTER_A, filterItem(Material.COMPARATOR, "menu-filter-status", ctx.status() == null
                    ? config.msg("filter-all") : config.msg("status-" + ctx.status().name())));
            inv.setItem(FILTER_B, filterItem(Material.HOPPER, "menu-filter-category", ctx.category() == null
                    ? config.msg("filter-all") : config.msg("category-" + ctx.category().name())));
            for (int i = 0; i < d.list().size(); i++) {
                inv.setItem(slotOf(i), listItem(d.list().get(i), d.counts().getOrDefault(d.list().get(i).targetUuid(), 0)));
            }
            p.openInventory(inv);
        }));
    }

    private ItemStack filterItem(Material m, String key, String value) {
        return new FItemStack(m, 1, config.msg(key).replace("{valor}", value))
                .setLore(List.of(config.msg("menu-filter-click"))).createItem();
    }

    private ItemStack listItem(Report r, int count) {
        List<String> lore = new ArrayList<>();
        reportLore(lore, r, count);
        lore.add(config.msg("staff-click-detail"));
        return ReportMenus.head(r.targetUuid(), config.msg("staff-report-name")
                .replace("{id}", String.valueOf(r.id())).replace("{jogador}", r.targetName()), lore);
    }

    private void reportLore(List<String> lore, Report r, int count) {
        lore.add(config.msg("menu-mine-category").replace("{categoria}", config.msg("category-" + r.category().name())));
        Texts.addWrapped(lore, config.msg("menu-mine-reason"), "{motivo}",
                r.reason() == null ? config.msg("menu-confirm-no-reason") : r.reason());
        lore.add(config.msg("menu-mine-status").replace("{estado}", config.msg("status-" + r.status().name())));
        if (r.verdict() != null) {
            lore.add(config.msg("menu-mine-verdict").replace("{veredito}", config.msg("verdict-" + r.verdict().name())));
        }
        lore.add(config.msg("staff-reporter").replace("{nome}", r.reporterName()));
        if (r.claimedByName() != null) lore.add(config.msg("staff-claimed").replace("{nome}", r.claimedByName()));
        lore.add(config.msg("staff-count").replace("{n}", String.valueOf(count)));
        lore.add(config.msg("menu-mine-date").replace("{data}", DATE.format(Instant.ofEpochMilli(r.createdAt()))));
    }

    // ---------- detalhe ----------

    private void openDetail(Player p, long id, Ctx back) {
        service.getReport(id).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(new Object[]{null, List.of(), 0});
            Report r = opt.get();
            return service.listNotes(id).thenCompose(ns -> service.countReportsAgainst(List.of(r.targetUuid()))
                    .thenApply(c -> new Object[]{r, ns, c.getOrDefault(r.targetUuid(), 0)}));
        }).whenComplete((o, e) -> runSync(() -> {
            if (!fail(p, e) || !p.isOnline()) return;
            Report r = (Report) o[0];
            if (r == null) {
                p.sendMessage(config.msg("report-not-found"));
                return;
            }
            @SuppressWarnings("unchecked")
            List<ReportNote> ns = (List<ReportNote>) o[1];
            Holder h = new Holder(Kind.DETAIL, back, 0, null);
            h.reportId = id;
            h.report = r;
            Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("detail-title").replace("{id}", String.valueOf(id)));
            frameOnly(inv);
            inv.setItem(BACK_SLOT, ReportMenus.item(Material.ARROW, config.msg("menu-back")));
            List<String> lore = new ArrayList<>();
            reportLore(lore, r, (Integer) o[2]);
            inv.setItem(D_INFO, ReportMenus.head(r.targetUuid(), config.msg("staff-report-name")
                    .replace("{id}", String.valueOf(id)).replace("{jogador}", r.targetName()), lore));
            inv.setItem(D_CLAIM, ReportMenus.item(Material.NAME_TAG, config.msg("detail-claim")));
            inv.setItem(D_TP, ReportMenus.item(Material.ENDER_PEARL, config.msg("detail-teleport")));
            List<String> nl = new ArrayList<>();
            if (ns.isEmpty()) nl.add(config.msg("detail-notes-none"));
            for (ReportNote n : ns) {
                Texts.addWrapped(nl, config.msg("detail-note-line").replace("{autor}", n.authorName()), "{texto}", n.text());
            }
            nl.add(config.msg("detail-notes-add"));
            inv.setItem(D_NOTES, new FItemStack(Material.WRITABLE_BOOK, 1, config.msg("detail-notes")).setLore(nl).createItem());
            for (int i = 0; i < VERDICTS.length; i++) {
                inv.setItem(D_VERDICT_SLOTS[i], ReportMenus.item(VERDICT_ICONS[i], config.msg("detail-verdict")
                        .replace("{veredito}", config.msg("verdict-" + VERDICTS[i].name()))));
            }
            inv.setItem(D_REJECT, ReportMenus.item(Material.BARRIER, config.msg("detail-reject")));
            p.openInventory(inv);
        }));
    }

    private void detailClick(Player p, Holder h, int slot) {
        long id = h.reportId;
        Ctx back = h.ctx;
        Report rep = h.report;
        if (slot == BACK_SLOT) {
            openList(p, back);
        } else if (slot == D_CLAIM) {
            done(p, service.claim(id, p.getUniqueId(), Texts.truncate(p.getName(), 16)), "report-claimed", id, back);
        } else if (slot == D_TP) {
            Player target = Bukkit.getPlayer(h.report.targetUuid());
            if (target == null) {
                p.sendMessage(config.msg("detail-teleport-offline"));
            } else {
                p.closeInventory();
                p.teleport(target.getLocation());
                p.sendMessage(config.msg("detail-teleported").replace("{jogador}", target.getName()));
            }
        } else if (slot == D_NOTES) {
            p.closeInventory();
            long expires = System.currentTimeMillis() + config.promptSeconds() * 1000L;
            NotePending pending = new NotePending(id, back, expires);
            UUID uuid = p.getUniqueId();
            notes.put(uuid, pending);
            p.sendMessage(config.msg("note-prompt").replace("{n}", String.valueOf(config.promptSeconds())));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!notes.remove(uuid, pending)) return;
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) online.sendMessage(config.msg("note-expired"));
            }, config.promptSeconds() * 20L);
        } else if (slot == D_REJECT) {
            ItemStack subject = ReportMenus.item(Material.BARRIER, config.msg("detail-reject"));
            confirm(p, config.msg("confirm-reject-title"), subject, id, back,
                    x -> done(x, service.reject(id, x.getUniqueId(), Texts.truncate(x.getName(), 16))
                            .thenApply(ok -> notifyAuthor(ok, rep, ReportStatus.REJECTED, null)), "report-rejected", id, back));
        } else {
            for (int i = 0; i < D_VERDICT_SLOTS.length; i++) {
                if (D_VERDICT_SLOTS[i] != slot) continue;
                Verdict v = VERDICTS[i];
                ItemStack subject = ReportMenus.item(VERDICT_ICONS[i], config.msg("detail-verdict")
                        .replace("{veredito}", config.msg("verdict-" + v.name())));
                confirm(p, config.msg("confirm-resolve-title"), subject, id, back,
                        x -> done(x, service.resolve(id, v, x.getUniqueId(), Texts.truncate(x.getName(), 16))
                                .thenApply(ok -> notifyAuthor(ok, rep, ReportStatus.RESOLVED, v)), "report-resolved", id, back));
                return;
            }
        }
    }

    private static Boolean notifyAuthor(Boolean ok, Report r, ReportStatus status, Verdict verdict) {
        if (Boolean.TRUE.equals(ok)) ReportAlerts.finished(r, status, verdict);
        return ok;
    }

    private void confirm(Player p, String title, ItemStack subject, long id, Ctx back, java.util.function.Consumer<Player> yes) {
        ConfirmMenu.open(p, title, subject,
                new ConfirmMenu.Button(Material.LIME_CONCRETE, config.msg("confirm-yes"), yes),
                new ConfirmMenu.Button(Material.RED_CONCRETE, config.msg("confirm-no"), x -> openDetail(x, id, back)),
                new ConfirmMenu.Button(Material.ARROW, config.msg("menu-back"), x -> openDetail(x, id, back)));
    }

    /** Resultado de claim/resolve/reject: false = já tratado por outro; em ambos os casos atualiza o detalhe. */
    private void done(Player p, CompletableFuture<Boolean> result, String okKey, long id, Ctx back) {
        UUID uuid = p.getUniqueId();
        result.whenComplete((ok, e) -> runSync(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            if (e != null) {
                plugin.getLogger().log(Level.WARNING, "falha ao atualizar o report", e);
                online.sendMessage(config.msg("error"));
                return;
            }
            online.sendMessage(config.msg(Boolean.TRUE.equals(ok) ? okKey : "report-already-handled"));
            openDetail(online, id, back);
        }));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        NotePending pending = notes.get(p.getUniqueId());
        if (pending == null || pending.expires() <= System.currentTimeMillis()) return;
        e.setCancelled(true);
        String text = e.getMessage().trim();
        runSync(() -> {
            if (p.isOnline()) handleNote(p, pending, text);
        });
    }

    private void handleNote(Player p, NotePending pending, String text) {
        if (!notes.remove(p.getUniqueId(), pending)) return;
        if (text.isEmpty() || text.equalsIgnoreCase("cancelar")) {
            p.sendMessage(config.msg("note-cancelled"));
            openDetail(p, pending.reportId(), pending.ctx());
            return;
        }
        service.addNote(pending.reportId(), p.getUniqueId(), Texts.truncate(p.getName(), 16), Texts.truncate(text, 512))
                .whenComplete((v, ex) -> runSync(() -> {
                    Player online = Bukkit.getPlayer(p.getUniqueId());
                    if (online == null) return;
                    if (ex != null) {
                        plugin.getLogger().log(Level.WARNING, "falha ao guardar a nota", ex);
                        online.sendMessage(config.msg("error"));
                        return;
                    }
                    online.sendMessage(config.msg("note-added"));
                    openDetail(online, pending.reportId(), pending.ctx());
                }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        notes.remove(e.getPlayer().getUniqueId());
    }

    // ---------- bug reports ----------

    private record BugData(int page, int total, List<BugReport> list) {
    }

    private void openBugList(Player p, BugStatus filter, int page) {
        service.countBugs(filter).thenCompose(total -> {
            int pg = clampPage(page, total);
            return service.listBugs(filter, pg * PER_PAGE, PER_PAGE).thenApply(list -> new BugData(pg, total, list));
        }).whenComplete((d, e) -> runSync(() -> {
            if (!fail(p, e) || !p.isOnline()) return;
            Holder h = new Holder(Kind.BUGS, null, d.page(), filter);
            h.bugs = d.list();
            Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("menu-bugs-title"));
            frame(inv, d.page(), d.total());
            inv.setItem(FILTER_A, filterItem(Material.COMPARATOR, "menu-filter-status", filter == null
                    ? config.msg("filter-all") : config.msg("bugstatus-" + filter.name())));
            for (int i = 0; i < d.list().size(); i++) {
                BugReport b = d.list().get(i);
                List<String> lore = new ArrayList<>();
                Texts.addWrapped(lore, config.msg("menu-mine-reason"), "{motivo}", b.description());
                lore.add(config.msg("bug-status").replace("{estado}", config.msg("bugstatus-" + b.status().name())));
                if (b.handledByName() != null) lore.add(config.msg("bug-handled").replace("{nome}", b.handledByName()));
                lore.add(config.msg("menu-mine-date").replace("{data}", DATE.format(Instant.ofEpochMilli(b.createdAt()))));
                lore.add(config.msg("bug-click"));
                inv.setItem(slotOf(i), new FItemStack(Material.PAPER, 1, config.msg("bug-name")
                        .replace("{id}", String.valueOf(b.id())).replace("{nome}", b.reporterName())).setLore(lore).createItem());
            }
            p.openInventory(inv);
        }));
    }

    private void bugClick(Player p, Holder h, int slot, ClickType click) {
        int i = indexOf(slot);
        if (i < 0 || i >= h.bugs.size()) return;
        if (!click.isLeftClick() && !click.isRightClick()) return;
        BugStatus status = click.isRightClick() ? BugStatus.RESOLVED : BugStatus.SEEN;
        BugReport b = h.bugs.get(i);
        if (status == BugStatus.SEEN && b.status() != BugStatus.OPEN) return;
        service.handleBug(b.id(), status, p.getUniqueId(), Texts.truncate(p.getName(), 16)).whenComplete((v, e) -> runSync(() -> {
            if (!fail(p, e)) return;
            p.sendMessage(config.msg("bug-marked"));
            openBugList(p, h.bugFilter, h.page);
        }));
    }

    // ---------- histórico ----------

    private record HistoryHolderData(UUID uuid, String name) {
    }

    private void openHistoryMenu(Player p, UUID reporterUuid, String reporterName, int page) {
        service.history(reporterUuid).whenComplete((list, e) -> runSync(() -> {
            if (!fail(p, e) || !p.isOnline()) return;
            int pg = clampPage(page, list.size());
            Holder h = new Holder(Kind.HISTORY, null, pg, null);
            h.historyOwner = new HistoryHolderData(reporterUuid, reporterName);
            Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("menu-history-title").replace("{jogador}", reporterName));
            frame(inv, pg, list.size());
            for (int i = 0; pg * PER_PAGE + i < list.size() && i < PER_PAGE; i++) {
                HistoryEntry en = list.get(pg * PER_PAGE + i);
                List<String> lore = List.of(
                        config.msg("history-count").replace("{n}", String.valueOf(en.count())),
                        config.msg("history-last-category").replace("{categoria}", config.msg("category-" + en.lastCategory().name())),
                        config.msg("history-last-date").replace("{data}", DATE.format(Instant.ofEpochMilli(en.lastCreatedAt()))));
                inv.setItem(slotOf(i), ReportMenus.head(en.targetUuid(), "§e" + en.targetName(), lore));
            }
            p.openInventory(inv);
        }));
    }

    // ---------- eventos ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("freports.staff")) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        switch (h.kind) {
            case DETAIL -> detailClick(p, h, slot);
            case LIST -> {
                if (slot == BACK_SLOT) {
                    plugin.menus().open(p);
                } else if (slot == PREV_SLOT && h.page > 0) {
                    openList(p, new Ctx(h.ctx.status(), h.ctx.category(), h.page - 1));
                } else if (slot == NEXT_SLOT && h.inventory.getItem(NEXT_SLOT) != null) {
                    openList(p, new Ctx(h.ctx.status(), h.ctx.category(), h.page + 1));
                } else if (slot == FILTER_A) {
                    openList(p, new Ctx(next(STATUS_CYCLE, h.ctx.status()), h.ctx.category(), 0));
                } else if (slot == FILTER_B) {
                    openList(p, new Ctx(h.ctx.status(), next(CATEGORY_CYCLE, h.ctx.category()), 0));
                } else {
                    int i = indexOf(slot);
                    if (i >= 0 && i < h.reports.size()) openDetail(p, h.reports.get(i).id(), h.ctx);
                }
            }
            case BUGS -> {
                if (slot == BACK_SLOT) plugin.menus().open(p);
                else if (slot == PREV_SLOT && h.page > 0) openBugList(p, h.bugFilter, h.page - 1);
                else if (slot == NEXT_SLOT && h.inventory.getItem(NEXT_SLOT) != null) openBugList(p, h.bugFilter, h.page + 1);
                else if (slot == FILTER_A) openBugList(p, next(BUG_CYCLE, h.bugFilter), 0);
                else bugClick(p, h, slot, e.getClick());
            }
            case HISTORY -> {
                if (slot == BACK_SLOT) p.closeInventory();
                else if (slot == PREV_SLOT && h.page > 0) openHistoryMenu(p, h.historyOwner.uuid(), h.historyOwner.name(), h.page - 1);
                else if (slot == NEXT_SLOT && h.inventory.getItem(NEXT_SLOT) != null) {
                    openHistoryMenu(p, h.historyOwner.uuid(), h.historyOwner.name(), h.page + 1);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ---------- util ----------

    /** Lista com {@code null} (= todos) seguido dos valores. */
    private static <T> List<T> cycle(T[] values) {
        List<T> l = new ArrayList<>();
        l.add(null);
        l.addAll(List.of(values));
        return l;
    }

    private static <T> T next(List<T> cycle, T current) {
        return cycle.get((cycle.indexOf(current) + 1) % cycle.size());
    }

    /** true se não houve erro; caso contrário avisa o jogador. */
    private boolean fail(Player p, Throwable e) {
        if (e == null) return true;
        plugin.getLogger().log(Level.WARNING, "falha nos menus de staff", e);
        if (p.isOnline()) p.sendMessage(config.msg("error"));
        return false;
    }

    private void runSync(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException ex) {
            // plugin a desligar
        }
    }

    private static int clampPage(int page, int total) {
        return Math.max(0, Math.min(page, Math.max(0, (total - 1) / PER_PAGE)));
    }

    private static int slotOf(int index) {
        return 9 * (1 + index / 7) + 1 + index % 7;
    }

    private static int indexOf(int slot) {
        int row = slot / 9, col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return -1;
        return (row - 1) * 7 + col - 1;
    }

    private static void frameOnly(Inventory inv) {
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(slot, ReportMenus.item(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private void frame(Inventory inv, int page, int total) {
        frameOnly(inv);
        inv.setItem(BACK_SLOT, ReportMenus.item(Material.ARROW, config.msg("menu-back")));
        if (page > 0) inv.setItem(PREV_SLOT, ReportMenus.item(Material.ARROW, config.msg("menu-prev")));
        if ((page + 1) * PER_PAGE < total) inv.setItem(NEXT_SLOT, ReportMenus.item(Material.ARROW, config.msg("menu-next")));
    }

    private static Inventory create(Holder holder, FInventorySize size, String title) {
        holder.inventory = new FInventory(holder, size, title).getInventory();
        return holder.inventory;
    }
}
