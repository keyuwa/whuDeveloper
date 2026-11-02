package edu.whu.developer.project;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long taskId,
        String taskTitle,
        String taskType,
        Long studentId,
        String studentName,
        Long publisherId,
        String publisherName,
        String status,
        LocalDateTime enrolledAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getTask().getId(),
                project.getTask().getTitle(),
                project.getTask().getType().name(),
                project.getStudent().getId(),
                project.getStudent().getDisplayName(),
                project.getPublisher().getId(),
                project.getPublisher().getDisplayName(),
                project.getStatus().name(),
                project.getEnrolledAt());
    }
}
