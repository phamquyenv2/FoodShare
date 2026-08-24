package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Report;
import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.ReportStatus;
import com.datn.foodshare.util.constant.ReportType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReportResponse {

    private Long id;
    private ReporterInfo reporter;
    private String title;
    private String content;
    private ReportType reportType;
    private ReportStatus reportStatus;
    private String response;
    private String evidenceUrl;
    private ReportReferenceType referenceType;
    private Long referenceId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    @Getter
    @Builder
    public static class ReporterInfo {
        private Long id;
        private String fullName;
        private String email;
        private String phone;
    }

    public static ReportResponse from(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .reporter(ReporterInfo.builder()
                        .id(report.getReporter().getId())
                        .fullName(report.getReporter().getFullName())
                        .email(report.getReporter().getEmail())
                        .phone(report.getReporter().getPhone())
                        .build())
                .title(report.getTitle())
                .content(report.getContent())
                .reportType(report.getReportType())
                .reportStatus(report.getReportStatus())
                .response(report.getResponse())
                .evidenceUrl(report.getEvidenceUrl())
                .referenceType(report.getReferenceType())
                .referenceId(report.getReferenceId())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
    }
}
