package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.dto.response.ReportHistoryResponse;
import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity;
import com.nmontytskyi.monitoring.server.repository.ReportHistoryRepository;
import com.nmontytskyi.monitoring.server.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final PdfReportService pdfReportService;
    private final ReportHistoryRepository reportHistoryRepository;

    @GetMapping("/{serviceId}/sla")
    public ResponseEntity<byte[]> slaReport(
            @PathVariable Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] pdf = pdfReportService.generateSlaReport(serviceId, from, to);
        String filename = "sla-report-" + serviceId + "-" + from + "-" + to + ".pdf";
        return buildPdfResponse(pdf, filename);
    }

    @GetMapping("/{serviceId}/full")
    public ResponseEntity<byte[]> fullReport(
            @PathVariable Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] pdf = pdfReportService.generateFullReport(serviceId, from, to);
        String filename = "full-report-" + serviceId + "-" + from + "-" + to + ".pdf";
        return buildPdfResponse(pdf, filename);
    }

    @GetMapping("/{serviceId}/history")
    public List<ReportHistoryResponse> history(@PathVariable Long serviceId) {
        return reportHistoryRepository.findAllByServiceIdOrderByGeneratedAtDesc(serviceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private ReportHistoryResponse toResponse(ReportHistoryEntity e) {
        return ReportHistoryResponse.builder()
                .id(e.getId())
                .reportType(e.getReportType())
                .periodFrom(e.getPeriodFrom())
                .periodTo(e.getPeriodTo())
                .generatedAt(e.getGeneratedAt())
                .fileSizeKb(e.getFileSizeKb())
                .build();
    }
}
