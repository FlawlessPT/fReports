package pt.flawless.freports;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pt.flawless.fapi.messages.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /report <jogador>}, {@code /report help} e (Task 5) {@code /report historico <jogador>}. */
public class ReportCommand implements CommandExecutor, TabCompleter {

    private static final String STAFF = "freports.staff";

    private final ReportsConfig config;

    public ReportCommand(ReportsConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(config.msg("help-report"));
            if (sender.hasPermission(STAFF)) sender.sendMessage(config.msg("help-historico"));
            sender.sendMessage(config.msg("help-bugreport"));
            sender.sendMessage(config.msg("help-reports"));
            sender.sendMessage(config.msg("help-help"));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(config.msg("usage-report"));
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("historico") || first.equals("history")) {
            if (!p.hasPermission(STAFF)) Messages.noPermission(p);
            else p.sendMessage(config.msg("coming-soon"));
            return true;
        }
        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            // só jogadores já conhecidos do servidor; getOfflinePlayerIfCached não bloqueia
            OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (off == null || !off.hasPlayedBefore() || off.getName() == null) {
                p.sendMessage(config.msg("player-not-found"));
                return true;
            }
            uuid = off.getUniqueId();
            name = off.getName();
        }
        if (uuid.equals(p.getUniqueId())) {
            p.sendMessage(config.msg("report-self"));
            return true;
        }
        ReportFlow.start(p, uuid, name);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(List.of("help"));
        if (sender.hasPermission(STAFF)) out.add("historico");
        Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
