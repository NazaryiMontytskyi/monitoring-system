package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity;
import com.nmontytskyi.monitoring.server.exception.ReportGenerationException;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.repository.ReportHistoryRepository;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    @Mock
    private RegisteredServiceRepository serviceRepository;
    @Mock
    private MetricRecordRepository metricRecordRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private ReportHistoryRepository reportHistoryRepository;
    @Mock
    private SlaCalculationService slaCalculationService;

    @InjectMocks
    private PdfReportService pdfReportService;

    private RegisteredServiceEntity service;
    private SlaReport slaReport;

    @BeforeEach
    void setUp() {
        service = RegisteredServiceEntity.builder()
                .id(1L)
                .name("test-service")
                .host("localhost")
                .port(8080)
                .status(HealthStatus.UP)
                .build();

        com.nmontytskyi.monitoring.model.SlaDefinition slaDef =
                com.nmontytskyi.monitoring.model.SlaDefinition.defaults();
        slaReport = SlaReport.builder()
                .sla(slaDef)
                .actualUptimePercent(99.95)
                .actualAvgResponseTimeMs(250.0)
                .actualErrorRatePercent(0.5)
                .uptimeMet(true)
                .responseTimeMet(true)
                .errorRateMet(true)
                .p95ResponseTimeMs(400L)
                .p99ResponseTimeMs(600L)
                .build();
    }

    private MetricRecordEntity buildRecord() {
        return MetricRecordEntity.builder()
                .id(1L)
                .service(service)
                .responseTimeMs(200L)
                .status(HealthStatus.UP)
                .cpuUsage(0.35)
                .heapUsedMb(256L)
                .heapMaxMb(512L)
                .source(MetricRecordEntity.MetricSource.PULL)
                .recordedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void generateSlaReport_returnsPdfBytes() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(metricRecordRepository.findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                eq(1L), any(), any())).thenReturn(List.of(buildRecord()));
        when(slaCalculationService.calculate(1L, SlaWindow.MONTH)).thenReturn(slaReport);
        when(alertEventRepository.findAllByFiredAtBetweenOrderByFiredAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        byte[] pdf = pdfReportService.generateSlaReport(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(pdf).isNotEmpty();
        assertThat(pdf[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(pdf[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(pdf[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(pdf[3]).isEqualTo((byte) 0x46); // 'F'
    }

    @Test
    void generateSlaReport_savesHistoryRecord() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(metricRecordRepository.findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                eq(1L), any(), any())).thenReturn(List.of(buildRecord()));
        when(slaCalculationService.calculate(1L, SlaWindow.MONTH)).thenReturn(slaReport);
        when(alertEventRepository.findAllByFiredAtBetweenOrderByFiredAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        pdfReportService.generateSlaReport(1L, LocalDate.now().minusDays(7), LocalDate.now());

        ArgumentCaptor<ReportHistoryEntity> captor = ArgumentCaptor.forClass(ReportHistoryEntity.class);
        verify(reportHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getReportType()).isEqualTo(ReportHistoryEntity.ReportType.SLA_SUMMARY);
    }

    @Test
    void generateSlaReport_noData_throwsReportGenerationException() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(metricRecordRepository.findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                eq(1L), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() ->
                pdfReportService.generateSlaReport(1L, LocalDate.now().minusDays(7), LocalDate.now()))
                .isInstanceOf(ReportGenerationException.class);
    }

    @Test
    void generateFullReport_returnsPdfBytes() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(metricRecordRepository.findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                eq(1L), any(), any())).thenReturn(List.of(buildRecord()));
        when(slaCalculationService.calculate(1L, SlaWindow.MONTH)).thenReturn(slaReport);
        when(alertEventRepository.findAllByFiredAtBetweenOrderByFiredAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        byte[] pdf = pdfReportService.generateFullReport(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(pdf).isNotEmpty();
        assertThat(pdf[0]).isEqualTo((byte) 0x25);
        assertThat(pdf[1]).isEqualTo((byte) 0x50);
        assertThat(pdf[2]).isEqualTo((byte) 0x44);
        assertThat(pdf[3]).isEqualTo((byte) 0x46);
    }
}
