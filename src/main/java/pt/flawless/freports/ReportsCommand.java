package pt.flawless.freports;

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
        if (sender instanceof Player p) menus.open(p);
        else sender.sendMessage(config.msg("players-only"));
        return true;
    }
}
