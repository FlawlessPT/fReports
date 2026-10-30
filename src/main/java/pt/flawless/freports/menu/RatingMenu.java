package pt.flawless.freports.menu;

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
import org.bukkit.profile.PlayerProfile;
import pt.flawless.fapi.enums.FInventorySize;
import pt.flawless.fapi.inventory.FInventory;
import pt.flawless.fapi.items.FItemStack;
import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.data.ReportsService;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Menu do {@code /avaliarstaff}: dez cabeças, uma por nota de 1 a 10. */
public class RatingMenu implements Listener {

    // duas filas de 5, centradas
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private static final class Holder implements InventoryHolder {
        final UUID staffUuid;
        final String staffName;
        Inventory inventory;

        Holder(UUID staffUuid, String staffName) {
            this.staffUuid = staffUuid;
            this.staffName = staffName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final ReportsService service;
    private final ReportsConfig config;

    public RatingMenu(Plugin plugin, ReportsService service, ReportsConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    /** Thread principal. */
    public void open(Player p, Player staff) {
        Holder h = new Holder(staff.getUniqueId(), staff.getName());
        h.inventory = new FInventory(h, FInventorySize.SIZE_36,
                config.msg("rate-title").replace("{staff}", staff.getName())).getInventory();
        for (int i = 0; i < SLOTS.length; i++) h.inventory.setItem(SLOTS[i], scoreHead(i + 1, staff.getName()));
        p.openInventory(h.inventory);
    }

    /** Cabeça com a textura configurada; sem ela, cabeça normal com o número como quantidade. */
    private ItemStack scoreHead(int score, String staff) {
        String n = String.valueOf(score);
        ItemStack head = new FItemStack(Material.PLAYER_HEAD, score, config.msg("rate-item").replace("{nota}", n))
                .setLore(List.of(config.msg("rate-item-lore").replace("{nota}", n).replace("{staff}", staff))).createItem();
        String url = plugin.getConfig().getString("rating-heads." + score, "");
        if (url.isBlank()) return head;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(("freports-rating-" + score).getBytes()), "nota" + score);
            profile.getTextures().setSkin(URI.create(url.trim()).toURL());
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            head.setAmount(1);
            head.setData(io.papermc.paper.datacomponent.DataComponentTypes.TOOLTIP_DISPLAY,
                    io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay()
                            .addHiddenComponents(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE).build());
        } catch (MalformedURLException | IllegalArgumentException ex) {
            plugin.getLogger().warning("rating-heads." + score + " inválido: " + url);
        }
        return head;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize() || !(e.getWhoClicked() instanceof Player p)) return;
        int score = 0;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == e.getRawSlot()) score = i + 1;
        if (score == 0) return;
        int chosen = score;
        p.closeInventory();
        service.rateStaff(h.staffUuid, h.staffName, p.getUniqueId(), p.getName(), chosen).whenComplete((saved, ex) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ex != null) {
                        plugin.getLogger().log(Level.WARNING, "falha ao guardar a avaliação", ex);
                        p.sendMessage(config.msg("error"));
                    } else if (!saved) {
                        p.sendMessage(config.msg("rate-cooldown").replace("{staff}", h.staffName));
                    } else {
                        p.sendMessage(config.msg("rate-done")
                                .replace("{nota}", String.valueOf(chosen)).replace("{staff}", h.staffName));
                    }
                });
            } catch (RuntimeException ignored) {
                // plugin a desligar
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
