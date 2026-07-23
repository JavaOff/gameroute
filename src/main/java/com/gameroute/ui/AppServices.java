package com.gameroute.ui;

import com.gameroute.config.AppConfig;
import com.gameroute.monitor.GameProcessMonitor;
import com.gameroute.monitor.PingMonitor;
import com.gameroute.monitor.ProcessDiagnosticsMonitor;
import com.gameroute.monitor.SystemMonitor;
import com.gameroute.network.DnsService;
import com.gameroute.network.NetworkInterfaceService;
import com.gameroute.network.PingService;
import com.gameroute.network.PublicIpService;
import com.gameroute.network.QosService;
import com.gameroute.network.RouteAnalyzer;
import com.gameroute.network.TracerouteService;
import com.gameroute.optimizer.OptimizerService;
import com.gameroute.service.AutoStartService;
import com.gameroute.service.BugReportService;
import com.gameroute.service.CsvExportService;
import com.gameroute.service.DesktopShortcutScanner;
import com.gameroute.service.DiscordAccountService;
import com.gameroute.service.DiscordIdentitySharingService;
import com.gameroute.service.DiscordPresenceService;
import com.gameroute.service.GameIconStore;
import com.gameroute.service.GameServerPingService;
import com.gameroute.service.NotificationCenter;
import com.gameroute.service.NotificationService;
import com.gameroute.service.StatisticsService;
import com.gameroute.service.TelemetryService;
import com.gameroute.service.UpdateService;

/**
 * Bundle of every backend service/monitor the UI tabs need, assembled once
 * in {@link com.gameroute.Main} and threaded through {@link MainView}. A
 * lightweight stand-in for a DI container, appropriate for an app this size.
 */
public record AppServices(
        AppConfig config,
        PingService pingService,
        TracerouteService tracerouteService,
        DnsService dnsService,
        NetworkInterfaceService networkInterfaceService,
        QosService qosService,
        RouteAnalyzer routeAnalyzer,
        OptimizerService optimizerService,
        AutoStartService autoStartService,
        GameProcessMonitor gameProcessMonitor,
        SystemMonitor systemMonitor,
        PingMonitor pingMonitor,
        StatisticsService statisticsService,
        CsvExportService csvExportService,
        NotificationService notificationService,
        NotificationCenter notificationCenter,
        ProcessDiagnosticsMonitor processDiagnosticsMonitor,
        PublicIpService publicIpService,
        GameIconStore gameIconStore,
        DesktopShortcutScanner desktopShortcutScanner,
        UpdateService updateService,
        TelemetryService telemetryService,
        DiscordPresenceService discordPresenceService,
        DiscordAccountService discordAccountService,
        DiscordIdentitySharingService discordIdentitySharingService,
        GameServerPingService gameServerPingService,
        BugReportService bugReportService
) {
}
