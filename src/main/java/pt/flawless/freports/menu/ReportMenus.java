package pt.flawless.freports.menu;

import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.data.ReportsService;
import pt.flawless.freports.listener.ReportFlow;
import pt.flawless.freports.model.Report;
import pt.flawless.freports.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import pt.flawless.fapi.enums.FInventorySize;
import pt.flawless.fapi.inventory.FInventory;
import pt.flawless.fapi.items.FItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/** Menus de entrada do {@code /reports}: membro, hub de staff, escolha de jogador e "os meus reports". */
public class ReportMenus implements Listener {

    private static final String STAFF = "freports.staff";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final int PER_PAGE = 28;
    private static final int PREV_SLOT = 48, BACK_SLOT = 49, NEXT_SLOT = 50;
    // menus de 27: botões na linha do meio
    private static final int LEFT = 11, MIDDLE = 13, RIGHT = 15, INFO = 22;

    private enum Kind { MEMBER, HUB, PICKER, MINE }

    private static final class Holder implements InventoryHolder {
        final Kind kind;
        final int page;
        /** Alvos do seletor (por posição na página). */
        final List<Player> players;
        Inventory inventory;

        Holder(Kind kind, int page, List<Player> players) {
            this.kind = kind;
            this.page = page;
            this.players = players;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final ReportsService service;
    private final ReportsConfig config;

    public ReportMenus(Plugin plugin, ReportsService service, ReportsConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    /** Menu de entrada conforme a permissão: hub de staff ou menu de membro. Thread principal. */
    public void open(Player p) {
        boolean staff = p.hasPermission(STAFF);
        Holder h = new Holder(staff ? Kind.HUB : Kind.MEMBER, 0, List.of());
        Inventory inv = create(h, FInventorySize.SIZE_27, config.msg(staff ? "menu-hub-title" : "menu-member-title"));
        if (staff) {
            inv.setItem(LEFT, item(Material.WRITABLE_BOOK, config.msg("menu-analyze"), config.msg("menu-analyze-lore")));
            inv.setItem(MIDDLE, item(Material.BEETROOT_SEEDS, config.msg("menu-bugs"), config.msg("menu-bugs-lore")));
            inv.setItem(RIGHT, item(Material.BOOK, config.msg("menu-mine"), config.msg("menu-mine-lore")));
        } else {
            inv.setItem(LEFT, item(Material.PLAYER_HEAD, config.msg("menu-report-player"), config.msg("menu-report-player-lore")));
            inv.setItem(RIGHT, item(Material.BOOK, config.msg("menu-mine"), config.msg("menu-mine-lore")));
            inv.setItem(INFO, new FItemStack(Material.OAK_SIGN, 1, config.msg("menu-info"))
                    .setLore(List.of(config.msg("menu-info-1"), config.msg("menu-info-2"))).createItem());
        }
        p.openInventory(inv);
    }

    private void openPicker(Player p, int page) {
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        all.removeIf(o -> o.getUniqueId().equals(p.getUniqueId()));
        all.sort(Comparator.comparing(o -> o.getName().toLowerCase(Locale.ROOT)));
        int pg = clampPage(page, all.size());
        List<Player> shown = all.subList(pg * PER_PAGE, Math.min(all.size(), (pg + 1) * PER_PAGE));
        Holder h = new Holder(Kind.PICKER, pg, List.copyOf(shown));
        Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("menu-picker-title"));
        frame(inv, pg, all.size());
        for (int i = 0; i < shown.size(); i++) {
            inv.setItem(slotOf(i), head(shown.get(i).getUniqueId(), "§e" + shown.get(i).getName(),
                    List.of(config.msg("menu-picker-lore"))));
        }
        p.openInventory(inv);
    }

    private void openMine(Player p, int page) {
        service.listByReporter(p.getUniqueId()).whenComplete((list, e) -> runSync(() -> {
            if (e != null) {
                plugin.getLogger().log(Level.WARNING, "falha ao listar os reports", e);
                p.sendMessage(config.msg("error"));
                return;
            }
            if (!p.isOnline()) return;
            int pg = clampPage(page, list.size());
            Holder h = new Holder(Kind.MINE, pg, List.of());
            Inventory inv = create(h, FInventorySize.SIZE_54, config.msg("menu-mine-title"));
            frame(inv, pg, list.size());
            int from = pg * PER_PAGE;
            for (int i = 0; from + i < list.size() && i < PER_PAGE; i++) {
                inv.setItem(slotOf(i), reportItem(list.get(from + i)));
            }
            p.openInventory(inv);
        }));
    }

    private ItemStack reportItem(Report r) {
        List<String> lore = new ArrayList<>();
        lore.add(config.msg("menu-mine-category").replace("{categoria}", config.msg("category-" + r.category().name())));
        Texts.addWrapped(lore, config.msg("menu-mine-reason"), "{motivo}",
                r.reason() == null ? config.msg("menu-confirm-no-reason") : r.reason());
        lore.add(config.msg("menu-mine-status").replace("{estado}", config.msg("status-" + r.status().name())));
        if (r.verdict() != null) {
            lore.add(config.msg("menu-mine-verdict").replace("{veredito}", config.msg("verdict-" + r.verdict().name())));
        }
        lore.add(config.msg("menu-mine-date").replace("{data}", DATE.format(Instant.ofEpochMilli(r.createdAt()))));
        return head(r.targetUuid(), "§e" + r.targetName(), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        switch (h.kind) {
            case MEMBER -> {
                if (slot == LEFT) openPicker(p, 0);
                else if (slot == RIGHT) openMine(p, 0);
            }
            case HUB -> {
                if (slot == LEFT) StaffMenus.openReports(p);
                else if (slot == MIDDLE) StaffMenus.openBugs(p);
                else if (slot == RIGHT) openMine(p, 0);
            }
            case PICKER, MINE -> {
                boolean picker = h.kind == Kind.PICKER;
                if (slot == BACK_SLOT) open(p);
                else if (slot == PREV_SLOT && h.page > 0) page(p, picker, h.page - 1);
                else if (slot == NEXT_SLOT && h.inventory.getItem(NEXT_SLOT) != null && h.inventory.getItem(NEXT_SLOT).getType() == org.bukkit.Material.ARROW) page(p, picker, h.page + 1);
                else if (picker) {
                    int i = indexOf(slot);
                    if (i >= 0 && i < h.players.size()) {
                        Player target = h.players.get(i);
                        if (target.isOnline()) ReportFlow.start(p, target.getUniqueId(), target.getName());
                        else p.sendMessage(config.msg("player-not-found"));
                    }
                }
            }
        }
    }

    private void page(Player p, boolean picker, int page) {
        if (picker) openPicker(p, page);
        else openMine(p, page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
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

    /** Interior 4x7: linhas 1-4, colunas 1-7. */
    private static int slotOf(int index) {
        return 9 * (1 + index / 7) + 1 + index % 7;
    }

    private static int indexOf(int slot) {
        int row = slot / 9, col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return -1;
        return (row - 1) * 7 + col - 1;
    }

    /** Moldura de vidro, botão de voltar e setas de página quando há mais páginas. */
    private void frame(Inventory inv, int page, int total) {
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, config.msg("menu-back")));
        if (page > 0) inv.setItem(PREV_SLOT, item(Material.ARROW, config.msg("menu-prev")));
        if ((page + 1) * PER_PAGE < total) inv.setItem(NEXT_SLOT, item(Material.ARROW, config.msg("menu-next")));
    }

    private static Inventory create(Holder holder, FInventorySize size, String title) {
        holder.inventory = new FInventory(holder, size, title).getInventory();
        return holder.inventory;
    }

    static ItemStack item(Material material, String name) {
        return new FItemStack(material, 1, name).createItem();
    }

    static ItemStack item(Material material, String name, String lore) {
        return new FItemStack(material, 1, name).setLore(List.of(lore)).createItem();
    }

    /** Cabeça do jogador; o dono vem do UUID (sem lookup por nome). */
    static ItemStack head(UUID uuid, String name, List<String> lore) {
        ItemStack head = new FItemStack(Material.PLAYER_HEAD, 1, name).setLore(lore).createItem();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        head.setItemMeta(meta);
        head.setData(io.papermc.paper.datacomponent.DataComponentTypes.TOOLTIP_DISPLAY,
                io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay()
                        .addHiddenComponents(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE).build());
        return head;
    }
}
