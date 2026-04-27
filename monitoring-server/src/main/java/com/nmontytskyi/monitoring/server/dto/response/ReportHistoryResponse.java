package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.server.entity.ReportHistoryEntity.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportHistoryResponse {

    private Long id;
    private ReportType reportType;
    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;
    private LocalDateTime generatedAt;
    private Integer fileSizeKb;
}
