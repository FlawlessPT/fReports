package pt.flawless.freports;

import pt.flawless.freports.command.BugReportCommand;
import pt.flawless.freports.command.ReportCommand;
import pt.flawless.freports.command.ReportsCommand;
import pt.flawless.freports.config.ReportsConfig;
import pt.flawless.freports.data.ReportsService;
import pt.flawless.freports.data.SqlReportsRepository;
import pt.flawless.freports.listener.ReportAlerts;
import pt.flawless.freports.listener.ReportFlow;
import pt.flawless.freports.menu.ReportMenus;
import pt.flawless.freports.menu.StaffMenus;
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
    private ReportMenus menus;

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
            StaffMenus.install(this, service, config);
            ReportAlerts.install(this, service, config);
            menus = new ReportMenus(this, service, config);
            getServer().getPluginManager().registerEvents(menus, this);
            getCommand("reports").setExecutor(new ReportsCommand(menus, config));
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

    public ReportMenus menus() {
        return menus;
    }

    public ReportsService service() {
        return service;
    }

    @Override
    public void onDisable() {
        if (enabledOk) consoleLogger.sendDisablePluginMessage();
    }
}
