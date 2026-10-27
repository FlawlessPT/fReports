package pt.flawless.freports;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pt.flawless.fapi.enums.FInventorySize;
import pt.flawless.fapi.inventory.ConfirmMenu;
import pt.flawless.fapi.inventory.FInventory;
import pt.flawless.fapi.items.FItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Fluxo de denúncia: menu de categorias, motivo por chat e confirmação. Ligar com {@link #install}; abrir com {@link #start}. */
public class ReportFlow implements Listener {

    private static ReportFlow instance;

    private record Pending(UUID targetUuid, String targetName, Category category, long expires) {
    }

    private static final class CategoryHolder implements InventoryHolder {
        final UUID targetUuid;
        final String targetName;
        Inventory inventory;

        CategoryHolder(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final Category[] CATEGORIES = Category.values();
    /** Uma categoria por slot, ao centro da linha do meio do menu de 27. */
    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};
    private static final Material[] ICONS = {Material.DIAMOND_SWORD, Material.WRITABLE_BOOK, Material.COMMAND_BLOCK,
            Material.TNT, Material.BARRIER, Material.PAPER};

    private final FReports plugin;
    private final ReportsConfig config;
    /** Concorrente: o chat assíncrono consulta-o. */
    private final Map<UUID, Pending> prompts = new ConcurrentHashMap<>();

    private ReportFlow(FReports plugin, ReportsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public static void install(FReports plugin, ReportsConfig config) {
        instance = new ReportFlow(plugin, config);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** Abre o menu de categorias a {@code reporter}; thread principal. */
    public static void start(Player reporter, UUID targetUuid, String targetName) {
        instance.openCategories(reporter, targetUuid, targetName);
    }

    private void openCategories(Player p, UUID targetUuid, String targetName) {
        CategoryHolder holder = new CategoryHolder(targetUuid, targetName);
        FInventory fInv = new FInventory(holder, FInventorySize.SIZE_27,
                config.msg("menu-category-title").replace("{jogador}", targetName));
        holder.inventory = fInv.getInventory();
        for (int i = 0; i < CATEGORIES.length; i++) {
            fInv.setItem(SLOTS[i], new FItemStack(ICONS[i], 1, config.msg("category-" + CATEGORIES[i].name()))
                    .setLore(List.of(config.msg("menu-category-lore"))));
        }
        p.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CategoryHolder holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == e.getRawSlot()) {
                p.closeInventory();
                startPrompt(p, holder.targetUuid, holder.targetName, CATEGORIES[i]);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof CategoryHolder) e.setCancelled(true);
    }

    private void startPrompt(Player p, UUID targetUuid, String targetName, Category category) {
        UUID uuid = p.getUniqueId();
        long expires = System.currentTimeMillis() + config.promptSeconds() * 1000L;
        Pending pending = new Pending(targetUuid, targetName, category, expires);
        prompts.put(uuid, pending);
        p.sendMessage(config.msg("prompt-start").replace("{n}", String.valueOf(config.promptSeconds())));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // só expira este prompt, não um mais recente
            if (!prompts.remove(uuid, pending)) return;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.sendMessage(config.msg("prompt-expired"));
        }, config.promptSeconds() * 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Pending pending = prompts.get(p.getUniqueId());
        if (pending == null || pending.expires() <= System.currentTimeMillis()) return;
        e.setCancelled(true);
        String text = e.getMessage().trim();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) handle(p, pending, text);
            });
        } catch (RuntimeException ignored) {
            // plugin a desligar
        }
    }

    private void handle(Player p, Pending pending, String text) {
        if (!prompts.remove(p.getUniqueId(), pending)) return;
        if (text.equalsIgnoreCase("cancelar")) {
            p.sendMessage(config.msg("prompt-cancelled"));
            return;
        }
        String reason = text.equalsIgnoreCase("saltar") || text.isEmpty() ? null : truncate(text, 256);
        confirm(p, pending, reason);
    }

    private void confirm(Player p, Pending pending, String reason) {
        String category = config.msg("category-" + pending.category().name());
        List<String> lore = new java.util.ArrayList<>();
        lore.add(config.msg("menu-confirm-category").replace("{categoria}", category));
        Texts.addWrapped(lore, config.msg("menu-confirm-reason"), "{motivo}",
                reason == null ? config.msg("menu-confirm-no-reason") : reason);
        FItemStack subject = new FItemStack(Material.PAPER, 1, config.msg("menu-confirm-subject").replace("{jogador}", pending.targetName()))
                .setLore(lore);
        ConfirmMenu.open(p, config.msg("menu-confirm-title"), subject.createItem(),
                new ConfirmMenu.Button(Material.LIME_CONCRETE, config.msg("menu-confirm-yes"), x -> send(x, pending, reason)),
                new ConfirmMenu.Button(Material.RED_CONCRETE, config.msg("menu-confirm-no"),
                        x -> x.sendMessage(config.msg("prompt-cancelled"))));
    }

    private void send(Player p, Pending pending, String reason) {
        UUID uuid = p.getUniqueId();
        plugin.service().createReport(uuid, truncate(p.getName(), 16), pending.targetUuid(),
                truncate(pending.targetName(), 16), pending.category(), reason)
                .whenComplete((id, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ex == null) ReportAlerts.newReport(id, truncate(p.getName(), 16), truncate(pending.targetName(), 16));
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null) return;
                    if (ex != null) {
                        plugin.getLogger().log(Level.WARNING, "falha ao criar report", ex);
                        online.sendMessage(config.msg("error"));
                    } else {
                        online.sendMessage(config.msg("report-sent"));
                    }
                }));
    }

    static String truncate(String s, int max) {
        return Texts.truncate(s, max);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
    }
}
