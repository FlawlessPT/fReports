package pt.flawless.freports;

import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.database.DatabaseService;
import pt.flawless.fapi.database.Databases;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;

public class FReports extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private boolean enabledOk = false;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            DatabaseService database = Databases.get();
        } catch (RuntimeException e) {
            // inclui DatabaseUnavailableException
            consoleLogger.sendMessage("não foi possível iniciar os reports: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        enabledOk = true;
        consoleLogger.sendEnablePluginMessage();
    }

    @Override
    public void onDisable() {
        if (enabledOk) consoleLogger.sendDisablePluginMessage();
    }
}
