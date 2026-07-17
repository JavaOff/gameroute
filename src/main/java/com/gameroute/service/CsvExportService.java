package com.gameroute.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies the persisted ping-history CSV to a location the user picks
 * (e.g. via a JavaFX {@code FileChooser} save dialog in the Statistics tab).
 */
public class CsvExportService {

    private static final Logger log = LoggerFactory.getLogger(CsvExportService.class);

    private final StatisticsService statisticsService;

    public CsvExportService(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    public boolean exportTo(Path destination) {
        try {
            Path source = statisticsService.historyFilePath();
            if (!Files.exists(source)) {
                log.warn("No ping history to export yet");
                return false;
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("CSV export failed", e);
            return false;
        }
    }
}
