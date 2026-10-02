package fr.esilv.poolup.admin;

import java.time.Instant;

/** What the reporter gets back: their own report only, nothing about the reported person. */
public record SubmittedReportResponse(Long id, ReportTargetType targetType, Long targetId, String reason,
        ReportStatus status, Instant createdAt) {

    static SubmittedReportResponse from(Report report) {
        return new SubmittedReportResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getStatus(), report.getCreatedAt());
    }
}
