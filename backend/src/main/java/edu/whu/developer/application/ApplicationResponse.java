package edu.whu.developer.application;

import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        Long taskId,
        String taskTitle,
        String taskType,
        Long studentId,
        String studentName,
        String proposal,
        String status,
        Long projectId,
        LocalDateTime createdAt
) {
    public static ApplicationResponse from(TaskApplication application, Long projectId) {
        return new ApplicationResponse(
                application.getId(),
                application.getTask().getId(),
                application.getTask().getTitle(),
                application.getTask().getType().name(),
                application.getStudent().getId(),
                application.getStudent().getDisplayName(),
                application.getProposal(),
                application.getStatus().name(),
                projectId,
                application.getCreatedAt());
    }
}
