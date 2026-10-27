package pt.flawless.freports;

import org.bukkit.entity.Player;

/** Menus de staff. Task 5 completa {@link #openReports} e {@link #openBugs}; o hub chama-os. */
public final class StaffMenus {

    private static ReportsConfig config;

    private StaffMenus() {
    }

    static void install(ReportsConfig cfg) {
        config = cfg;
    }

    /** Fila de reports para analisar; stub da Task 5. */
    public static void openReports(Player p) {
        p.sendMessage(config.msg("coming-soon"));
    }

    /** Lista de bug reports; stub da Task 5. */
    public static void openBugs(Player p) {
        p.sendMessage(config.msg("coming-soon"));
    }
}
