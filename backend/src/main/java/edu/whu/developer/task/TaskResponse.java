package edu.whu.developer.task;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        String type,
        String status,
        Long publisherId,
        String publisherName,
        LocalDateTime createdAt,
        ApplicationBrief myApplication
) {
    public record ApplicationBrief(Long id, String proposal, String status, Long projectId) {
    }
}
