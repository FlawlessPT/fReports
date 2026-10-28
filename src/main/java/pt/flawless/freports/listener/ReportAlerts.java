package pt.flawless.freports.listener;

import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.data.ReportsService;
import pt.flawless.freports.FReports;
import pt.flawless.freports.model.Report;
import pt.flawless.freports.model.ReportStatus;
import pt.flawless.freports.model.Verdict;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pt.flawless.fapi.events.PlayerAuthEvent.PlayerAuthEvent;

import java.util.UUID;
import java.util.logging.Level;

/** Avisos: report novo à staff, pendentes ao entrar e resultado ao autor. */
public final class ReportAlerts implements Listener {

    private static ReportAlerts instance;

    private final FReports plugin;
    private final ReportsService service;
    private final ReportsConfig config;

    private ReportAlerts(FReports plugin, ReportsService service, ReportsConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    public static void install(FReports plugin, ReportsService service, ReportsConfig config) {
        instance = new ReportAlerts(plugin, service, config);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** Avisa a staff online de um report novo, com clique para o abrir. Thread principal. */
    static void newReport(long id, String reporterName, String targetName) {
        String text = instance.config.msg("alert-new")
                .replace("{id}", String.valueOf(id)).replace("{nome}", reporterName).replace("{jogador}", targetName);
        Component msg = LegacyComponentSerializer.legacySection().deserialize(text)
                .clickEvent(ClickEvent.runCommand("/reports ver " + id));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("freports.staff")) p.sendMessage(msg);
        }
    }

    /** Report tratado: avisa o autor se estiver online e marca como notificado. Qualquer thread. */
    public static void finished(Report r, ReportStatus status, Verdict verdict) {
        instance.runSync(() -> {
            Player p = Bukkit.getPlayer(r.reporterUuid());
            if (p == null) return;
            instance.tell(p, r.id(), r.targetName(), status, verdict);
        });
    }

    private void tell(Player p, long id, String target, ReportStatus status, Verdict verdict) {
        p.sendMessage(config.msg("notify-finished").replace("{id}", String.valueOf(id)).replace("{jogador}", target)
                .replace("{resultado}", verdict != null ? config.msg("verdict-" + verdict.name()) : config.msg("status-" + status.name())));
        service.markNotified(id).exceptionally(e -> {
            plugin.getLogger().log(Level.WARNING, "falha ao marcar o report como notificado", e);
            return null;
        });
    }

    @EventHandler
    public void onAuth(PlayerAuthEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (p.hasPermission("freports.staff")) {
            service.countPending().thenAccept(n -> runSync(() -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && n > 0) online.sendMessage(config.msg("alert-pending").replace("{n}", String.valueOf(n)));
            })).exceptionally(this::warn);
        }
        service.listUnnotifiedFinished(uuid).thenAccept(list -> runSync(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            for (Report r : list) tell(online, r.id(), r.targetName(), r.status(), r.verdict());
        })).exceptionally(this::warn);
    }

    private Void warn(Throwable t) {
        plugin.getLogger().log(Level.WARNING, "falha nos avisos de reports", t);
        return null;
    }

    private void runSync(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException ex) {
            // plugin a desligar
        }
    }
}
