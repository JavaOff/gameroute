package com.gameroute.service;

import com.gameroute.config.Constants;
import com.gameroute.model.DailyStatistics;
import com.gameroute.model.PingSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists every ping sample to a local CSV file
 * ({@code ~/.gameroute/stats/ping-history.csv}) and derives daily/weekly
 * aggregates from it for the Statistics tab. A flat CSV keeps the format
 * transparent and trivially exportable/inspectable by the user.
 */
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    private static final String HEADER = "timestamp,rtt_ms,success";

    private BufferedWriter writer;

    public StatisticsService() {
        try {
            Files.createDirectories(Constants.STATS_DIR);
            boolean isNew = !Files.exists(Constants.PING_HISTORY_CSV);
            writer = Files.newBufferedWriter(Constants.PING_HISTORY_CSV,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (isNew) {
                writer.write(HEADER);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.error("Could not open ping history file for writing", e);
        }
    }

    public synchronized void record(PingSample sample) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(sample.timestamp().toString());
            writer.write(",");
            writer.write(sample.success() ? String.valueOf(sample.rttMillis()) : "");
            writer.write(",");
            writer.write(sample.success() ? "1" : "0");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.warn("Could not append ping sample to history file", e);
        }
    }

    public List<DailyStatistics> dailyStatistics(int lastNDays) {
        List<CsvRow> rows = readAll();
        LocalDate cutoff = LocalDate.now().minusDays(lastNDays);
        Map<LocalDate, List<CsvRow>> byDay = new LinkedHashMap<>();
        for (CsvRow row : rows) {
            LocalDate day = row.timestamp.atZone(ZoneId.systemDefault()).toLocalDate();
            if (day.isBefore(cutoff)) {
                continue;
            }
            byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(row);
        }
        List<DailyStatistics> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CsvRow>> entry : byDay.entrySet()) {
            result.add(aggregate(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public List<DailyStatistics> weeklyStatistics() {
        return dailyStatistics(7);
    }

    private DailyStatistics aggregate(LocalDate day, List<CsvRow> rows) {
        List<Double> successfulRtts = rows.stream().filter(r -> r.success).map(r -> r.rtt).toList();
        double avg = successfulRtts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = successfulRtts.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = successfulRtts.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double jitter = jitterOf(successfulRtts);
        double lossPercent = rows.isEmpty() ? 0 : 100.0 * (rows.size() - successfulRtts.size()) / rows.size();
        return new DailyStatistics(day, avg, min, max, jitter, lossPercent, rows.size());
    }

    private double jitterOf(List<Double> rtts) {
        if (rtts.size() < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 1; i < rtts.size(); i++) {
            sum += Math.abs(rtts.get(i) - rtts.get(i - 1));
        }
        return sum / (rtts.size() - 1);
    }

    private record CsvRow(Instant timestamp, double rtt, boolean success) {
    }

    private List<CsvRow> readAll() {
        List<CsvRow> rows = new ArrayList<>();
        if (!Files.exists(Constants.PING_HISTORY_CSV)) {
            return rows;
        }
        try {
            List<String> lines = Files.readAllLines(Constants.PING_HISTORY_CSV);
            for (int i = 1; i < lines.size(); i++) { // skip header
                String[] parts = lines.get(i).split(",", -1);
                if (parts.length < 3) {
                    continue;
                }
                try {
                    Instant ts = Instant.parse(parts[0]);
                    boolean success = "1".equals(parts[2]);
                    double rtt = success && !parts[1].isBlank() ? Double.parseDouble(parts[1]) : -1;
                    rows.add(new CsvRow(ts, rtt, success));
                } catch (Exception e) {
                    log.debug("Skipping malformed CSV row: {}", lines.get(i));
                }
            }
        } catch (IOException e) {
            log.warn("Could not read ping history file", e);
        }
        return rows;
    }

    public Path historyFilePath() {
        return Constants.PING_HISTORY_CSV;
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            log.warn("Error closing ping history file", e);
        }
    }
}
