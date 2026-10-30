package pt.flawless.freports.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.menu.RatingMenu;

import java.util.List;
import java.util.Locale;

/** {@code /avaliarstaff <staff>}: abre o menu de notas. O staff tem de estar online. */
public class RateStaffCommand implements CommandExecutor, TabCompleter {

    private final RatingMenu menu;
    private final ReportsConfig config;

    public RateStaffCommand(RatingMenu menu, ReportsConfig config) {
        this.menu = menu;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(config.msg("usage-avaliarstaff"));
            return true;
        }
        Player staff = Bukkit.getPlayerExact(args[0]);
        if (staff == null || !staff.hasPermission("freports.staff")) {
            p.sendMessage(config.msg("rate-not-staff"));
        } else if (staff.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(config.msg("rate-self"));
        } else {
            menu.open(p, staff);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(o -> o.hasPermission("freports.staff") && !o.equals(sender))
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
