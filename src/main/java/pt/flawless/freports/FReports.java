package pt.flawless.freports;

import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.database.DatabaseService;
import pt.flawless.fapi.database.Databases;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;

import java.sql.SQLException;
import java.util.List;

public class FReports extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());
    private boolean enabledOk = false;
    private ReportsService service;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            DatabaseService database = Databases.get();
            database.migrate(this, List.of("db/freports/V001__create_reports.sql"));
            service = new ReportsService(new SqlReportsRepository(database));
            ReportsConfig config = ReportsConfig.from(getConfig());
            ReportFlow.install(this, config);
            ReportCommand report = new ReportCommand(config);
            getCommand("report").setExecutor(report);
            getCommand("report").setTabCompleter(report);
            getCommand("bugreport").setExecutor(new BugReportCommand(this, config));
        } catch (SQLException | RuntimeException e) {
            // inclui DatabaseUnavailableException
            consoleLogger.sendMessage("não foi possível iniciar os reports: " + e.getMessage(), EMessageTypeEnum.ERROR);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        enabledOk = true;
        consoleLogger.sendEnablePluginMessage();
    }

    public ReportsService service() {
        return service;
    }

    @Override
    public void onDisable() {
        if (enabledOk) consoleLogger.sendDisablePluginMessage();
    }
}
