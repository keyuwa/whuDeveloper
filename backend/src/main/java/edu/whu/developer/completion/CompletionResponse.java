package edu.whu.developer.completion;

import java.time.LocalDateTime;

public record CompletionResponse(
        Long id,
        Long projectId,
        String prUrl,
        String summary,
        LocalDateTime createdAt
) {
    public static CompletionResponse from(Completion completion) {
        return new CompletionResponse(
                completion.getId(),
                completion.getProject().getId(),
                completion.getPrUrl(),
                completion.getSummary(),
                completion.getCreatedAt());
    }
}
