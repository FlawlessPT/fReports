package pt.flawless.freports.command;

import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.menu.ReportMenus;
import pt.flawless.freports.menu.StaffMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /reports}: abre o menu de membro ou o hub de staff. */
public class ReportsCommand implements CommandExecutor {

    private final ReportMenus menus;
    private final ReportsConfig config;

    public ReportsCommand(ReportMenus menus, ReportsConfig config) {
        this.menus = menus;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            // interno: alvo do clique no alerta de report novo (staff)
            if (args.length == 2 && args[0].equalsIgnoreCase("ver") && p.hasPermission("freports.staff")) {
                try {
                    StaffMenus.openReport(p, Long.parseLong(args[1]));
                    return true;
                } catch (NumberFormatException ignored) {
                    // cai no menu normal
                }
            }
            menus.open(p);
        } else sender.sendMessage(config.msg("players-only"));
        return true;
    }
}
