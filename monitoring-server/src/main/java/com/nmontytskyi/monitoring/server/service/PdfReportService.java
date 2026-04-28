package com.nmontytskyi.monitoring.server.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity;
import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity.ReportType;
import com.nmontytskyi.monitoring.server.exception.ReportGenerationException;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.repository.ReportHistoryRepository;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private static final Font FONT_TITLE = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_HEADER = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_CELL = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.DARK_GRAY);
    private static final Font FONT_CELL_GREEN = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(34, 197, 94));
    private static final Font FONT_CELL_RED = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(239, 68, 68));
    private static final Font FONT_SECTION = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(30, 58, 95));

    private static final BaseColor DARK_HEADER = new BaseColor(30, 58, 95);
    private static final BaseColor ROW_LIGHT = BaseColor.WHITE;
    private static final BaseColor ROW_DARK = new BaseColor(241, 245, 249);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RegisteredServiceRepository serviceRepository;
    private final MetricRecordRepository metricRecordRepository;
    private final AlertEventRepository alertEventRepository;
    private final ReportHistoryRepository reportHistoryRepository;
    private final SlaCalculationService slaCalculationService;

    @Transactional
    public byte[] generateSlaReport(Long serviceId, LocalDate from, LocalDate to) {
        RegisteredServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        List<MetricRecordEntity> records = metricRecordRepository
                .findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(serviceId, fromDt, toDt);

        if (records.isEmpty()) {
            throw new ReportGenerationException(
                    "No metric data found for service " + service.getName() + " in period " + from + " — " + to);
        }

        com.nmontytskyi.monitoring.model.SlaReport slaReport = slaCalculationService.calculate(serviceId, SlaWindow.MONTH);

        byte[] pdf = buildSlaDocument(service, slaReport, records, from, to);
        saveHistory(service, ReportType.SLA_SUMMARY, fromDt, toDt, pdf.length);
        return pdf;
    }

    @Transactional
    public byte[] generateFullReport(Long serviceId, LocalDate from, LocalDate to) {
        RegisteredServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        List<MetricRecordEntity> records = metricRecordRepository
                .findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(serviceId, fromDt, toDt);

        if (records.isEmpty()) {
            throw new ReportGenerationException(
                    "No metric data found for service " + service.getName() + " in period " + from + " — " + to);
        }

        com.nmontytskyi.monitoring.model.SlaReport slaReport = slaCalculationService.calculate(serviceId, SlaWindow.MONTH);

        byte[] pdf = buildFullDocument(service, slaReport, records, from, to);
        saveHistory(service, ReportType.FULL, fromDt, toDt, pdf.length);
        return pdf;
    }

    @Transactional
    public byte[] generateSystemReport(LocalDate from, LocalDate to) {
        List<RegisteredServiceEntity> services = serviceRepository.findAll();
        if (services.isEmpty()) {
            throw new ReportGenerationException("No services registered in the system");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        Map<RegisteredServiceEntity, List<MetricRecordEntity>> recordsMap = new LinkedHashMap<>();
        for (RegisteredServiceEntity svc : services) {
            recordsMap.put(svc, metricRecordRepository
                    .findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(svc.getId(), fromDt, toDt));
        }

        Map<Long, com.nmontytskyi.monitoring.model.SlaReport> slaReports = new HashMap<>();
        for (RegisteredServiceEntity svc : services) {
            if (!recordsMap.get(svc).isEmpty()) {
                try {
                    slaReports.put(svc.getId(), slaCalculationService.calculate(svc.getId(), SlaWindow.MONTH));
                } catch (Exception e) {
                    log.warn("SLA calculation skipped for service {}: {}", svc.getName(), e.getMessage());
                }
            }
        }

        byte[] pdf = buildSystemDocument(services, recordsMap, slaReports, from, to);
        saveHistory(services.get(0), ReportType.SYSTEM_OVERVIEW, fromDt, toDt, pdf.length);
        return pdf;
    }

    private byte[] buildSystemDocument(List<RegisteredServiceEntity> services,
                                       Map<RegisteredServiceEntity, List<MetricRecordEntity>> recordsMap,
                                       Map<Long, com.nmontytskyi.monitoring.model.SlaReport> slaReports,
                                       LocalDate from, LocalDate to) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addSystemTitleSection(doc, from, to);
            doc.add(Chunk.NEWLINE);

            addSystemHealthSummary(doc, services);
            doc.add(Chunk.NEWLINE);

            addServicesStatusOverview(doc, services, recordsMap, slaReports);
            doc.add(Chunk.NEWLINE);

            addSystemAggregatedMetrics(doc, recordsMap);
            doc.add(Chunk.NEWLINE);

            addSystemTopAlerts(doc, services, from.atStartOfDay(), to.atTime(23, 59, 59));
            doc.add(Chunk.NEWLINE);

            Font serviceSubtitleFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, new BaseColor(59, 130, 246));
            for (RegisteredServiceEntity svc : services) {
                List<MetricRecordEntity> records = recordsMap.get(svc);
                if (!records.isEmpty()) {
                    doc.add(new Paragraph("Service: " + svc.getName(), serviceSubtitleFont));
                    addMetricsStatTable(doc, records);
                    doc.add(Chunk.NEWLINE);
                }
            }

            addFooter(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("System PDF generation failed", e);
            throw new ReportGenerationException("PDF generation failed: " + e.getMessage());
        }
    }

    private void addSystemTitleSection(Document doc, LocalDate from, LocalDate to) throws DocumentException {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell titleCell = new PdfPCell(new Phrase("System Overview Report", FONT_TITLE));
        titleCell.setBackgroundColor(DARK_HEADER);
        titleCell.setPadding(12);
        titleCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(titleCell);

        Font subFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, new BaseColor(148, 163, 184));
        PdfPCell subCell = new PdfPCell(new Phrase(
                "All Services — Period: " + from.format(DATE_FMT) + " – " + to.format(DATE_FMT)
                + "   Generated: " + LocalDateTime.now().format(DT_FMT), subFont));
        subCell.setBackgroundColor(DARK_HEADER);
        subCell.setPaddingLeft(12);
        subCell.setPaddingBottom(10);
        subCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(subCell);

        doc.add(header);
    }

    private void addSystemHealthSummary(Document doc, List<RegisteredServiceEntity> services) throws DocumentException {
        doc.add(new Paragraph("System Health Summary", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        long upCount = services.stream().filter(s -> s.getStatus() == HealthStatus.UP).count();
        long degradedCount = services.stream().filter(s -> s.getStatus() == HealthStatus.DEGRADED).count();
        long downCount = services.stream().filter(s -> s.getStatus() == HealthStatus.DOWN).count();

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 2f, 2f, 2f});

        addHeaderRow(table, "Total Services", "UP", "DEGRADED", "DOWN");

        addCenteredCell(table, String.valueOf(services.size()), ROW_LIGHT);

        PdfPCell upCell = new PdfPCell(new Phrase(String.valueOf(upCount), FONT_CELL_GREEN));
        upCell.setBackgroundColor(ROW_LIGHT);
        upCell.setPadding(6);
        upCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(upCell);

        addCenteredCell(table, String.valueOf(degradedCount), ROW_LIGHT);

        PdfPCell downCell = new PdfPCell(new Phrase(String.valueOf(downCount), FONT_CELL_RED));
        downCell.setBackgroundColor(ROW_LIGHT);
        downCell.setPadding(6);
        downCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(downCell);

        doc.add(table);
    }

    private void addServicesStatusOverview(Document doc,
                                           List<RegisteredServiceEntity> services,
                                           Map<RegisteredServiceEntity, List<MetricRecordEntity>> recordsMap,
                                           Map<Long, com.nmontytskyi.monitoring.model.SlaReport> slaReports)
            throws DocumentException {
        doc.add(new Paragraph("Services Status Overview", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 2f, 2f, 2f, 2f});

        addHeaderRow(table, "Service", "Status", "Uptime %", "Avg RT (ms)", "Error Rate %", "SLA");

        Font degradedFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(245, 158, 11));

        int i = 0;
        for (RegisteredServiceEntity svc : services) {
            BaseColor bg = (i++ % 2 == 0) ? ROW_LIGHT : ROW_DARK;
            boolean hasData = !recordsMap.get(svc).isEmpty();
            com.nmontytskyi.monitoring.model.SlaReport sla = slaReports.get(svc.getId());

            PdfPCell nameCell = new PdfPCell(new Phrase(svc.getName(), FONT_CELL));
            nameCell.setBackgroundColor(bg);
            nameCell.setPadding(5);
            table.addCell(nameCell);

            HealthStatus status = svc.getStatus();
            Font statusFont = status == HealthStatus.UP ? FONT_CELL_GREEN
                    : status == HealthStatus.DOWN ? FONT_CELL_RED : degradedFont;
            PdfPCell statusCell = new PdfPCell(new Phrase(status.name(), statusFont));
            statusCell.setBackgroundColor(bg);
            statusCell.setPadding(5);
            statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(statusCell);

            if (hasData && sla != null) {
                addCenteredCell(table, String.format("%.2f", sla.getActualUptimePercent()), bg);
                addCenteredCell(table, String.format("%.0f", sla.getActualAvgResponseTimeMs()), bg);
                addCenteredCell(table, String.format("%.2f", sla.getActualErrorRatePercent()), bg);

                boolean met = !sla.isSlaBreached();
                PdfPCell slaCell = new PdfPCell(new Phrase(met ? "✓ MET" : "✗ BREACHED",
                        met ? FONT_CELL_GREEN : FONT_CELL_RED));
                slaCell.setBackgroundColor(bg);
                slaCell.setPadding(5);
                slaCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(slaCell);
            } else {
                addCenteredCell(table, "N/A", bg);
                addCenteredCell(table, "N/A", bg);
                addCenteredCell(table, "N/A", bg);
                addCenteredCell(table, "—", bg);
            }
        }

        doc.add(table);
    }

    private void addSystemAggregatedMetrics(Document doc,
                                            Map<RegisteredServiceEntity, List<MetricRecordEntity>> recordsMap)
            throws DocumentException {
        doc.add(new Paragraph("Aggregated Metrics (All Services)", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 2f, 2f, 2f});

        addHeaderRow(table, "Metric", "Avg", "Min", "Max", "P95");

        List<MetricRecordEntity> allRecords = recordsMap.values().stream()
                .flatMap(Collection::stream)
                .toList();

        if (allRecords.isEmpty()) {
            PdfPCell naCell = new PdfPCell(new Phrase("No data available for the selected period.", FONT_CELL));
            naCell.setColspan(5);
            naCell.setPadding(6);
            naCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(naCell);
            doc.add(table);
            return;
        }

        List<Double> rtValues = allRecords.stream()
                .mapToDouble(MetricRecordEntity::getResponseTimeMs)
                .sorted().boxed().toList();
        DoubleSummaryStatistics rtStats = rtValues.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        addAggregateRow(table, "Response Time (ms)",
                rtStats.getAverage(), rtStats.getMin(), rtStats.getMax(), percentile(rtValues, 95), 0, "%.0f");

        List<Double> cpuValues = allRecords.stream()
                .map(MetricRecordEntity::getCpuUsage)
                .filter(Objects::nonNull)
                .map(v -> v * 100)
                .sorted().toList();
        if (!cpuValues.isEmpty()) {
            DoubleSummaryStatistics cpuStats = cpuValues.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            addAggregateRow(table, "CPU Usage (%)",
                    cpuStats.getAverage(), cpuStats.getMin(), cpuStats.getMax(), percentile(cpuValues, 95), 1, "%.1f");
        } else {
            addAggregateNaRow(table, "CPU Usage (%)", 1);
        }

        List<Double> heapValues = allRecords.stream()
                .map(MetricRecordEntity::getHeapUsedMb)
                .filter(Objects::nonNull)
                .map(Long::doubleValue)
                .sorted().toList();
        if (!heapValues.isEmpty()) {
            DoubleSummaryStatistics heapStats = heapValues.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            addAggregateRow(table, "Heap Used (MB)",
                    heapStats.getAverage(), heapStats.getMin(), heapStats.getMax(), percentile(heapValues, 95), 0, "%.0f");
        } else {
            addAggregateNaRow(table, "Heap Used (MB)", 0);
        }

        doc.add(table);
    }

    private void addSystemTopAlerts(Document doc, List<RegisteredServiceEntity> services,
                                    LocalDateTime from, LocalDateTime to) throws DocumentException {
        doc.add(new Paragraph("Top 10 Alerts (System-Wide)", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        List<AlertEventEntity> allAlerts = new ArrayList<>();
        for (RegisteredServiceEntity svc : services) {
            alertEventRepository
                    .findAllByServiceIdAndFiredAtBetweenOrderByFiredAtDesc(svc.getId(), from, to, PageRequest.of(0, 10))
                    .getContent()
                    .forEach(allAlerts::add);
        }

        allAlerts.sort(Comparator.comparing(AlertEventEntity::getFiredAt).reversed());
        List<AlertEventEntity> top10 = allAlerts.stream().limit(10).toList();

        if (top10.isEmpty()) {
            doc.add(new Paragraph("No alerts in this period.", FONT_CELL));
            return;
        }

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 2f, 5f});

        addHeaderRow(table, "Service", "Fired At", "Message");

        int i = 0;
        for (AlertEventEntity event : top10) {
            BaseColor bg = (i++ % 2 == 0) ? ROW_LIGHT : ROW_DARK;
            addCenteredCell(table, event.getService().getName(), bg);
            addCenteredCell(table, event.getFiredAt().format(DT_FMT), bg);
            PdfPCell msgCell = new PdfPCell(new Phrase(event.getMessage(), FONT_CELL));
            msgCell.setBackgroundColor(bg);
            msgCell.setPadding(5);
            table.addCell(msgCell);
        }

        doc.add(table);
    }

    private void addAggregateRow(PdfPTable table, String label,
                                 double avg, double min, double max, double p95,
                                 int rowIndex, String fmt) {
        BaseColor bg = (rowIndex % 2 == 0) ? ROW_LIGHT : ROW_DARK;
        String[] vals = {label, fmt.formatted(avg), fmt.formatted(min), fmt.formatted(max), fmt.formatted(p95)};
        for (int i = 0; i < vals.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(vals[i], FONT_CELL));
            cell.setBackgroundColor(bg);
            cell.setPadding(5);
            if (i > 0) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addAggregateNaRow(PdfPTable table, String label, int rowIndex) {
        BaseColor bg = (rowIndex % 2 == 0) ? ROW_LIGHT : ROW_DARK;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_CELL));
        labelCell.setBackgroundColor(bg);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        PdfPCell naCell = new PdfPCell(new Phrase("N/A", FONT_CELL));
        naCell.setColspan(4);
        naCell.setBackgroundColor(bg);
        naCell.setPadding(5);
        naCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(naCell);
    }

    private byte[] buildSlaDocument(RegisteredServiceEntity service,
                                    com.nmontytskyi.monitoring.model.SlaReport sla,
                                    List<MetricRecordEntity> records,
                                    LocalDate from, LocalDate to) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addTitleSection(doc, "SLA Compliance Report", service.getName(), from, to);
            doc.add(Chunk.NEWLINE);

            addSlaTable(doc, sla);
            doc.add(Chunk.NEWLINE);

            addMetricsStatTable(doc, records);
            doc.add(Chunk.NEWLINE);

            addTopAlerts(doc, service.getId(), from.atStartOfDay(), to.atTime(23, 59, 59));
            doc.add(Chunk.NEWLINE);

            addFooter(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("SLA PDF generation failed for service {}", service.getName(), e);
            throw new ReportGenerationException("PDF generation failed: " + e.getMessage());
        }
    }

    private byte[] buildFullDocument(RegisteredServiceEntity service,
                                     com.nmontytskyi.monitoring.model.SlaReport sla,
                                     List<MetricRecordEntity> records,
                                     LocalDate from, LocalDate to) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addTitleSection(doc, "Full Metrics Report", service.getName(), from, to);
            doc.add(Chunk.NEWLINE);

            addSlaTable(doc, sla);
            doc.add(Chunk.NEWLINE);

            addMetricsStatTable(doc, records);
            doc.add(Chunk.NEWLINE);

            addTopAlerts(doc, service.getId(), from.atStartOfDay(), to.atTime(23, 59, 59));
            doc.add(Chunk.NEWLINE);

            addHourlyBreakdown(doc, records);
            doc.add(Chunk.NEWLINE);

            addFooter(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Full PDF generation failed for service {}", service.getName(), e);
            throw new ReportGenerationException("PDF generation failed: " + e.getMessage());
        }
    }

    private void addTitleSection(Document doc, String reportTitle, String serviceName,
                                 LocalDate from, LocalDate to) throws DocumentException {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell titleCell = new PdfPCell(new Phrase(reportTitle + " — " + serviceName, FONT_TITLE));
        titleCell.setBackgroundColor(DARK_HEADER);
        titleCell.setPadding(12);
        titleCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(titleCell);

        Font subFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, new BaseColor(148, 163, 184));
        PdfPCell subCell = new PdfPCell(new Phrase(
                "Period: " + from.format(DATE_FMT) + " – " + to.format(DATE_FMT)
                + "    Generated: " + LocalDateTime.now().format(DT_FMT), subFont));
        subCell.setBackgroundColor(DARK_HEADER);
        subCell.setPaddingLeft(12);
        subCell.setPaddingBottom(10);
        subCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(subCell);

        doc.add(header);
    }

    private void addSlaTable(Document doc, com.nmontytskyi.monitoring.model.SlaReport sla) throws DocumentException {
        doc.add(new Paragraph("SLA Compliance Summary", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 2f, 1.5f});

        addHeaderRow(table, "Metric", "Actual", "Required / Max", "Status");

        boolean uptimeMet = sla.isUptimeMet();
        addDataRow(table, "Uptime %",
                String.format("%.2f%%", sla.getActualUptimePercent()),
                String.format("%.1f%%", sla.getSla().getUptimePercent()),
                uptimeMet ? "✓" : "✗", uptimeMet, 0);

        boolean rtMet = sla.isResponseTimeMet();
        addDataRow(table, "Avg Response Time (ms)",
                String.format("%.0f", sla.getActualAvgResponseTimeMs()),
                String.valueOf(sla.getSla().getMaxResponseTimeMs()),
                rtMet ? "✓" : "✗", rtMet, 1);

        boolean errMet = sla.isErrorRateMet();
        addDataRow(table, "Error Rate %",
                String.format("%.2f%%", sla.getActualErrorRatePercent()),
                String.format("%.1f%%", sla.getSla().getMaxErrorRatePercent()),
                errMet ? "✓" : "✗", errMet, 0);

        boolean compliant = !sla.isSlaBreached();
        PdfPCell statusCell = new PdfPCell(new Phrase(
                compliant ? "COMPLIANT" : "BREACHED",
                compliant ? FONT_CELL_GREEN : FONT_CELL_RED));
        statusCell.setColspan(4);
        statusCell.setBackgroundColor(ROW_DARK);
        statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        statusCell.setPadding(6);
        table.addCell(statusCell);

        doc.add(table);
    }

    private void addMetricsStatTable(Document doc, List<MetricRecordEntity> records) throws DocumentException {
        doc.add(new Paragraph("Metrics Statistics", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 2f, 2f, 2f, 2f});

        addHeaderRow(table, "Metric", "Avg", "Min", "Max", "P95", "P99");

        DoubleSummaryStatistics rtStats = records.stream()
                .mapToDouble(MetricRecordEntity::getResponseTimeMs).summaryStatistics();

        List<Double> rtSorted = records.stream()
                .mapToDouble(MetricRecordEntity::getResponseTimeMs)
                .sorted().boxed().toList();
        double rtP95 = percentile(rtSorted, 95);
        double rtP99 = percentile(rtSorted, 99);

        addStatRow(table, "Response Time (ms)",
                rtStats.getAverage(), rtStats.getMin(), rtStats.getMax(), rtP95, rtP99, 0, "%.0f");

        List<Double> cpuValues = records.stream()
                .map(MetricRecordEntity::getCpuUsage)
                .filter(Objects::nonNull)
                .sorted().toList();

        if (!cpuValues.isEmpty()) {
            DoubleSummaryStatistics cpuStats = cpuValues.stream()
                    .mapToDouble(Double::doubleValue).summaryStatistics();
            addStatRow(table, "CPU Usage (%)",
                    cpuStats.getAverage() * 100, cpuStats.getMin() * 100, cpuStats.getMax() * 100,
                    percentile(cpuValues.stream().map(v -> v * 100).toList(), 95),
                    percentile(cpuValues.stream().map(v -> v * 100).toList(), 99),
                    1, "%.1f");
        } else {
            addEmptyStatRow(table, "CPU Usage (%)", 1);
        }

        List<Double> heapValues = records.stream()
                .map(MetricRecordEntity::getHeapUsedMb)
                .filter(Objects::nonNull)
                .map(Long::doubleValue).sorted().toList();

        if (!heapValues.isEmpty()) {
            DoubleSummaryStatistics heapStats = heapValues.stream()
                    .mapToDouble(Double::doubleValue).summaryStatistics();
            addStatRow(table, "Heap Used (MB)",
                    heapStats.getAverage(), heapStats.getMin(), heapStats.getMax(),
                    percentile(heapValues, 95), percentile(heapValues, 99), 0, "%.0f");
        } else {
            addEmptyStatRow(table, "Heap Used (MB)", 0);
        }

        doc.add(table);
    }

    private void addTopAlerts(Document doc, Long serviceId, LocalDateTime from, LocalDateTime to)
            throws DocumentException {
        doc.add(new Paragraph("Top 5 Alerts", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        var events = alertEventRepository
                .findAllByServiceIdAndFiredAtBetweenOrderByFiredAtDesc(serviceId, from, to, PageRequest.of(0, 5));

        if (events.isEmpty()) {
            doc.add(new Paragraph("No alerts in this period.", FONT_CELL));
            return;
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 5f});

        addHeaderRow(table, "Fired At", "Message");

        int i = 0;
        for (var event : events) {
            BaseColor bg = (i++ % 2 == 0) ? ROW_LIGHT : ROW_DARK;
            PdfPCell timeCell = new PdfPCell(
                    new Phrase(event.getFiredAt().format(DT_FMT), FONT_CELL));
            timeCell.setBackgroundColor(bg);
            timeCell.setPadding(5);
            table.addCell(timeCell);

            PdfPCell msgCell = new PdfPCell(
                    new Phrase(event.getMessage(), FONT_CELL));
            msgCell.setBackgroundColor(bg);
            msgCell.setPadding(5);
            table.addCell(msgCell);
        }
        doc.add(table);
    }

    private void addHourlyBreakdown(Document doc, List<MetricRecordEntity> records) throws DocumentException {
        doc.add(new Paragraph("Hourly Breakdown", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        Map<Integer, List<MetricRecordEntity>> byHour = records.stream()
                .collect(Collectors.groupingBy(r -> r.getRecordedAt().getHour()));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 2f, 3f, 2f});

        addHeaderRow(table, "Hour", "Requests", "Avg RT (ms)", "Errors");

        int i = 0;
        for (int hour = 0; hour <= 23; hour++) {
            List<MetricRecordEntity> hourRecords = byHour.getOrDefault(hour, List.of());
            if (hourRecords.isEmpty()) continue;

            BaseColor bg = (i++ % 2 == 0) ? ROW_LIGHT : ROW_DARK;
            long errors = hourRecords.stream().filter(MetricRecordEntity::isErrorFlag).count();
            double avgRt = hourRecords.stream()
                    .mapToLong(MetricRecordEntity::getResponseTimeMs).average().orElse(0);

            addCenteredCell(table, String.format("%02d:00", hour), bg);
            addCenteredCell(table, String.valueOf(hourRecords.size()), bg);
            addCenteredCell(table, String.format("%.0f", avgRt), bg);
            addCenteredCell(table, String.valueOf(errors), bg);
        }
        doc.add(table);
    }

    private void addFooter(Document doc) throws DocumentException {
        Font footerFont = new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, new BaseColor(148, 163, 184));
        Paragraph footer = new Paragraph(
                "Monitoring System v1.0 | Generated: " + LocalDateTime.now().format(DT_FMT),
                footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(DARK_HEADER);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addDataRow(PdfPTable table, String label, String actual,
                            String required, String status, boolean met, int rowIndex) {
        BaseColor bg = (rowIndex % 2 == 0) ? ROW_LIGHT : ROW_DARK;
        Font statusFont = met ? FONT_CELL_GREEN : FONT_CELL_RED;

        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_CELL));
        labelCell.setBackgroundColor(bg);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell actualCell = new PdfPCell(new Phrase(actual, FONT_CELL));
        actualCell.setBackgroundColor(bg);
        actualCell.setPadding(5);
        actualCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(actualCell);

        PdfPCell reqCell = new PdfPCell(new Phrase(required, FONT_CELL));
        reqCell.setBackgroundColor(bg);
        reqCell.setPadding(5);
        reqCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(reqCell);

        PdfPCell statusCell = new PdfPCell(new Phrase(status, statusFont));
        statusCell.setBackgroundColor(bg);
        statusCell.setPadding(5);
        statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(statusCell);
    }

    private void addStatRow(PdfPTable table, String label,
                            double avg, double min, double max,
                            double p95, double p99, int rowIndex, String fmt) {
        BaseColor bg = (rowIndex % 2 == 0) ? ROW_LIGHT : ROW_DARK;
        String[] vals = {label, fmt.formatted(avg), fmt.formatted(min),
                fmt.formatted(max), fmt.formatted(p95), fmt.formatted(p99)};
        for (int i = 0; i < vals.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(vals[i], FONT_CELL));
            cell.setBackgroundColor(bg);
            cell.setPadding(5);
            if (i > 0) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addEmptyStatRow(PdfPTable table, String label, int rowIndex) {
        BaseColor bg = (rowIndex % 2 == 0) ? ROW_LIGHT : ROW_DARK;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_CELL));
        labelCell.setBackgroundColor(bg);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell naCell = new PdfPCell(new Phrase("N/A", FONT_CELL));
        naCell.setColspan(5);
        naCell.setBackgroundColor(bg);
        naCell.setPadding(5);
        naCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(naCell);
    }

    private void addCenteredCell(PdfPTable table, String text, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_CELL));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private void saveHistory(RegisteredServiceEntity service, ReportType type,
                             LocalDateTime from, LocalDateTime to, int sizeBytes) {
        ReportHistoryEntity history = ReportHistoryEntity.builder()
                .service(service)
                .reportType(type)
                .periodFrom(from)
                .periodTo(to)
                .generatedAt(LocalDateTime.now())
                .fileSizeKb(sizeBytes / 1024)
                .build();
        reportHistoryRepository.save(history);
    }
}
