package pt.flawless.freports.command;

import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.FReports;
import pt.flawless.freports.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/** {@code /bugreport <descrição>}. */
public class BugReportCommand implements CommandExecutor {

    private final FReports plugin;
    private final ReportsConfig config;

    public BugReportCommand(FReports plugin, ReportsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }
        String text = String.join(" ", args).trim();
        if (text.isEmpty()) {
            p.sendMessage(config.msg("usage-bugreport"));
            return true;
        }
        UUID uuid = p.getUniqueId();
        plugin.service().createBug(uuid, Texts.truncate(p.getName(), 16), Texts.truncate(text, 512))
                .whenComplete((id, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null) return;
                    if (ex != null) {
                        plugin.getLogger().log(Level.WARNING, "falha ao criar bug", ex);
                        online.sendMessage(config.msg("error"));
                    } else {
                        online.sendMessage(config.msg("bug-sent"));
                    }
                }));
        return true;
    }
}
