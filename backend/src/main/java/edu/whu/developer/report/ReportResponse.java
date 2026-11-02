package edu.whu.developer.report;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long projectId,
        Integer weekNumber,
        String content,
        LocalDateTime createdAt
) {
    public static ReportResponse from(WeeklyReport report) {
        return new ReportResponse(
                report.getId(),
                report.getProject().getId(),
                report.getWeekNumber(),
                report.getContent(),
                report.getCreatedAt());
    }
}
