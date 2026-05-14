package com.nmontytskyi.monitoring.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity recording metadata for each generated PDF report.
 *
 * <p>Stores the report type (SLA, FULL, SYSTEM), the date range covered, the timestamp
 * of generation, and the resulting file size. Used to populate the report history table
 * in the web UI without re-generating reports.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.service.PdfReportService
 */
@Entity
@Table(name = "report_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private RegisteredServiceEntity service;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Column(name = "period_from", nullable = false)
    private LocalDateTime periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDateTime periodTo;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "file_size_kb")
    private Integer fileSizeKb;

    public enum ReportType {
        SLA_SUMMARY,
        METRICS_DETAIL,
        FULL,
        SYSTEM_OVERVIEW
    }
}
