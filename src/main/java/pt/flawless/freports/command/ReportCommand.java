package pt.flawless.freports.command;

import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.listener.ReportFlow;
import pt.flawless.freports.menu.StaffMenus;
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
            if (!p.hasPermission(STAFF)) {
                Messages.noPermission(p);
            } else if (args.length < 2) {
                p.sendMessage(config.msg("usage-historico"));
            } else {
                Object[] t = resolve(args[1]);
                if (t == null) p.sendMessage(config.msg("player-not-found"));
                else StaffMenus.openHistory(p, (UUID) t[0], (String) t[1]);
            }
            return true;
        }
        Object[] target = resolve(args[0]);
        if (target == null) {
            p.sendMessage(config.msg("player-not-found"));
            return true;
        }
        UUID uuid = (UUID) target[0];
        String name = (String) target[1];
        if (uuid.equals(p.getUniqueId())) {
            p.sendMessage(config.msg("report-self"));
            return true;
        }
        ReportFlow.start(p, uuid, name);
        return true;
    }

    /** {uuid, nome} de um jogador online ou já conhecido; null se não existir. */
    private static Object[] resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return new Object[]{online.getUniqueId(), online.getName()};
        // getOfflinePlayerIfCached não bloqueia
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(input);
        if (off == null || !off.hasPlayedBefore() || off.getName() == null) return null;
        return new Object[]{off.getUniqueId(), off.getName()};
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && sender.hasPermission(STAFF) && args[0].equalsIgnoreCase("historico")) {
            String p2 = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p2)).toList();
        }
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(List.of("help"));
        if (sender.hasPermission(STAFF)) out.add("historico");
        Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
